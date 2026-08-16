package com.commvault.commlink.domain.model

data class KeyEventModel(
    val keyCode: Byte,
    val modifier: Byte = 0
)
