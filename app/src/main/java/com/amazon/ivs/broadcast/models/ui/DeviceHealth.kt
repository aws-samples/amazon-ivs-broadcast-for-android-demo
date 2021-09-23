package com.amazon.ivs.broadcast.models.ui

data class DeviceHealth(
    val usedMemory: String,
    val cpuTemp: String,
    val cpuUsage: String = "0"
)
