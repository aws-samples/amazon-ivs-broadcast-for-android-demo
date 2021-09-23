package com.amazon.ivs.broadcast.models.ui

data class DeviceItem(
    val type: String,
    val deviceId: String,
    val direction: String,
    var isSelected: Boolean = false,
    var viewId: Int = -1
)
