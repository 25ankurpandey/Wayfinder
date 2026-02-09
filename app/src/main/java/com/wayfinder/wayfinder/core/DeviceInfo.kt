package com.wayfinder.wayfinder.core

data class DeviceInfo(
    val ipAddress: String, // this is a unique identifier for each device
    val deviceName: String?,
    var lastBroadcastTime: Long = System.currentTimeMillis() // Track the last time we received a broadcast
)
