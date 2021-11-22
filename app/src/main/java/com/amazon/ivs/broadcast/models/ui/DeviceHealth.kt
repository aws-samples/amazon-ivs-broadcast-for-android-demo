package com.amazon.ivs.broadcast.models.ui

data class DeviceHealth(
    val usedMemory: String = "0",
    val cpuTemp: String = "0",
    val cpuUsage: String = "0"
)
