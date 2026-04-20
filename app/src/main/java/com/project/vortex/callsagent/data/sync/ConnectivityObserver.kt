package com.project.vortex.callsagent.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConnectivityObserver"

/**
 * Observes network availability and triggers an immediate sync when we
 * go from "offline" → "online". Registers a [ConnectivityManager.NetworkCallback]
 * on first use and keeps it alive for the process lifetime (singleton).
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
    private val syncScheduler: SyncScheduler,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob())

    private val _isOnline = MutableStateFlow(currentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasOffline = !_isOnline.value
            _isOnline.value = true
            if (wasOffline) {
                Log.i(TAG, "Network restored — triggering immediate sync")
                syncScheduler.triggerImmediateSync()
            }
        }

        override fun onLost(network: Network) {
            // Check if any other network is still up before flipping.
            _isOnline.value = currentlyOnline()
        }
    }

    /** Start listening. Call once from app startup (after login). */
    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, callback) }
            .onFailure { Log.w(TAG, "Failed to register network callback", it) }
    }

    fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun currentlyOnline(): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** Convenience Flow alias used by the UI. */
fun ConnectivityObserver.asOnlineFlow(): Flow<Boolean> = isOnline
