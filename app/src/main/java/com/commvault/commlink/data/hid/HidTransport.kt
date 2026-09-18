package com.commvault.commlink.data.hid

import kotlinx.coroutines.flow.StateFlow

interface HidTransport {
    val connectionState: StateFlow<ConnectionState>
    val isReady: Boolean

    fun connect()
    fun disconnect()
    fun sendReportInternal(id: Int, data: ByteArray)

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val deviceName: String) : ConnectionState()
    }
}
