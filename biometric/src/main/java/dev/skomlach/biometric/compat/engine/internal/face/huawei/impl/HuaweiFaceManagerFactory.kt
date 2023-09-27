/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.engine.internal.face.huawei.impl

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d

object HuaweiFaceManagerFactory {
    private const val TAG = "HuaweiFaceManagerFactory"
    private var mFaceImplV1: HuaweiFaceManagerV1Impl? = null
    fun getHuaweiFaceManager(): HuaweiFaceManager? {
        d(TAG, "HuaweiManager getHuaweiFaceManager")
        if (mFaceImplV1 == null) {
            mFaceImplV1 = HuaweiFaceManagerV1Impl()
        }
        return mFaceImplV1
    }
}