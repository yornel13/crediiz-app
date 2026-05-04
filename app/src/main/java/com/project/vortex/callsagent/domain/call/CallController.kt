package com.project.vortex.callsagent.domain.call

import android.util.Log
import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.sip.CallSession
import com.project.vortex.callsagent.data.sip.LinphoneCoreManager
import com.project.vortex.callsagent.data.sip.SipCallEnding
import com.project.vortex.callsagent.data.sip.SipCallState
import com.project.vortex.callsagent.data.sip.SipRegistrationState
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.call.model.CallUiState
import com.project.vortex.callsagent.domain.call.model.EndedCall
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.Interaction
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallController"
private const val REGISTER_WAIT_MS = 10_000L

// ╔══════════════════════════════════════════════════════════════════════════╗
// ║                                                                          ║
// ║   ████████  ███████  ████████ ████████     ███    ███████  ████████ ███████ ║
// ║      ██     ██       ██          ██        ████  ████ ██  ██   ██  ██       ║
// ║      ██     ██████   ████████    ██        ██ ████ ██ ██  ██   ██  █████    ║
// ║      ██     ██             ██    ██        ██  ██  ██ ██  ██   ██  ██       ║
// ║      ██     ███████  ████████    ██        ██      ██ ████████  ███████ ██  ║
// ║                                                                          ║
// ║   TEMPORARY TEST OVERRIDE — REMOVE BEFORE SHIPPING TO AGENTS             ║
// ║                                                                          ║
// ║   Every outbound call placed via [CallController.startCall] is routed    ║
// ║   to TEST_HARDCODED_TARGET regardless of the Client the agent selected.  ║
// ║   The agent will see the right Client name on the in-call screen but    ║
// ║   the actual SIP INVITE is sent to the hardcoded test number.           ║
// ║                                                                          ║
// ║   Scope: end-to-end QA of the SIP migration with a known reachable      ║
// ║   number. The clientId / Client.phone column is intentionally ignored.  ║
// ║                                                                          ║
// ║   To remove:                                                             ║
// ║      1. Delete TEST_HARDCODED_TARGET below.                              ║
// ║      2. Restore `coreManager.placeCall(client.phone)` in startCall().   ║
// ║      3. Search the codebase for "TEST_HARDCODED_TARGET" to ensure no    ║
// ║         other reference leaked.                                          ║
// ║                                                                          ║
// ║   Set on: 2026-05-03 by the SIP migration test plan.                    ║
// ║                                                                          ║
// ╚══════════════════════════════════════════════════════════════════════════╝
private const val TEST_HARDCODED_TARGET = "+507 6342 5495"

/**
 * Domain-level orchestrator of an active call. Replaces the legacy
 * Telecom-based `CallManager`.
 *
 * The persistence surface (Interaction + Note + sync trigger) is
 * identical, so existing consumers (PreCallViewModel, InCallViewModel,
 * AutoCallOrchestrator, CallNavigationViewModel, InCallGate) keep
 * their flow contracts unchanged.
 *
 * Bridges the engine's per-call [CallSession] to a stable surface:
 * mirrors `SipCallState` to [CallUiState], runs persistence on call
 * end, and exposes the live note content + the post-call navigation
 * signal.
 */
