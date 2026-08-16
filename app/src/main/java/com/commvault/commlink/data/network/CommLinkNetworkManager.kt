package com.commvault.commlink.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredPeer(val deviceName: String, val ipAddress: InetAddress, val port: Int)

class CommLinkNetworkManager private constructor(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_commlink._tcp."
    private var serviceName = "CommLink_Device"
    private var serverSocket: ServerSocket? = null
    private var localPort = 0
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers

    private val peerMap = ConcurrentHashMap<String, DiscoveredPeer>()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    var onMessageReceived: ((JSONObject) -> Unit)? = null

    companion object {
        @Volatile
        private var instance: CommLinkNetworkManager? = null

        fun getInstance(context: Context): CommLinkNetworkManager {
            return instance ?: synchronized(this) {
                instance ?: CommLinkNetworkManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun startNetworking(userName: String) {
        this.serviceName = "CommLink_$userName"
        startServerSocket()
    }

    private fun startServerSocket() {
        scope.launch {
            try {
                serverSocket = ServerSocket(0).also { socket ->
                    localPort = socket.localPort
                    registerService(localPort)
                    discoverServices()
                    
                    while (!socket.isClosed) {
                        val client = socket.accept()
                        handleIncomingConnection(client)
                    }
                }
            } catch (e: Exception) {
                Log.e("CommLinkNetwork", "Error starting server", e)
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        scope.launch {
            try {
                val senderIp = socket.inetAddress.hostAddress
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val data = reader.readLine()
                if (!data.isNullOrEmpty()) {
                    val json = JSONObject(data)
                    json.put("senderIp", senderIp)
                    onMessageReceived?.invoke(json)
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("CommLinkNetwork", "Error handling connection", e)
            }
        }
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = this@CommLinkNetworkManager.serviceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                serviceName = NsdServiceInfo.serviceName
                Log.d("CommLinkNetwork", "Registered service as $serviceName on port $port")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("CommLinkNetwork", "Service registration failed: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun discoverServices() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("CommLinkNetwork", "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == SERVICE_TYPE && service.serviceName != serviceName) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("CommLinkNetwork", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host
                            val port = serviceInfo.port
                            val name = serviceInfo.serviceName.removePrefix("CommLink_")
                            val peer = DiscoveredPeer(name, host, port)
                            peerMap[name] = peer
                            updatePeersFlow()
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val name = service.serviceName.removePrefix("CommLink_")
                peerMap.remove(name)
                updatePeersFlow()
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun updatePeersFlow() {
        _discoveredPeers.value = peerMap.values.toList()
    }

    fun sendMessage(targetName: String, payload: JSONObject, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val peer = peerMap.values.find { it.deviceName.equals(targetName, ignoreCase = true) }
        if (peer == null) {
            onError("User not found on network")
            return
        }

        scope.launch {
            try {
                val socket = Socket(peer.ipAddress, peer.port)
                val out = PrintWriter(socket.getOutputStream(), true)
                out.println(payload.toString())
                socket.close()
                onSuccess()
            } catch (e: Exception) {
                Log.e("CommLinkNetwork", "Failed to send message", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun sendFile(
        senderName: String,
        targetName: String, 
        fileName: String, 
        fileSize: Long, 
        fileUri: android.net.Uri, 
        onSuccess: () -> Unit, 
        onError: (String) -> Unit
    ) {
        val peer = peerMap.values.find { it.deviceName.equals(targetName, ignoreCase = true) }
        if (peer == null) {
            onError("User not found on network")
            return
        }

        scope.launch {
            try {
                val fileServerSocket = ServerSocket(0)
                val filePort = fileServerSocket.localPort
                
                val payload = JSONObject().apply {
                    put("type", "FILE_OFFER")
                    put("senderName", senderName)
                    put("fileName", fileName)
                    put("fileSize", fileSize)
                    put("port", filePort)
                }

                val socket = Socket(peer.ipAddress, peer.port)
                val out = PrintWriter(socket.getOutputStream(), true)
                out.println(payload.toString())
                socket.close()

                fileServerSocket.soTimeout = 30000 // 30 seconds
                val fileSocket = fileServerSocket.accept()
                
                val inputStream = context.contentResolver.openInputStream(fileUri)
                val outputStream = fileSocket.getOutputStream()
                
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                fileSocket.close()
                fileServerSocket.close()
                
                onSuccess()
            } catch (e: Exception) {
                Log.e("CommLinkNetwork", "Failed to send file", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }
    
    fun downloadFile(ipAddressStr: String, port: Int, fileName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val ipAddress = InetAddress.getByName(ipAddressStr)
                val socket = Socket(ipAddress, port)
                val inputStream = socket.getInputStream()
                
                val downloadsDir = java.io.File(context.cacheDir, "CommLinkDownloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val file = java.io.File(downloadsDir, fileName)
                val outputStream = java.io.FileOutputStream(file)
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                socket.close()
                onSuccess(file.absolutePath)
            } catch (e: Exception) {
                Log.e("CommLinkNetwork", "Failed to download file", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun stopNetworking() {
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("CommLinkNetwork", "Error stopping networking", e)
        }
    }
}
