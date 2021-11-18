/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat

/**
 * General categories for authentication failures.
 */
enum class AuthenticationFailureReason {
    /**
     * The device does not have a fingerprint sensor that is recognized by any of the Core modules.
     */
    NO_HARDWARE,

    /**
     * The sensor is temporarily unavailable, perhaps because the device is locked, or another
     * operation is already pending.
     */
    HARDWARE_UNAVAILABLE,

    /**
     * The user has not registered any fingerprints with the system.
     */
    NO_BIOMETRICS_REGISTERED,

    /**
     * The sensor was unable to read the fingerprint, perhaps because the finger was moved too
     * quickly, or the sensor was dirty.
     */
    SENSOR_FAILED,

    /**
     * Too many failed attempts have been made, and the user cannot make another attempt for an
     * unspecified amount of time.
     */
    LOCKED_OUT,

    /**
     * The sensor has been running for too long without reading anything.
     *
     *
     * The amount of time that the sensor can be running before a timeout is system and sensor specific, but
     * is usually around 30 seconds. It is safe to immediately start another authentication attempt.
     */
    TIMEOUT,

    /**
     * A fingerprint was read successfully, but that fingerprint was not registered on the device.
     */
    AUTHENTICATION_FAILED,

    /**
     * The authentication failed for an unknown reason.
     */
    UNKNOWN,

    /**
     * Internal API error
     */
    INTERNAL_ERROR,

    /**
     * API not initialized
     */
    NOT_INITIALIZED_ERROR,

    /**
     * Biometric can't be starte due to missing permissions
     */
    MISSING_PERMISSIONS_ERROR
}