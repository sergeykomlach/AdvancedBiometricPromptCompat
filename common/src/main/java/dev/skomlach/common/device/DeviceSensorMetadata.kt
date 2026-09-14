package dev.skomlach.common.device

/** Verified corrections for missing database entries, keyed by hardware model, never marketing name. */
internal object DeviceSensorMetadata {
    fun complete(manufacturer: String, hardwareModel: String, info: DeviceInfo): DeviceInfo {
        if (info.emulatorKind != null || info.sensors.any { it.contains("fingerprint", ignoreCase = true) }) return info
        // Huawei's PRA-LX1 quick start guide, p. 2, places the scanner on the back:
        // https://consumer.huawei.com/za/support/content/en-us00455860/
        if (!manufacturer.equals("HUAWEI", ignoreCase = true) ||
            !hardwareModel.equals("PRA-LX1", ignoreCase = true)) return info
        return info.copy(sensors = info.sensors + "Fingerprint (rear-mounted)")
    }
}
