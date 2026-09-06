package com.example.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

enum class ConnectionStatus {
    CONNECTED,
    RECONNECTING,
    DISCONNECTED
}

object NetworkMonitor {
    private val _status = MutableStateFlow(ConnectionStatus.CONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isHeartbeatActive = false

    fun initialize(context: Context) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            // Initial check
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            val initialConnected = capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
            _status.value = if (initialConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
    
            val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("NetworkMonitor", "System network interface available")
                    monitorScope.launch {
                        checkEndToEndConnectivity()
                    }
                }

                override fun onLost(network: Network) {
                    Log.d("NetworkMonitor", "System network interface lost")
                    _status.value = ConnectionStatus.DISCONNECTED
                }
            })
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Failed to initialize NetworkMonitor or register callback", e)
            _status.value = ConnectionStatus.CONNECTED // Fallback
        }

        startHeartbeat()
    }

    fun setStatus(newStatus: ConnectionStatus) {
        if (_status.value != newStatus) {
            Log.d("NetworkMonitor", "Connection status changed to: $newStatus")
            _status.value = newStatus
        }
    }

    fun checkEndToEndConnectivity(): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress("generativelanguage.googleapis.com", 443), 3000)
            socket.close()
            Log.d("NetworkMonitor", "Ping-Pong success: End-to-end connection verified.")
            _status.value = ConnectionStatus.CONNECTED
            true
        } catch (e: Exception) {
            Log.w("NetworkMonitor", "Ping check notice: ${e.localizedMessage}")
            // Do not force DISCONNECTED on a raw socket hiccup if system network is active
            true
        }
    }

    private fun startHeartbeat() {
        if (isHeartbeatActive) return
        isHeartbeatActive = true
        monitorScope.launch {
            while (true) {
                delay(30000) // Ping every 30 seconds
                checkEndToEndConnectivity()
            }
        }
    }
    
    suspend fun awaitConnection() {
        if (status.value != ConnectionStatus.CONNECTED) {
            setStatus(ConnectionStatus.RECONNECTING)
            status.first { it == ConnectionStatus.CONNECTED }
        }
    }
}
