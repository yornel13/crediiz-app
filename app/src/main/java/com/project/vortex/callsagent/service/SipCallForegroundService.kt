package com.project.vortex.callsagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.domain.call.CallController
import com.project.vortex.callsagent.presentation.incall.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the process alive during a SIP call so Android 14+ does not
 * silence the microphone or kill the audio thread when the agent
 * minimizes the app.
 *
 * Lifecycle:
 *  - Started by [com.project.vortex.callsagent.CallsAgentApp] when the
 *    call state transitions out of Idle/Disconnected.
 *  - Stops itself (via `stopService`) when the call returns to idle.
 *
 * Android 16 hardening:
 *  - `foregroundServiceType="microphone"` declared in the manifest.
 *  - Acquires an [AudioFocusRequest] with `USAGE_VOICE_COMMUNICATION`
 *    BEFORE switching `AudioManager.mode` to `MODE_IN_COMMUNICATION` —
 *    on Android 16 the mode change is silently ignored without focus.
 *  - Notification has a "Colgar" action so the system considers it an
 *    actionable call notification (otherwise it may be dismissable).
 *
 * Tap the notification body → opens [InCallActivity].
 * Tap "Colgar" → asks [CallController] to disconnect.
 */
@AndroidEntryPoint
class SipCallForegroundService : Service() {

    @Inject lateinit var callController: CallController

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var modeWasChanged = false
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        applyVoiceCommunicationAudioMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANGUP) {
            Log.d(TAG, "Hangup action received from notification")
            runCatching { callController.disconnect() }
                .onFailure { Log.e(TAG, "disconnect() threw", it) }
            // The CallController state transition will trigger
            // CallsAgentApp.observeCallStateForUi → stopService.
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        restoreAudioMode()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Switch the system audio mode to MODE_IN_COMMUNICATION while the
     * call is active. This is the canonical mode for VoIP — it activates
     * the right routing rules (earpiece vs. speaker), echo cancellation
     * hooks, and Bluetooth SCO eligibility.
     *
     * On Android 16+, `setMode` requires the caller to hold an active
     * [AudioFocusRequest] of usage VOICE_COMMUNICATION; otherwise the
     * call is silently ignored. We acquire focus first, then change
     * the mode. Both are restored in [onDestroy].
     */
    private fun applyVoiceCommunicationAudioMode() {
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return

        val focusRequest = AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
        )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener {
                // Linphone handles focus loss internally (it watches its
                // own listener). We log for visibility only.
                Log.d(TAG, "Audio focus changed: $it")
            }
            .build()

        val focusResult = am.requestAudioFocus(focusRequest)
        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = focusRequest
        } else {
            Log.w(TAG, "Audio focus not granted (result=$focusResult); proceeding anyway")
        }

        previousAudioMode = am.mode
        runCatching { am.mode = AudioManager.MODE_IN_COMMUNICATION }
            .onSuccess { modeWasChanged = true }
            .onFailure { Log.w(TAG, "Could not switch AudioManager.mode", it) }
    }

    private fun restoreAudioMode() {
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager
        if (modeWasChanged && am != null) {
            runCatching { am.mode = previousAudioMode }
                .onFailure { Log.w(TAG, "Could not restore AudioManager.mode", it) }
            modeWasChanged = false
        }
        audioFocusRequest?.let { req ->
            am?.abandonAudioFocusRequest(req)
            audioFocusRequest = null
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sip_call_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.sip_call_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        val openInCall = PendingIntent.getActivity(
            this,
            REQ_OPEN_IN_CALL,
            Intent(this, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hangup = PendingIntent.getService(
            this,
            REQ_HANGUP,
            Intent(this, SipCallForegroundService::class.java).apply {
                action = ACTION_HANGUP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sip_call_notification_title))
            .setContentText(getString(R.string.sip_call_notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openInCall)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.sip_call_notification_hangup),
                hangup,
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "sip_call_active"
        private const val NOTIFICATION_ID = 1001
        private const val REQ_OPEN_IN_CALL = 1
        private const val REQ_HANGUP = 2
        private const val TAG = "SipCallFG"

        /** Sent by the notification "Colgar" action. */
        private const val ACTION_HANGUP =
            "com.project.vortex.callsagent.action.SIP_CALL_HANGUP"

        fun start(context: Context) {
            val intent = Intent(context, SipCallForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SipCallForegroundService::class.java))
        }
    }
}
