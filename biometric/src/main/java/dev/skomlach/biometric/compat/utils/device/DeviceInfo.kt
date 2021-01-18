package dev.skomlach.biometric.compat.utils.device

data class DeviceInfo(
    val model: String,
    val sensors: Set<String>?
)