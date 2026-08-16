package com.commvault.commlink.domain.repository

import com.commvault.commlink.domain.model.Workstation
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    val knownWorkstations: StateFlow<List<Workstation>>
    
    fun saveWorkstation(address: String, name: String)
    fun removeWorkstation(address: String)
    fun updateNickname(address: String, nickname: String)
}
