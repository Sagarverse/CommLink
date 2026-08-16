package com.commvault.commlink.data.model

import java.util.UUID

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false
)
