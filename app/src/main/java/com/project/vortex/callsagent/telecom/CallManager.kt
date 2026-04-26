package com.project.vortex.callsagent.telecom

import android.content.Context
import android.net.Uri
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.util.Log
import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.MissedCallReason
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.Interaction
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.MissedCallRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import com.project.vortex.callsagent.telecom.model.CallUiState
import com.project.vortex.callsagent.telecom.model.EndedCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallManager"

/**
 * Single source of truth for the **active** call. Bridges
 * [CallsInCallService] / [CallsConnectionService] (which receive Telecom
 * callbacks) with both the in-call UI and post-call navigation.
 *
 * Phases covered:
 *  - 3.2: state flows, audio routing, live notes, place call.
 *  - 3.3: state machine, end-of-call persistence, hand-off to PostCall.
 *  - 3.4: outcome pre-fill via DisconnectCauseMapper.
 *  - 3.5: incoming-call Option B (caller lookup, accept/reject, direction
 *    tag, missed-call logging).
 *
 * **Thread model**: every public mutator is invoked from the main thread
 * (Telecom callbacks, UI taps). The IO work — Room persistence, sync
 * scheduling — happens on [scope] which uses Dispatchers.IO.
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val clientRepository: ClientRepository,
    private val interactionRepository: InteractionRepository,
    private val noteRepository: NoteRepository,
    private val missedCallRepository: MissedCallRepository,
    private val syncScheduler: SyncScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentCall: Call? = null
    private var inCallService: InCallService? = null
    private var callStartedAt: Instant? = null

    private val _currentClient = MutableStateFlow<Client?>(null)
    val currentClient: StateFlow<Client?> = _currentClient.asStateFlow()

    private val _callDirection = MutableStateFlow(CallDirection.OUTBOUND)
    val callDirection: StateFlow<CallDirection> = _callDirection.asStateFlow()

    /** For incoming calls when no client matches the caller's number. */
    private val _incomingPhoneNumber = MutableStateFlow<String?>(null)
    val incomingPhoneNumber: StateFlow<String?> = _incomingPhoneNumber.asStateFlow()

    private val _callState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val callState: StateFlow<CallUiState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    /** Defaults to true — Tab A9+ is hands-free use case. */
    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    /** Notes the agent types during the call. Persisted on call end if non-empty. */
    val liveNoteContent = MutableStateFlow("")

    /** One-shot signal for [com.project.vortex.callsagent.presentation.navigation.AppNavGraph]
     * to navigate to PostCall. Caller MUST invoke [consumeLastEndedCall]
     * after handling. */
    private val _lastEndedCall = MutableStateFlow<EndedCall?>(null)
    val lastEndedCall: StateFlow<EndedCall?> = _lastEndedCall.asStateFlow()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateStateFromCall(call)
        }
    }

    fun hasActiveCall(): Boolean = currentCall != null

    /** Outgoing entry point — used by PreCallScreen. */
    fun startCall(client: Client) {
        if (hasActiveCall()) {
            Log.w(TAG, "startCall ignored — another call is already active")
            return
        }
        // Fresh slate.
        _currentClient.value = client
        _incomingPhoneNumber.value = null
        _callDirection.value = CallDirection.OUTBOUND
        _callState.value = CallUiState.Dialing
        liveNoteContent.value = ""
        _isMuted.value = false
        _isSpeakerOn.value = true
        callStartedAt = Instant.now()

        val telecom = appContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", client.phone, null)
        try {
            telecom.placeCall(uri, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "placeCall denied — missing CALL_PHONE or role", e)
            _callState.value = CallUiState.Disconnected
        }
    }

    /** Outgoing-call binding. Called by [CallsInCallService.onCallAdded]. */
    fun setOutgoingCall(call: Call, ctx: InCallService) {
        currentCall = call
        inCallService = ctx
        _callDirection.value = CallDirection.OUTBOUND
        call.registerCallback(callCallback)
        updateStateFromCall(call)
    }

    /** Incoming-call binding (Option B). Looks up the caller against
     * assigned clients so the InCallScreen can show their identity. */
    fun setIncomingCall(call: Call, ctx: InCallService) {
        currentCall = call
        inCallService = ctx
        _callDirection.value = CallDirection.INBOUND
        liveNoteContent.value = ""
        _isMuted.value = false
        _isSpeakerOn.value = true
        callStartedAt = null // start counting on Accept (STATE_ACTIVE)

        val phone = call.details?.handle?.schemeSpecificPart.orEmpty()
        _incomingPhoneNumber.value = phone
        scope.launch {
            val match = runCatching { clientRepository.findByPhone(phone) }.getOrNull()
            _currentClient.value = match
        }

        call.registerCallback(callCallback)
        updateStateFromCall(call)
    }

    /** Accept the currently-ringing incoming call. */
    fun acceptIncoming() {
        val call = currentCall ?: return
        call.answer(0)
        // Honor the "speaker by default" rule once we accept.
        @Suppress("DEPRECATION")
        runCatching { inCallService?.setAudioRoute(CallAudioState.ROUTE_SPEAKER) }
    }

    /** Reject the currently-ringing incoming call. Logs a missed call. */
    fun rejectIncoming() {
        val call = currentCall ?: return
        val phone = _incomingPhoneNumber.value.orEmpty()
        val matchedId = _currentClient.value?.id
        scope.launch {
            missedCallRepository.log(phone, matchedId, MissedCallReason.REJECTED)
        }
        call.reject(false, null)
    }

    fun mute(enabled: Boolean) {
        inCallService?.setMuted(enabled)
    }

    fun setSpeaker(enabled: Boolean) {
        val route = if (enabled) {
            CallAudioState.ROUTE_SPEAKER
        } else {
            CallAudioState.ROUTE_EARPIECE
        }
        @Suppress("DEPRECATION")
        inCallService?.setAudioRoute(route)
    }

    fun disconnect() {
        val call = currentCall
        if (call != null) {
            call.disconnect()
        } else if (_callState.value !is CallUiState.Disconnected) {
            // No active call to drop, but the in-call UI is up. Surface
            // Disconnected so InCallActivity can close itself.
            _callState.value = CallUiState.Disconnected
        }
    }

    /** Forwarded by the InCallService when the system audio state changes. */
    fun onAudioStateChanged(state: CallAudioState) {
        _isMuted.value = state.isMuted
        _isSpeakerOn.value = state.route == CallAudioState.ROUTE_SPEAKER
    }

    /** Logs a missed incoming call without binding. Used by the
     * ConnectionService busy-guard path where we never get a Call ref. */
    fun logIncomingMissed(phone: String, reason: MissedCallReason) {
        scope.launch {
            val matchedId = runCatching { clientRepository.findByPhone(phone) }
                .getOrNull()?.id
            missedCallRepository.log(phone, matchedId, reason)
        }
    }

    /** Called by [CallsInCallService.onCallRemoved]. */
    fun onCallEnded(call: Call) {
        currentCall?.unregisterCallback(callCallback)
        val client = _currentClient.value
        val direction = _callDirection.value
        val started = callStartedAt
        val cause = call.details?.disconnectCause
        val wasRinging = _callState.value is CallUiState.Ringing

        Log.d(
            TAG,
            "onCallEnded — client=${client?.id} direction=$direction " +
                "wasRinging=$wasRinging cause=${cause?.code}",
        )

        currentCall = null
        inCallService = null
        callStartedAt = null
        _callState.value = CallUiState.Disconnected
        // Clear the per-call mutable state so the NEXT startCall starts
        // from a clean slate. _currentClient was already captured above.
        _currentClient.value = null
        _incomingPhoneNumber.value = null

        when {
            // Incoming that ended while ringing → caller hung up before accept.
            direction == CallDirection.INBOUND && wasRinging -> {
                val phone = call.details?.handle?.schemeSpecificPart.orEmpty()
                val matchedId = client?.id
                scope.launch {
                    missedCallRepository.log(phone, matchedId, MissedCallReason.NOT_ANSWERED)
                }
            }
            // Otherwise we have a real interaction to persist.
            client != null && started != null -> {
                scope.launch { persistInteraction(client, started, cause, direction) }
            }
            else -> {
                Log.w(
                    TAG,
                    "onCallEnded with no client/started — nothing to persist",
                )
            }
        }
    }

    /**
     * Caller (AppNavGraph) MUST call this after navigating to PostCall.
     * Intentionally does NOT reset live state — see Phase 3.2 fix notes.
     */
    fun consumeLastEndedCall() {
        _lastEndedCall.value = null
    }

    private suspend fun persistInteraction(
        client: Client,
        startedAt: Instant,
        disconnectCause: DisconnectCause?,
        direction: CallDirection,
    ) {
        val endedAt = Instant.now()
        val durationSeconds = (endedAt.epochSecond - startedAt.epochSecond)
            .toInt().coerceAtLeast(0)

        val outcome = DisconnectCauseMapper.toOutcome(disconnectCause)
            ?: CallOutcome.NO_ANSWER

        val interaction = Interaction(
            mobileSyncId = UUID.randomUUID().toString(),
            clientId = client.id,
            direction = direction,
            callStartedAt = startedAt,
            callEndedAt = endedAt,
            durationSeconds = durationSeconds,
            outcome = outcome,
            disconnectCause = disconnectCause?.toString(),
            deviceCreatedAt = Instant.now(),
            syncStatus = SyncStatus.PENDING,
        )

        // Best-effort persistence. Even if Room write fails, we still
        // emit lastEndedCall so the UI can route to PostCall — there the
        // agent at least lands somewhere consistent. If we silently
        // swallow the failure without emitting, the user gets stranded
        // on InCallActivity → PreCall (broken auto-call flow).
        val saved = runCatching { interactionRepository.save(interaction) }
        if (saved.isFailure) {
            Log.e(
                TAG,
                "persistInteraction — failed to save interaction ${interaction.mobileSyncId}",
                saved.exceptionOrNull(),
            )
        }

        val noteText = liveNoteContent.value.trim()
        if (noteText.isNotEmpty()) {
            runCatching {
                noteRepository.save(
                    Note(
                        mobileSyncId = UUID.randomUUID().toString(),
                        clientId = client.id,
                        interactionMobileSyncId = interaction.mobileSyncId,
                        content = noteText,
                        type = NoteType.CALL,
                        deviceCreatedAt = Instant.now(),
                        syncStatus = SyncStatus.PENDING,
                    ),
                )
            }.onFailure { Log.e(TAG, "persistInteraction — note save failed", it) }
        }

        runCatching { syncScheduler.triggerImmediateSync() }

        Log.d(
            TAG,
            "persistInteraction — emitting lastEndedCall " +
                "client=${client.id} interaction=${interaction.mobileSyncId} " +
                "outcome=$outcome",
        )
        _lastEndedCall.value = EndedCall(
            clientId = client.id,
            interactionMobileSyncId = interaction.mobileSyncId,
        )
    }

    private fun updateStateFromCall(call: Call) {
        _callState.value = when (call.state) {
            Call.STATE_CONNECTING, Call.STATE_DIALING -> CallUiState.Dialing
            Call.STATE_RINGING -> CallUiState.Ringing
            Call.STATE_ACTIVE -> {
                if (callStartedAt == null) callStartedAt = Instant.now()
                val previous = _callState.value
                if (previous is CallUiState.Active) previous
                else CallUiState.Active(activeSince = Instant.now())
            }
            Call.STATE_HOLDING -> _callState.value
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallUiState.Disconnected
            else -> _callState.value
        }
    }
}
