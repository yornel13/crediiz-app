package com.project.vortex.callsagent.data.sip

import org.linphone.core.AudioDevice

/**
 * Coarse audio-output categories the agent can pick during a call.
 * Collapses Linphone's granular [AudioDevice.Type] set into the four
 * buckets a user actually reasons about: the built-in speaker, the phone
 * earpiece, a wired headset (3.5mm / USB-C), and a Bluetooth headset.
 */
enum class AudioRoute {
    Speaker,
    Earpiece,
    WiredHeadset,
    Bluetooth,
}

/**
 * Snapshot of the call's audio routing: which route is active and which
 * ones the current hardware actually exposes. Drives the in-call UI — a
 * binary toggle when [available] has ≤2 entries, a device picker beyond.
 */
data class AudioRouteState(
    val current: AudioRoute,
    val available: List<AudioRoute>,
) {
    companion object {
        /** Default before a call exists / before devices are enumerated. */
        val SpeakerOnly = AudioRouteState(AudioRoute.Speaker, listOf(AudioRoute.Speaker))
    }
}

/**
 * Auto-routing preference order. When no explicit agent choice is in
 * effect, the highest-priority *available* route wins — a connected
 * headset beats the speaker, matching standard phone behavior.
 */
internal val AUTO_ROUTE_PRIORITY = listOf(
    AudioRoute.Bluetooth,
    AudioRoute.WiredHeadset,
    AudioRoute.Speaker,
    AudioRoute.Earpiece,
)

/**
 * Stable left-to-right order for rendering the route list in the UI.
 * Speaker first (the hands-free default on the Tab A9+), accessories
 * after, earpiece last.
 */
internal val DISPLAY_ROUTE_ORDER = listOf(
    AudioRoute.Speaker,
    AudioRoute.WiredHeadset,
    AudioRoute.Bluetooth,
    AudioRoute.Earpiece,
)

/**
 * Map a Linphone device type onto our coarse route bucket, or `null`
 * when it is not a user-facing playback route (microphone, telephony,
 * unknown, aux line).
 */
internal fun AudioDevice.Type.toAudioRouteOrNull(): AudioRoute? = when (this) {
    AudioDevice.Type.Speaker -> AudioRoute.Speaker
    AudioDevice.Type.Earpiece -> AudioRoute.Earpiece
    AudioDevice.Type.Bluetooth,
    AudioDevice.Type.BluetoothA2DP,
    AudioDevice.Type.HearingAid -> AudioRoute.Bluetooth
    AudioDevice.Type.Headset,
    AudioDevice.Type.Headphones,
    AudioDevice.Type.GenericUsb -> AudioRoute.WiredHeadset
    AudioDevice.Type.Unknown,
    AudioDevice.Type.Microphone,
    AudioDevice.Type.Telephony,
    AudioDevice.Type.AuxLine -> null
}

/**
 * Pick the best playback device from this Linphone device array.
 *
 * If [preferred] is set and a matching, playback-capable device exists,
 * that wins (honors an explicit agent choice). Otherwise the highest
 * [AUTO_ROUTE_PRIORITY] route with an available device is chosen.
 * Returns `null` when no playback device maps to a user-facing route.
 */
internal fun Array<AudioDevice>.preferredPlaybackDevice(
    preferred: AudioRoute? = null,
): AudioDevice? {
    val playable = filter { it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) }
    preferred?.let { target ->
        playable.firstOrNull { it.type.toAudioRouteOrNull() == target }?.let { return it }
    }
    return playable
        .mapNotNull { device -> device.type.toAudioRouteOrNull()?.let { device to it } }
        .minByOrNull { (_, route) -> AUTO_ROUTE_PRIORITY.indexOf(route) }
        ?.first
}