@Singleton
class CallController @Inject constructor(
    private val coreManager: LinphoneCoreManager,
    private val interactionRepository: InteractionRepository,
    private val noteRepository: NoteRepository,
    private val syncScheduler: SyncScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var session: CallSession? = null
    private var sessionWatcher: Job? = null
    private var callStartedAt: Instant? = null

    private val _currentClient = MutableStateFlow<Client?>(null)
    val currentClient: StateFlow<Client?> = _currentClient.asStateFlow()

    /** Always OUTBOUND in v1 — inbound was removed with the Telecom layer. */
    private val _callDirection = MutableStateFlow(CallDirection.OUTBOUND)
    val callDirection: StateFlow<CallDirection> = _callDirection.asStateFlow()

    /** Always null — no inbound calls. Kept for InCallViewModel binary compat. */
    private val _incomingPhoneNumber = MutableStateFlow<String?>(null)
    val incomingPhoneNumber: StateFlow<String?> = _incomingPhoneNumber.asStateFlow()

    private val _callState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val callState: StateFlow<CallUiState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    /** Tab A9+ is hands-free; the engine forces speaker route. */
    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    val liveNoteContent = MutableStateFlow("")

    private val _lastEndedCall = MutableStateFlow<EndedCall?>(null)
    val lastEndedCall: StateFlow<EndedCall?> = _lastEndedCall.asStateFlow()

    init {
        // Kick off SIP registration eagerly so the first tap on Call
        // does not have to wait for the REGISTER round-trip.
        // register() is idempotent.
        scope.launch { coreManager.register() }
    }

    fun hasActiveCall(): Boolean = session != null

    fun startCall(client: Client) {
        if (hasActiveCall()) {
            Log.w(TAG, "startCall ignored — another call is already active")
            return
        }

        _currentClient.value = client
        _incomingPhoneNumber.value = null
        _callDirection.value = CallDirection.OUTBOUND
        _callState.value = CallUiState.Dialing
        liveNoteContent.value = ""
        _isMuted.value = false
        _isSpeakerOn.value = true
        callStartedAt = Instant.now()

        scope.launch {
            val registered = withTimeoutOrNull(REGISTER_WAIT_MS) {
                coreManager.registrationState.first {
                    it is SipRegistrationState.Registered
                }
            }
            if (registered == null) {
                Log.e(TAG, "REGISTER did not complete within ${REGISTER_WAIT_MS}ms")
                _callState.value = CallUiState.Disconnected
                return@launch
            }

            // ⚠ TEMPORARY: see TEST_HARDCODED_TARGET banner at the top of this file.
            //   Remove this override and restore `client.phone` before production.
            Log.w(
                TAG,
                "TEST OVERRIDE: routing call for client ${client.id} (phone=${client.phone}) " +
                    "to hardcoded $TEST_HARDCODED_TARGET",
            )
            val newSession = coreManager.placeCall(TEST_HARDCODED_TARGET)
            if (newSession == null) {
                Log.e(TAG, "placeCall returned null for client ${client.id}")
                _callState.value = CallUiState.Disconnected
                return@launch
            }
            session = newSession
            attachSession(newSession)
        }
    }

    fun mute(enabled: Boolean) {
        session?.setMuted(enabled)
    }

    /**
     * Toggle audio output between speaker (`true`) and earpiece (`false`).
     * Routes through the active [CallSession]; on devices without an
     * earpiece — e.g. the Tab A9+, which only exposes a built-in speaker
     * — the engine reports failure and the visual flag is **kept in
     * sync with the actual routing**: the icon does not flip, signaling
     * to the agent that the toggle has no effect on this hardware.
     *
     * If there is no active session yet, the flag updates optimistically
     * so the next call can pick up the preference.
     */
    fun setSpeaker(enabled: Boolean) {
        val active = session
        if (active == null) {
            _isSpeakerOn.value = enabled
            return
        }
        val applied = active.setSpeakerEnabled(enabled)
        if (applied) {
            _isSpeakerOn.value = enabled
        } else {
            Log.w(
                TAG,
                "Speaker toggle to enabled=$enabled not applied — " +
                    "target audio device unavailable on this hardware",
            )
        }
    }

    fun disconnect() {
        val s = session
        if (s != null) {
            s.disconnect()
        } else if (_callState.value !is CallUiState.Disconnected) {
            _callState.value = CallUiState.Disconnected
        }
    }

    fun consumeLastEndedCall() {
        _lastEndedCall.value = null
    }

    private fun attachSession(s: CallSession) {
        sessionWatcher?.cancel()
        sessionWatcher = scope.launch {
            launch { s.isMuted.collect { _isMuted.value = it } }
            s.state.collect { sipState ->
                _callState.value = sipState.toUi()
                if (sipState is SipCallState.Disconnected) {
                    onSessionEnded()
                }
            }
        }
    }

    private fun onSessionEnded() {
        val client = _currentClient.value
        val started = callStartedAt
        // Capture ending BEFORE clearing the session reference; the
        // CallSession populates `ending` synchronously when the state
        // transitions to Disconnected (see CallSession.listener).
        val ending = session?.ending?.value
        session = null
        sessionWatcher?.cancel()
        sessionWatcher = null
        callStartedAt = null
        _currentClient.value = null

        if (client != null && started != null) {
            scope.launch { persistInteraction(client, started, ending) }
        } else {
            Log.w(TAG, "onSessionEnded with no client/started — nothing to persist")
        }
    }

    private suspend fun persistInteraction(
        client: Client,
        startedAt: Instant,
        ending: SipCallEnding?,
    ) {
        val insight = ending?.let(CallEndingInsight::from)
        val endedAt = Instant.now()
        val durationSeconds = (endedAt.epochSecond - startedAt.epochSecond)
            .toInt().coerceAtLeast(0)

        val interaction = Interaction(
            mobileSyncId = UUID.randomUUID().toString(),
            clientId = client.id,
            direction = CallDirection.OUTBOUND,
            callStartedAt = startedAt,
            callEndedAt = endedAt,
            durationSeconds = durationSeconds,
            // Persist the suggested outcome as the placeholder. The agent
            // confirms or changes it on PostCall; either way the row is
            // updated before sync. Falling back to NO_ANSWER preserves
            // legacy behavior when there's no insight (e.g. orphan).
            outcome = insight?.suggestedOutcome ?: CallOutcome.NO_ANSWER,
            disconnectCause = ending?.javaClass?.simpleName,
            deviceCreatedAt = Instant.now(),
            syncStatus = SyncStatus.PENDING,
        )

        runCatching { interactionRepository.save(interaction) }
            .onFailure {
                Log.e(TAG, "Failed to persist interaction ${interaction.mobileSyncId}", it)
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
            }.onFailure { Log.e(TAG, "Failed to persist call note", it) }
        }

        runCatching { syncScheduler.triggerImmediateSync() }

        _lastEndedCall.value = EndedCall(
            clientId = client.id,
            interactionMobileSyncId = interaction.mobileSyncId,
            suggestedOutcome = insight?.suggestedOutcome,
            allowedOutcomes = insight?.allowedOutcomes.orEmpty(),
            reasonLabel = insight?.reasonLabel,
        )
    }

    private fun SipCallState.toUi(): CallUiState = when (this) {
        SipCallState.Idle -> CallUiState.Idle
        SipCallState.Dialing -> CallUiState.Dialing
        SipCallState.Ringing -> CallUiState.Ringing
        is SipCallState.Active -> CallUiState.Active(activeSince)
        SipCallState.Disconnected -> CallUiState.Disconnected
    }
}
