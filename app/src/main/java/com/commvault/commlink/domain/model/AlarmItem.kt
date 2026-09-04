package com.commvault.commlink.domain.model

data class AlarmItem(
    val id: String,
    val title: String,
    val timeInMillis: Long,
    val alarmType: String, // "Standard", "Escalating", "Interval"
    val maxVolume: Int,
    val intervalSecs: Int,
    val repeatCount: Int,
    var isEnabled: Boolean = true
)
