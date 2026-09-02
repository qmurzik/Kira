package com.qmurzik.animetv.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs item 13 ("Работа при плохом интернете"): screens observe [isOnline] to show a
 * friendly "you're offline" state and auto-retry the moment connectivity returns, instead of
 * just letting requests fail once and staying stuck.
 */
@Singleton
class NetworkConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isOnline(): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(hasAnyValidatedNetwork(connectivityManager))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }
        }

        trySend(hasAnyValidatedNetwork(connectivityManager))
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun hasAnyValidatedNetwork(manager: ConnectivityManager): Boolean {
        // ConnectivityManager#getActiveNetwork() is API 23+; minSdk here is 21 (many generic
        // Android TV boxes still ship Lollipop/early Marshmallow), so pre-23 falls back to the
        // deprecated but still-functional getActiveNetworkInfo().
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            manager.activeNetworkInfo?.isConnected == true
        }
    }
}
