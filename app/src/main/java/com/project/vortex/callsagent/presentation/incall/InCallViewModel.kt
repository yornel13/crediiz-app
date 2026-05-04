package com.project.vortex.callsagent.presentation.incall

import androidx.lifecycle.ViewModel
import com.project.vortex.callsagent.domain.call.CallController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * Thin pass-through to [CallController]. Exposes the singleton's state
 * flows to Compose without leaking the controller directly into UI.
 *
 * Outbound only — `acceptIncoming` / `rejectIncoming` were removed
 * with the Telecom layer (see SIP migration plan).
 */
@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callController: CallController,
) : ViewModel() {

    val callState = callController.callState
    val currentClient = callController.currentClient
    val callDirection = callController.callDirection
    val incomingPhoneNumber = callController.incomingPhoneNumber
    val isMuted = callController.isMuted
    val isSpeakerOn = callController.isSpeakerOn

    val liveNoteContent: MutableStateFlow<String> = callController.liveNoteContent

    fun onNoteChange(text: String) {
        liveNoteContent.value = text
    }

    fun toggleMute() = callController.mute(!isMuted.value)
    fun toggleSpeaker() = callController.setSpeaker(!isSpeakerOn.value)
    fun endCall() = callController.disconnect()
}
