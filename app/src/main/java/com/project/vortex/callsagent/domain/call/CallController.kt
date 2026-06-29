package com.project.vortex.callsagent.domain.call

import android.util.Log
import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.sip.AudioRoute
import com.project.vortex.callsagent.data.sip.AudioRouteState
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

    /**
     * Live audio-routing snapshot mirrored from the active [CallSession].
     * Defaults to speaker-only between calls; the session republishes the
     * real device set (and reacts to headset/Bluetooth hot-swaps) once a
     * call is up. See [selectRoute].
     */
    private val _audioRoute = MutableStateFlow(AudioRouteState.SpeakerOnly)
    val audioRoute: StateFlow<AudioRouteState> = _audioRoute.asStateFlow()

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
        _audioRoute.value = AudioRouteState.SpeakerOnly
        callStartedAt = Instant.now()

        scope.launch {
            // Wait for a TERMINAL registration outcome — Registered (go) or
            // Failed (stop now instead of burning the full timeout). The
            // watchdog is independently re-registering in the background, so a
            // transient drop usually resolves before the agent re-dials.
            val outcome = withTimeoutOrNull(REGISTER_WAIT_MS) {
                coreManager.registrationState.first {
                    it is SipRegistrationState.Registered || it is SipRegistrationState.Failed
                }
            }
            if (outcome !is SipRegistrationState.Registered) {
                Log.e(TAG, "REGISTER not ready before dialing: $outcome")
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
     * Route the call's audio to [route] at the agent's explicit request.
     * Delegates to the active [CallSession], which records the manual
     * pick and re-publishes the [audioRoute] snapshot (mirrored here via
     * [attachSession]). No-op when there is no active call.
     */
    fun selectRoute(route: AudioRoute) {
        val active = session
        if (active == null) {
            Log.w(TAG, "selectRoute($route) ignored — no active session")
            return
        }
        active.selectRoute(route)
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
            launch { s.audioRoute.collect { _audioRoute.value = it } }
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
            // updated before sync. Fall back to NO_SELECTED (not NO_ANSWER)
            // when there's no insight (e.g. orphan): an unknown ending must
            // not fabricate a "no contestó" the agent never chose.
            outcome = insight?.suggestedOutcome ?: CallOutcome.NO_SELECTED,
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
        // optimistic agentCallAttempts bump moves the client from "Sin
        // llamar" to "Para reintentar" right away. A pull can race ahead of
        // the async push (pull-to-refresh, reconnect, the Clients init
        // refresh), so `replaceAllAssigned` floors the server count with
        // this local value (max) — that keeps the client out of "Sin llamar"
        // until the push lands, after which the row converges to the server
        // snapshot (canonical status/attempts).
        // No "vanish" risk: the client stays in the assigned mirror even
        // if it turned terminal (it only leaves on unassign/hard-delete).
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
