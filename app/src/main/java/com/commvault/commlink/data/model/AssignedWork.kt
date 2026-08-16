package com.commvault.commlink.data.model

import java.util.UUID

enum class WorkStatus {
    PENDING, IN_PROGRESS, COMPLETED
}

enum class WorkPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class AssignedWork(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val managerName: String,
    val dueDate: String,
    var status: WorkStatus = WorkStatus.PENDING,
    val priority: WorkPriority = WorkPriority.MEDIUM
)
