/*
 *  Copyright (c) 2026 Sergey Komlach
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import android.content.Context
import android.hardware.usb.UsbDevice
import com.zkteco.android.biometric.core.device.ParameterHelper
import com.zkteco.android.biometric.core.device.TransportType
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintCaptureListener
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintSensor
import com.zkteco.android.biometric.module.fingerprintreader.FingprintFactory
import com.zkteco.android.biometric.module.fingerprintreader.ZKFingerService
import com.zkteco.android.biometric.module.fingerprintreader.exception.FingerprintException

/**
 * Source-level contract for the optional ZKTeco runtime. Its implementations remain opaque to
 * the rest of the provider, so an APK without the compile-only vendor runtime can fail closed.
 */
internal interface ZkFingerSdkBridge {
    interface Sensor

    fun createSensor(context: Context, device: UsbDevice): Sensor

    fun setCaptureListener(
        sensor: Sensor,
        deviceIndex: Int,
        onExtracted: (ByteArray?) -> Unit,
        onExtractError: (Int) -> Unit
    )

    fun setExceptionListener(sensor: Sensor, onDeviceException: () -> Unit)

    fun open(sensor: Sensor, deviceIndex: Int)

    fun startCapture(sensor: Sensor, deviceIndex: Int)

    fun stopCapture(sensor: Sensor, deviceIndex: Int)

    fun close(sensor: Sensor, deviceIndex: Int)

    fun destroy(sensor: Sensor)

    fun init(): Int

    fun free(): Int

    fun clear(): Int

    fun delete(id: String): Int

    fun verify(first: ByteArray, second: ByteArray): Int

    fun merge(first: ByteArray, second: ByteArray, third: ByteArray, merged: ByteArray): Int

    fun save(template: ByteArray, id: String): Int

    fun identify(template: ByteArray, result: ByteArray, threshold: Int, count: Int): Int

    companion object {
        fun loadOrNull(): ZkFingerSdkBridge? = safelyCreateZkFingerSdkBridge {
            DirectZkFingerSdkBridge()
        }
    }
}

/**
 * The only class that references ZKTeco types directly. If the consumer omits a vendor JAR or
 * native runtime, the factory catches the linkage failure before it can escape provider loading.
 */
private class DirectZkFingerSdkBridge : ZkFingerSdkBridge {
    // JVM/ART may defer resolving method-body references. Resolve required API types while
    // still inside loadOrNull's LinkageError boundary, without starting USB/JNI work or using
    // reflective class lookup/invocation. Consumer rules retain this optional-linkage boundary.
    @Suppress("unused")
    private val requiredApiTypes = arrayOf(
        ParameterHelper::class.java,
        FingprintFactory::class.java,
        FingerprintSensor::class.java,
        ZKFingerService::class.java
    )

    private class DirectSensor(val value: FingerprintSensor) : ZkFingerSdkBridge.Sensor

    override fun createSensor(context: Context, device: UsbDevice): ZkFingerSdkBridge.Sensor {
        val parameters = hashMapOf<String, Any>(
            ParameterHelper.PARAM_KEY_VID to device.vendorId,
            ParameterHelper.PARAM_KEY_PID to device.productId
        )
        return DirectSensor(
            FingprintFactory.createFingerprintSensor(context, TransportType.USB, parameters)
                ?: error("ZKFinger factory returned no sensor")
        )
    }

    override fun setCaptureListener(
        sensor: ZkFingerSdkBridge.Sensor,
        deviceIndex: Int,
        onExtracted: (ByteArray?) -> Unit,
        onExtractError: (Int) -> Unit
    ) {
        sensor.requireDirect().setFingerprintCaptureListener(
            deviceIndex,
            object : FingerprintCaptureListener {
                override fun captureOK(image: ByteArray?) = Unit

                override fun captureError(error: FingerprintException?) = Unit

                override fun extractOK(template: ByteArray?) {
                    onExtracted(template)
                }

                override fun extractError(errorCode: Int) {
                    onExtractError(errorCode)
                }
            }
        )
    }

    override fun setExceptionListener(
        sensor: ZkFingerSdkBridge.Sensor,
        onDeviceException: () -> Unit
    ) {
        sensor.requireDirect().SetFingerprintExceptionListener { onDeviceException() }
    }

    override fun open(sensor: ZkFingerSdkBridge.Sensor, deviceIndex: Int) {
        sensor.requireDirect().open(deviceIndex)
    }

    override fun startCapture(sensor: ZkFingerSdkBridge.Sensor, deviceIndex: Int) {
        sensor.requireDirect().startCapture(deviceIndex)
    }

    override fun stopCapture(sensor: ZkFingerSdkBridge.Sensor, deviceIndex: Int) {
        sensor.requireDirect().stopCapture(deviceIndex)
    }

    override fun close(sensor: ZkFingerSdkBridge.Sensor, deviceIndex: Int) {
        sensor.requireDirect().close(deviceIndex)
    }

    override fun destroy(sensor: ZkFingerSdkBridge.Sensor) {
        sensor.requireDirect().destroy()
    }

    override fun init(): Int = ZKFingerService.init()

    override fun free(): Int = ZKFingerService.free()

    override fun clear(): Int = ZKFingerService.clear()

    override fun delete(id: String): Int = ZKFingerService.del(id)

    override fun verify(first: ByteArray, second: ByteArray): Int = ZKFingerService.verify(first, second)

    override fun merge(
        first: ByteArray,
        second: ByteArray,
        third: ByteArray,
        merged: ByteArray
    ): Int = ZKFingerService.merge(first, second, third, merged)

    override fun save(template: ByteArray, id: String): Int = ZKFingerService.save(template, id)

    override fun identify(
        template: ByteArray,
        result: ByteArray,
        threshold: Int,
        count: Int
    ): Int = ZKFingerService.identify(template, result, threshold, count)

    private fun ZkFingerSdkBridge.Sensor.requireDirect(): FingerprintSensor =
        (this as? DirectSensor)?.value ?: error("ZKFinger sensor belongs to another SDK bridge")
}

internal fun safelyCreateZkFingerSdkBridge(
    create: () -> ZkFingerSdkBridge
): ZkFingerSdkBridge? = try {
    create()
} catch (_: LinkageError) {
    null
}
