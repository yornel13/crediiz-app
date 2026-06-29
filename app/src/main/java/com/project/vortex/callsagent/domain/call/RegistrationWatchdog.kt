package com.project.vortex.callsagent.domain.call

import com.project.vortex.callsagent.data.sip.LinphoneCoreManager
import com.project.vortex.callsagent.data.sip.SipRegistrationState
import com.project.vortex.callsagent.data.sync.InCallGate
import com.project.vortex.callsagent.data.voip.VoipAccountRepository
import com.project.vortex.callsagent.data.voip.VoipAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive safety net for the SIP registration.
 *
 * The registration can silently drop during normal use — a UDP NAT binding
 * decays after a call's RTP activity, a refresh fails, or the SBC drops the
 * binding — and nothing in the call flow re-registers. Before this watchdog the
 * only recovery paths were a foreground resume (debounced to 60s) and a 3h
 * periodic refresh, so a dropped registration stayed stuck on "Conectando con
 * el servidor de llamadas…" until the agent minimized and reopened the app.
 *
 * This watchdog closes that gap: whenever the registration is NOT healthy while
 * the agent is idle and holds a VoIP line, it re-registers on an escalating
 * backoff until it recovers — no user action, no app restart.
 *
 * It deliberately does NOTHING:
 *  - while a call is active (re-registering mid-call would disrupt media), and
 *  - while there is no VoIP account (nothing to register).
 *
 * Mechanics: [combine] + [distinctUntilChanged] + [collectLatest]. When the
 * "needs recovery" condition flips off (back to Registered, into a call, or
 * unassigned), the in-flight grace/backoff loop is cancelled automatically, so
 * the backoff resets cleanly with no manual bookkeeping. The pure decision
 * lives in [needsRecovery] for direct unit testing.
 */
@Singleton
class RegistrationWatchdog internal constructor(
    private val registrationState: Flow<SipRegistrationState>,
    private val inCall: Flow<Boolean>,
    private val availability: Flow<VoipAvailability>,
    private val recover: suspend () -> Unit,
    private val graceMillis: Long,
    private val backoffMillis: List<Long>,
) {
    init {
        require(backoffMillis.isNotEmpty()) { "backoffMillis must not be empty" }
        require(graceMillis >= 0) { "graceMillis must be >= 0" }
    }

    @Inject
    constructor(
        coreManager: LinphoneCoreManager,
        voipAccountRepository: VoipAccountRepository,
        inCallGate: InCallGate,
    ) : this(
        registrationState = coreManager.registrationState,
        inCall = inCallGate.observeIsInCall(),
        availability = voipAccountRepository.voipAvailability,
        recover = { coreManager.ensureRegistered() },
        graceMillis = DEFAULT_GRACE_MS,
        backoffMillis = DEFAULT_BACKOFF_MS,
    )

    /**
     * Start watching on [scope]. Returns the [Job] so the caller owns
     * cancellation. Safe to run for the whole process lifetime.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(registrationState, inCall, availability) { reg, call, avail ->
            needsRecovery(reg, call, avail)
        }
            .distinctUntilChanged()
            .collectLatest { needsRecovery ->
                if (!needsRecovery) return@collectLatest
                // Grace: don't pre-empt a normal register handshake
                // (InProgress -> Ok). Only act if STILL unhealthy afterward.
                // collectLatest cancels this whole block the moment the
                // condition flips back to false.
                delay(graceMillis)
                var attempt = 0
                while (true) {
                    try {
                        recover()
                    } catch (ce: CancellationException) {
                        throw ce // cooperate with collectLatest cancellation
                    } catch (_: Throwable) {
                        // A failed recovery attempt must NOT kill the watchdog.
                        // The cause is logged at the recover() implementation
                        // (LinphoneCoreManager.ensureRegistered); the next
                        // backoff tick simply tries again.
                    }
                    delay(backoffMillis[attempt.coerceAtMost(backoffMillis.lastIndex)])
                    attempt++
                }
            }
    }

    companion object {
        /**
         * Wait before the FIRST recovery attempt so a normal register handshake
         * (InProgress -> Ok) isn't pre-empted by a spurious re-register.
         */
        const val DEFAULT_GRACE_MS = 4_000L

        /** Escalating delays between recovery attempts; caps at the last value. */
        val DEFAULT_BACKOFF_MS = listOf(2_000L, 5_000L, 10_000L, 15_000L)

        /**
         * Pure decision: the registration needs recovery when the agent HAS a
         * VoIP line, is NOT in a call, and the SIP layer is anything but
         * Registered. Exposed for unit testing.
         */
        fun needsRecovery(
            reg: SipRegistrationState,
            inCall: Boolean,
            availability: VoipAvailability,
        ): Boolean =
            availability is VoipAvailability.Available &&
                !inCall &&
                reg !is SipRegistrationState.Registered
    }
}
