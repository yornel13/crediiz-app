package com.project.vortex.callsagent.presentation.dev

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.project.vortex.callsagent.data.sip.CallSession
import com.project.vortex.callsagent.data.sip.LinphoneCoreManager
import com.project.vortex.callsagent.data.sip.SipCallState
import com.project.vortex.callsagent.data.sip.SipRegistrationState
import com.project.vortex.callsagent.ui.theme.CallsAgendsTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Standalone debug Activity for milestones M1, M2 and M3:
 *  - Register against Voselia.
 *  - Place an outbound call (SRTP/SDES + Opus/G.722/G.711 negotiation).
 *  - Hangup, mute, DTMF.
 *
 * Launch from a workstation:
 * ```
 * adb shell am start -n com.project.vortex.callsagent/.presentation.dev.SipSmokeTestActivity
 * ```
 *
 * Lives under src/debug/ so it ships only in debug builds.
 */
@AndroidEntryPoint
class SipSmokeTestActivity : ComponentActivity() {

    private val recordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* re-checked on tap */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallsAgendsTheme {
                Scaffold { padding ->
                    SipSmokeTestScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        onRequestRecordAudio = {
                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        hasRecordAudio = ::hasRecordAudio,
                    )
                }
            }
        }
    }

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

@HiltViewModel
class SipSmokeTestViewModel @Inject constructor(
    val coreManager: LinphoneCoreManager,
) : ViewModel() {

    private val _activeSession = MutableStateFlow<CallSession?>(null)
    val activeSession: StateFlow<CallSession?> = _activeSession.asStateFlow()

    private val _inviteError = MutableStateFlow<String?>(null)
    val inviteError: StateFlow<String?> = _inviteError.asStateFlow()

    fun register() = coreManager.register()

    fun call(target: String) {
        if (target.isBlank()) return
        viewModelScope.launch {
            _inviteError.value = null
            val session = coreManager.placeCall(target.trim())
            _activeSession.value = session
            if (session == null) {
                _inviteError.value =
                    "Could not place call. Check the target format and registration."
            }
        }
    }

    fun hangup() {
        _activeSession.value?.disconnect()
    }

    fun toggleMute() {
        val s = _activeSession.value ?: return
        s.setMuted(!s.isMuted.value)
    }

    fun sendDtmf(digit: Char) {
        _activeSession.value?.dtmf(digit)
    }

    fun clearSessionIfDisconnected(state: SipCallState) {
        if (state is SipCallState.Disconnected) _activeSession.value = null
    }
}

@Composable
private fun SipSmokeTestScreen(
    modifier: Modifier = Modifier,
    onRequestRecordAudio: () -> Unit,
    hasRecordAudio: () -> Boolean,
    viewModel: SipSmokeTestViewModel = hiltViewModel(),
) {
    val regState by viewModel.coreManager.registrationState.collectAsStateWithLifecycle()
    val session by viewModel.activeSession.collectAsStateWithLifecycle()
    val callState = session?.state?.collectAsState()?.value ?: SipCallState.Idle
    val isMuted = session?.isMuted?.collectAsState()?.value ?: false
    val inviteError by viewModel.inviteError.collectAsStateWithLifecycle()

    var target by remember { mutableStateOf("+507 63425495") }

    LaunchedEffect(callState) { viewModel.clearSessionIfDisconnected(callState) }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SIP Smoke Test", style = MaterialTheme.typography.headlineSmall)

        // M1 — Registration
        Text("M1 — Registration", style = MaterialTheme.typography.titleMedium)
        Text("State: ${regState.label()}")
        Button(onClick = { viewModel.register() }) { Text("Register") }

        HorizontalDivider()

        // M2 + M3 — Outbound call
        Text("M2 + M3 — Outbound call", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target (number or sip: URI)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Call state: ${callState.label()}${if (isMuted) "  (muted)" else ""}")
        if (inviteError != null) {
            Text(
                text = "Error: $inviteError",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = session == null && regState is SipRegistrationState.Registered,
                onClick = {
                    if (!hasRecordAudio()) onRequestRecordAudio() else viewModel.call(target)
                },
            ) { Text("Call") }
            Button(
                enabled = session != null,
                onClick = { viewModel.hangup() },
            ) { Text("Hangup") }
            Button(
                enabled = session != null,
                onClick = { viewModel.toggleMute() },
            ) { Text(if (isMuted) "Unmute" else "Mute") }
        }
        Spacer(Modifier.height(4.dp))
        Text("DTMF", style = MaterialTheme.typography.labelLarge)
        DtmfPad(
            enabled = callState is SipCallState.Active,
            onDigit = viewModel::sendDtmf,
        )
    }
}

@Composable
private fun DtmfPad(enabled: Boolean, onDigit: (Char) -> Unit) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { digit ->
                    Button(
                        enabled = enabled,
                        onClick = { onDigit(digit) },
                        modifier = Modifier.width(56.dp),
                    ) { Text(digit.toString()) }
                }
            }
        }
    }
}

private fun SipRegistrationState.label(): String = when (this) {
    SipRegistrationState.Idle -> "idle"
    SipRegistrationState.InProgress -> "registering..."
    SipRegistrationState.Registered -> "REGISTERED"
    SipRegistrationState.Cleared -> "cleared"
    is SipRegistrationState.Failed -> "FAILED: $message"
}

private fun SipCallState.label(): String = when (this) {
    SipCallState.Idle -> "idle"
    SipCallState.Dialing -> "dialing..."
    SipCallState.Ringing -> "ringing..."
    is SipCallState.Active -> "ACTIVE since $activeSince"
    SipCallState.Disconnected -> "disconnected"
}
