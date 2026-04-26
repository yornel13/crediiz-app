package com.project.vortex.callsagent.presentation.incall

import androidx.lifecycle.ViewModel
import com.project.vortex.callsagent.telecom.CallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * Thin pass-through to [CallManager]. Exposes the singleton's state flows
 * to Compose without leaking the manager directly into UI code.
 */
@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callManager: CallManager,
) : ViewModel() {

    val callState = callManager.callState
    val currentClient = callManager.currentClient
    val callDirection = callManager.callDirection
    val incomingPhoneNumber = callManager.incomingPhoneNumber
    val isMuted = callManager.isMuted
    val isSpeakerOn = callManager.isSpeakerOn

    /** Two-way bound to the CallManager's live note content. */
    val liveNoteContent: MutableStateFlow<String> = callManager.liveNoteContent

    fun onNoteChange(text: String) {
        liveNoteContent.value = text
    }

    fun toggleMute() = callManager.mute(!isMuted.value)
    fun toggleSpeaker() = callManager.setSpeaker(!isSpeakerOn.value)
    fun endCall() = callManager.disconnect()
    fun acceptIncoming() = callManager.acceptIncoming()
    fun rejectIncoming() = callManager.rejectIncoming()
}
