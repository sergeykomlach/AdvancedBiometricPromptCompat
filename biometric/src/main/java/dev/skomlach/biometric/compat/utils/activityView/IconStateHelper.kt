/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
package dev.skomlach.biometric.compat.utils.activityView

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.common.misc.ExecutorHelper

object IconStateHelper {
    private val dispatcher = IconStateDispatcher(
        ExecutorHelper::post, ExecutorHelper::postDelayed, ExecutorHelper::removeCallbacks
    )
    fun registerListener(stateListener: IconStateListener) {
        dispatcher.register(stateListener)
    }

    fun unregisterListener(stateListener: IconStateListener) {
        dispatcher.unregister(stateListener)
    }

    fun errorType(type: BiometricType?) {
        dispatcher.error(type)
    }

    fun successType(type: BiometricType?) {
        dispatcher.success(type)
    }

    internal fun refreshAvailability() = dispatcher.refreshAvailability()

    interface IconStateListener {
        fun onError(type: BiometricType?)
        fun onSuccess(type: BiometricType?)
        fun reset(type: BiometricType?)
        fun onAvailabilityChanged() {}
    }
}
