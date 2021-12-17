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

package dev.skomlach.biometric.compat.impl.dialogs

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import androidx.core.content.ContextCompat
import dev.skomlach.biometric.compat.*
import dev.skomlach.biometric.compat.impl.AuthCallback
import dev.skomlach.biometric.compat.impl.AuthResult
import dev.skomlach.biometric.compat.utils.BiometricTitle
import dev.skomlach.biometric.compat.utils.DialogMainColor
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.statusbar.StatusBarTools
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicBoolean


class BiometricPromptCompatDialogImpl(
    private val compatBuilder: BiometricPromptCompat.Builder,
    private val authCallback: AuthCallback?,
    private val isInScreen: Boolean = false
) {

    private val animateHandler: Handler
    private val dialog: BiometricPromptCompatDialog
    private val promptText: CharSequence
    private val too_many_attempts: CharSequence
    private val not_recognized: CharSequence
    private val inProgress = AtomicBoolean(false)
    val WHAT_RESTORE_NORMAL_STATE = 0

    var authFinishedCopy: MutableMap<BiometricType?, AuthResult> = mutableMapOf()

    private var stopWatcher: Runnable? = null
    init {
        promptText = BiometricTitle.getRelevantTitle(compatBuilder.getContext(), compatBuilder.getAllAvailableTypes())
        too_many_attempts =
            compatBuilder.getContext()
                .getString(androidx.biometric.R.string.fingerprint_error_lockout)
        not_recognized =
            compatBuilder.getContext()
                .getString(androidx.biometric.R.string.fingerprint_not_recognized)
        animateHandler = AnimateHandler(Looper.getMainLooper())
        dialog = BiometricPromptCompatDialog(compatBuilder, isInScreen)
        dialog.setOnDismissListener { dialogInterface: DialogInterface? ->
            detachWindowListeners()
            if (inProgress.get()) {
                inProgress.set(false)
                authCallback?.stopAuth()
            }
            authCallback?.onUiClosed()
            stopWatcher?.run()
            stopWatcher = null
        }
        dialog.setOnCancelListener {
            authCallback?.cancelAuth()
            detachWindowListeners()
            if (inProgress.get()) {
                inProgress.set(false)
                authCallback?.stopAuth()
            }
        }
        dialog.setOnShowListener { d: DialogInterface? ->
            e("BiometricPromptGenericImpl" + "AbstractBiometricPromptCompat. started.")
            dialog.window?.let {

                StatusBarTools.setNavBarAndStatusBarColors(
                    it,
                    DialogMainColor.getColor( compatBuilder.getContext(),DarkLightThemes.isNightMode(compatBuilder.getContext())),
                    DialogMainColor.getColor( compatBuilder.getContext(), !DarkLightThemes.isNightMode(compatBuilder.getContext())),
                    compatBuilder.getStatusBarColor()
                )

            }

            if (compatBuilder.getTitle() == null) {
                dialog.title?.visibility = View.GONE
            } else {
                dialog.title?.text = compatBuilder.getTitle()
            }
            if (compatBuilder.getSubtitle() == null) {
                dialog.subtitle?.visibility = View.GONE
            } else {
                dialog.subtitle?.text = compatBuilder.getSubtitle()
            }
            if (compatBuilder.getDescription() == null) {
                dialog.description?.visibility = View.GONE
            } else {
                dialog.description?.text = compatBuilder.getDescription()
            }
            if (compatBuilder.getNegativeButtonText() == null) {
                dialog.negativeButton?.visibility = View.INVISIBLE
            } else {
                dialog.negativeButton?.text = compatBuilder.getNegativeButtonText()
                dialog.negativeButton?.setOnClickListener { v: View? ->
                    dismissDialog()
                    authCallback?.cancelAuth()
                }
            }
            dialog.status?.text = promptText
            originalColor = dialog.status?.textColors

            dialog.fingerprintIcon?.setState(
                FingerprintIconView.State.ON,
                false,
                primaryBiometricType
            )

            checkInScreenVisibility()
            attachWindowListeners()
            startAuth()
        }
    }

    private val homeWatcher =
        HomeWatcher(compatBuilder.getContext(), object : HomeWatcher.OnHomePressedListener {
            override fun onHomePressed() {
                dialog.cancel()
            }

            override fun onRecentAppPressed() {
                dialog.cancel()
            }

            override fun onPowerPressed() {
                dialog.cancel()
            }
        })
    private var primaryBiometricType: BiometricType = BiometricType.BIOMETRIC_ANY
        private set
        get() {
            val list = mutableListOf<BiometricType>()
            if (compatBuilder.getSecondaryAvailableTypes().isEmpty()) {
                for (type in compatBuilder.getPrimaryAvailableTypes()) {
                    val request = BiometricAuthRequest(
                        compatBuilder.getBiometricAuthRequest().api,
                        type,
                        compatBuilder.getBiometricAuthRequest().confirmation
                    )
                    if (BiometricManagerCompat.isBiometricReady(request))
                        list.add(type)
                }
            } else {
                for (type in compatBuilder.getSecondaryAvailableTypes()) {
                    val request = BiometricAuthRequest(
                        compatBuilder.getBiometricAuthRequest().api,
                        type,
                        compatBuilder.getBiometricAuthRequest().confirmation
                    )
                    if (BiometricManagerCompat.isBiometricReady(request))
                        list.add(type)
                }
            }
            list.removeAll(authFinishedCopy.keys)
            list.sortWith { o1, o2 -> o1.ordinal.compareTo(o2.ordinal) }
            e("BiometricPromptGenericImpl.primaryBiometricType - $list")
            return if (list.isEmpty()) BiometricType.BIOMETRIC_ANY else list[0]
        }
    private val onGlobalLayoutListener = OnGlobalLayoutListener {
        e( "BiometricPromptGenericImpl.onGlobalLayout - fallback dialog")
        checkInScreenVisibility()
    }
    private val onWindowFocusChangeListener: WindowFocusChangedListener =
        object : WindowFocusChangedListener {
            override fun onStartWatching() {
                e("BiometricPromptGenericImpl.onStartWatching")
            }

            override fun hasFocus(hasFocus: Boolean) {
                e("BiometricPromptGenericImpl.onWindowFocusChanged - fallback dialog $hasFocus")
                if (hasFocus) {
                    startAuth()
                } else {
                    if (isMultiWindowHack) {
                        if (isInScreen && isInScreenUIHackNeeded) {
                            e( "BiometricPromptGenericImpl.onWindowFocusChanged - do not cancelAuth - inScreenDevice and app on top")
                            return
                        } else {
                            e( "BiometricPromptGenericImpl.onWindowFocusChanged - do not cancelAuth - regular device in multiwindow")
                            return
                        }
                    }
                    e( "BiometricPromptGenericImpl.onWindowFocusChanged - cancelAuth")
                    cancelAuth()
                }
            }
        }
    private var originalColor: ColorStateList? = null

    private fun attachWindowListeners() {
        try {
            val v = dialog.findViewById<View>(Window.ID_ANDROID_CONTENT)
            if(!isInScreen) {
                dialog.setWindowFocusChangedListener(onWindowFocusChangeListener)
            }
            v?.viewTreeObserver?.addOnGlobalLayoutListener(onGlobalLayoutListener)
        } catch (we: Throwable) {
            e(we)
        }
    }

    private fun detachWindowListeners() {
        try {
            val v = dialog.findViewById<View>(Window.ID_ANDROID_CONTENT)
            if(!isInScreen) {
                dialog.setWindowFocusChangedListener(null)
            }
            v?.viewTreeObserver?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        } catch (we: Throwable) {
            e(we)
        }
    }

    //in case devices is InScreenScanner and app switched to the SplitScreen mode and app placed on the top of screen
    //we need to show Fingerprint icon
    private val isInScreenUIHackNeeded: Boolean
        get() = compatBuilder.getMultiWindowSupport().isInMultiWindow && !compatBuilder.getMultiWindowSupport()
            .isWindowOnScreenBottom()

    //in case app switched to the SplitScreen mode we need to skip onPause on lost focus cases
    private val isMultiWindowHack: Boolean
        get() = if (compatBuilder.getMultiWindowSupport().isInMultiWindow && inProgress.get() && dialog.isShowing) {
            e( "BiometricPromptGenericImpl.isMultiWindowHack - perform hack")
            authCallback?.stopAuth()
            authCallback?.startAuth()
            true
        } else {
            e( "BiometricPromptGenericImpl.isMultiWindowHack - do not perform hack")
            false
        }

    private fun checkInScreenVisibility() {
        if (isInScreen) {
            if (isInScreenUIHackNeeded || compatBuilder.getMultiWindowSupport().screenOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                dialog.makeInvisible()
            } else {
                dialog.makeVisible()
            }
        }
    }

    private fun startAuth() {
        if (!inProgress.get() && dialog.isShowing) {
            inProgress.set(true)
            authCallback?.startAuth()
        }
    }

    private fun cancelAuth() {
        if (inProgress.get() && dialog.isShowing) {
            inProgress.set(false)
            authCallback?.stopAuth()
        }
    }

    fun showDialog() {
        require(!dialog.isShowing) { "BiometricPromptGenericImpl. has been started." }
        stopWatcher = homeWatcher.startWatch()
        dialog.show()
    }

    val authPreview: View?
        get() = dialog.authPreview

    fun dismissDialog() {
        if (dialog.isShowing) {
            detachWindowListeners()
            cancelAuth()
            dialog.dismiss()
        }
    }

    fun onHelp(msg: CharSequence?) {
        ExecutorHelper.post {
            animateHandler.removeMessages(WHAT_RESTORE_NORMAL_STATE)

            dialog.fingerprintIcon?.setState(FingerprintIconView.State.ERROR, primaryBiometricType)

            dialog.status?.text = msg
            dialog.status?.setTextColor(
                ContextCompat.getColor(
                    compatBuilder.getContext(), R.color.material_red_500
                )
            )
            animateHandler.sendEmptyMessageDelayed(
                WHAT_RESTORE_NORMAL_STATE,
                2000
            )
        }
    }

    fun onFailure(isLockout: Boolean) {
        ExecutorHelper.post {
            animateHandler.removeMessages(WHAT_RESTORE_NORMAL_STATE)

            dialog.fingerprintIcon?.setState(FingerprintIconView.State.ERROR, primaryBiometricType)

            dialog.status?.text = if (isLockout) too_many_attempts else not_recognized
            dialog.status?.setTextColor(
                ContextCompat.getColor(
                    compatBuilder.getContext(), R.color.material_red_500
                )
            )
            animateHandler.sendEmptyMessageDelayed(
                WHAT_RESTORE_NORMAL_STATE,
                2000
            )
        }
    }

    private inner class AnimateHandler constructor(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                WHAT_RESTORE_NORMAL_STATE -> {
                    dialog.fingerprintIcon?.setState(
                        FingerprintIconView.State.ON,
                        primaryBiometricType
                    )
                    dialog.status?.text = promptText
                    dialog.status?.setTextColor(originalColor)
                }
            }
        }
    }
}