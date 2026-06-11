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
import com.project.vortex.callsagent.domain.repository.ClientRepository
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

/**
 * Dial-mode switch.
 *
 * Production (default): leave this `null`. Every outbound call is routed to the
 * real `Client.phone` of the agent's selected client.
 *
 * QA override: set this to a known reachable number (e.g. "+507 6957 4868") to
 * route EVERY call to that fixed target while still showing the real client on
 * the in-call screen. Used for end-to-end SIP testing without dialing clients.
 *
 * Flipping QA ↔ production is a one-line change here — no other code touched.
 * MUST be `null` on any build shipped to agents.
 */
private val DIAL_OVERRIDE: String? = null

/**
 * Domain-level orchestrator of an active call. Replaces the legacy
 * Telecom-based `CallManager`.
 *
 * The persistence surface (Interaction + Note + sync trigger) isiuiuoiilp[][]]=
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
    private val clientRepository: ClientRepository,
    private val syncScheduler: SyncScheduler,
    private val callReadinessProvider: CallReadinessProvider,
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

    /**
     * Surfaced when the post-call persistence path fails (interaction
     * save throws, note save throws, etc.). The UI layer collects this
     * and shows it as a Toast/Snackbar so the agent — and we, when
     * debugging — sees WHY the call disappeared instead of silently
     * losing the record. Consumed via [consumeSaveError].
     */
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    fun consumeSaveError() { _saveError.value = null }

    // SIP registration is no longer kicked off here. Phase B routes
    // all REGISTER flows through `VoipRefreshOrchestrator`, which only
    // calls `coreManager.register()` after a successful credential
    // fetch — `coreManager.register()` is idempotent, so subsequent
    // refreshes (foreground, periodic) just re-up the same account.

    fun hasActiveCall(): Boolean = session != null

    fun startCall(client: Client) {
        if (hasActiveCall()) {
            Log.w(TAG, "startCall ignored — another call is already active")
            return
        }
        // Final, single-source-of-truth gate. UI layers already disable
        // the button, but a stale recomposition or AutoCall trigger
        // could slip through. Refuse to dial unless SIP is fully ready
        // (VoIP assigned + REGISTER successful).
        val readiness = callReadinessProvider.readiness.value
        if (readiness !is CallReadiness.Ready) {
            Log.w(TAG, "startCall ignored — not ready: $readiness")
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

            // Production dials the real client number. DIAL_OVERRIDE is the QA
            // escape hatch (see its docs); null on shipped builds.
            val target = DIAL_OVERRIDE ?: client.phone
            if (target.isBlank()) {
                Log.e(TAG, "startCall aborted — client ${client.id} has no phone")
                _callState.value = CallUiState.Disconnected
                return@launch
            }
            if (DIAL_OVERRIDE != null) {
                Log.w(
                    TAG,
                    "DIAL_OVERRIDE active: routing client ${client.id} " +
                        "(phone=${client.phone}) to $DIAL_OVERRIDE",
                )
            }
            val newSession = coreManager.placeCall(target)
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

        // Gate the EndedCall emission on a successful save. If the row
        // didn't make it into the DB, the PostCall route would land on
        // a "Couldn't load call details" screen — the agent gets
        // stuck. Better to keep them on the previous screen (PreCall)
        // and surface the failure via logs so we can diagnose. Note:
        // this means the call data is lost in the failure case — that
        // is itself a serious bug, but it's a known-loud failure now
        // (logs) rather than a silent-stuck-screen mystery.
        val saveResult = runCatching { interactionRepository.save(interaction) }
        if (saveResult.isFailure) {
            val cause = saveResult.exceptionOrNull()
            Log.e(
                TAG,
                "Failed to persist interaction ${interaction.mobileSyncId} — " +
                    "skipping PostCall navigation. Call data is lost; " +
                    "investigate the exception below.",
                cause,
            )
            // Surface the failure to the UI so the agent — and we
            // when debugging — see WHY the call vanished, rather than
            // losing the record silently. Format includes the
            // exception type so a screenshot tells us enough to fix
            // the root cause without logcat access.
            _saveError.value = "No se pudo guardar la llamada: " +
                "${cause?.javaClass?.simpleName ?: "Error"} — " +
                (cause?.message ?: "sin mensaje")
            return
        }

        // Local-first: record the call's local side-effects RIGHT NOW
        // with the placeholder outcome (bump callAttempts, set
        // lastCalledAt/lastOutcome), before any sync runs.
        //
        // In the 5-state model the app does NOT decide the status here —
        // a placeholder no-contact outcome leaves the client PENDING. The
        // sync push below uploads the interaction first; the pull then
        // re-fetches `assigned?status=PENDING` and `replaceAllByStatus`
        // converges the row to the server snapshot, which already
        // reflects that push (incremented attempts, canonical status).
        // No "vanish" risk: the client comes back in the PENDING snapshot.
        // Only the safe high-water-mark advances (INTERESTED/SCHEDULED/
        // SOLD) move the status locally, and those land in PostCall.
        runCatching {
            clientRepository.applyInteractionLocally(
                clientId = client.id,
                outcome = interaction.outcome,
                callStartedAt = startedAt,
            )
        }.onFailure {
            Log.w(
                TAG,
                "applyInteractionLocally failed for client ${client.id} — " +
                    "Recientes may miss this call until next sync resolves it.",
                it,
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
