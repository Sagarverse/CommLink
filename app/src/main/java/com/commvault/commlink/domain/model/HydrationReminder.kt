package com.commvault.commlink.domain.model

data class HydrationReminder(
    val id: String,
    val hour: Int,       // 0-23
    val minute: Int,     // 0-59
    val isEnabled: Boolean = true,
    val label: String = "Rehydrate Yourself"
)
