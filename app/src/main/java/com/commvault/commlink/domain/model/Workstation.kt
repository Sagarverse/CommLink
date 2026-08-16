package com.commvault.commlink.domain.model

data class Workstation(
    val address: String,
    val name: String,
    val lastConnected: Long = System.currentTimeMillis()
)
