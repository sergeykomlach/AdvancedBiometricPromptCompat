package dev.skomlach.common.device

import dev.skomlach.common.device.DeviceSpecManager.getSensors

/** Partial review summaries must not hide a complete database/fallback sensor record. */
internal fun DeviceSpec.hasUsableSensorSource(): Boolean =
    metadata["spec_source"] != "review_inline" || getSensors().isNotEmpty()
