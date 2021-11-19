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

package androidx.window

import android.app.Activity
import android.graphics.Rect
import androidx.window.layout.WindowMetricsCalculator

object WindowHelper {
    //NOTE!! Date: 16/11/2021
    //Keep this code up-to-date manually
    //Latest codebase
    //implementation "androidx.window:window:1.0.0-beta03"


    /* For some reasons exception happens on foldable devices if we use androidx.window.WindowManager(activity)

   Fatal Exception: java.lang.AbstractMethodError: abstract method "void androidx.window.sidecar.SidecarInterface$SidecarCallback.onDeviceStateChanged(androidx.window.sidecar.SidecarDeviceState)"
      at androidx.window.sidecar.SamsungSidecarImpl.updateDevicePosture(SamsungSidecarImpl.java:86)
      at androidx.window.sidecar.SamsungSidecarImpl.access$000(SamsungSidecarImpl.java:43)
      at androidx.window.sidecar.SamsungSidecarImpl$SamsungSidecarCallbackListener.onDeviceStateChanged(SamsungSidecarImpl.java:62)
      at android.view.WindowManagerGlobal.lambda$handleDeviceStateChangedEventIfNeedLocked$2$WindowManagerGlobal(WindowManagerGlobal.java:1150)
      at android.view.-$$Lambda$WindowManagerGlobal$jfB49vh4VxV3HJn7Ersg7WbshIM.run(-.java:2)
      at android.os.Handler.handleCallback(Handler.java:938)
      at android.os.Handler.dispatchMessage(Handler.java:99)
      at android.os.Looper.loop(Looper.java:246)
      at android.app.ActivityThread.main(ActivityThread.java:8506)
      at java.lang.reflect.Method.invoke(Method.java)
      at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:602)
      at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1130)

      And

      Fatal Exception: java.lang.AbstractMethodError: abstract method "void androidx.window.sidecar.SidecarInterface$SidecarCallback.onDeviceStateChanged(androidx.window.sidecar.SidecarDeviceState)"
      at androidx.window.sidecar.MicrosoftSurfaceSidecar.updateDeviceState(MicrosoftSurfaceSidecar.java:159)
      at androidx.window.sidecar.MicrosoftSurfaceSidecar$1.deviceStateChanged(MicrosoftSurfaceSidecar.java:192)
      at android.vendor.screenlayout.service.IWindowExtensionCallbackInterface$Stub.onTransact(IWindowExtensionCallbackInterface.java:94)
      at android.os.Binder.execTransactInternal(Binder.java:1021)
      at android.os.Binder.execTransact(Binder.java:994)
   * */
    fun getMaximumWindowMetrics(mActivity: Activity): Rect {
        return WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(mActivity).bounds
    }

    fun getCurrentWindowMetrics(mActivity: Activity): Rect {
        return WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(mActivity).bounds
    }

}