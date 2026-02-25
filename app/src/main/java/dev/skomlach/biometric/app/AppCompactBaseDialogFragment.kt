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

package dev.skomlach.biometric.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import dev.skomlach.biometric.app.utils.startBiometric
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.storage.SharedPreferenceProvider


class AppCompactBaseDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1.
        setStyle(STYLE_NORMAL, R.style.Theme_App_Dialog_FullScreen)
    }

    // 2.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(
            R.layout.fragment_first,
            container,
            false
        )

        val buttonsList = view.findViewById<LinearLayout>(R.id.buttons_list)
        view.findViewById<LinearLayout>(R.id.buttons).visibility = View.GONE
        view.findViewById<CheckBox>(R.id.checkboxFullscreen).visibility = View.GONE
        if (!App.isReady) {
            App.onInitListeners.add(object : App.OnInitFinished {
                override fun onFinished() {
                    fillList(inflater, buttonsList)
                    checkDeviceInfo()
                }
            })
        } else {
            fillList(inflater, buttonsList)
        }
        view.findViewById<CheckBox>(R.id.checkboxWindowSecure).isChecked =
            SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean("checkboxWindowSecure", false)

        view.findViewById<CheckBox>(R.id.checkboxWindowSecure)
            .setOnCheckedChangeListener { buttonView, isChecked ->
                SharedPreferenceProvider.getPreferences("app_settings").edit()
                    .putBoolean("checkboxWindowSecure", isChecked).apply()
                (activity as MainActivity).updateUI()
                Toast.makeText(AndroidContext.appContext, "Changes applied", Toast.LENGTH_LONG)
                    .show()
            }
        view.findViewById<CheckBox>(R.id.checkboxCrypto)?.let {
            it.isChecked =
                SharedPreferenceProvider.getPreferences("app_settings")
                    .getBoolean("crypto", false)
            it.setOnCheckedChangeListener { buttonView, isChecked ->
                SharedPreferenceProvider.getPreferences("app_settings").edit()
                    .putBoolean("crypto", isChecked).apply()
            }

        }
        view.findViewById<CheckBox>(R.id.allowDeviceCredentials)?.let {
            it.isChecked =
                SharedPreferenceProvider.getPreferences("app_settings")
                    .getBoolean(
                        "allowDeviceCredentials",
                        BiometricManagerCompat.isDeviceSecureAvailable()
                    )
            it.setOnCheckedChangeListener { buttonView, isChecked ->
                SharedPreferenceProvider.getPreferences("app_settings").edit()
                    .putBoolean("allowDeviceCredentials", isChecked).apply()
            }

        }
        view.findViewById<CheckBox>(R.id.checkboxSilent)?.let {
            it.isChecked =
                SharedPreferenceProvider.getPreferences("app_settings")
                    .getBoolean("silent", false)
            it.setOnCheckedChangeListener { buttonView, isChecked ->
                SharedPreferenceProvider.getPreferences("app_settings").edit()
                    .putBoolean("silent", isChecked).apply()
            }
        }
        return view
    }

    private fun checkDeviceInfo() {
        val deviceInfo = BiometricPromptCompat.deviceInfo
        view?.findViewById<TextView>(R.id.text)?.text = deviceInfo.toString()
    }

    private fun fillList(inflater: LayoutInflater, buttonsList: LinearLayout) {
        for (authRequest in App.authRequestList) {
            val container: FrameLayout =
                inflater.inflate(R.layout.button, buttonsList, false) as FrameLayout
            val button = container.findViewById<Button>(R.id.button)
            button.text = "${authRequest.api}/${authRequest.type}"
            button.setOnLongClickListener {
                startBiometric(
                    authRequest,
                    SharedPreferenceProvider.getPreferences("app_settings")
                        .getBoolean("silent", false),
                    SharedPreferenceProvider.getPreferences("app_settings")
                        .getBoolean("crypto", false),
                    SharedPreferenceProvider.getPreferences("app_settings")
                        .getBoolean(
                            "allowDeviceCredentials",
                            BiometricManagerCompat.isDeviceSecureAvailable()
                        ), true
                )
                true
            }
            button.setOnClickListener {
                startBiometric(
                    authRequest,
                    SharedPreferenceProvider.getPreferences("app_settings")
                        .getBoolean("silent", false),
                    SharedPreferenceProvider.getPreferences("app_settings")
                        .getBoolean("crypto", false),
                    SharedPreferenceProvider.getPreferences("app_settings")
                        .getBoolean(
                            "allowDeviceCredentials",
                            BiometricManagerCompat.isDeviceSecureAvailable()
                        ), false
                )
            }
            buttonsList.addView(container)
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        // 3.
        requireDialog().window?.setWindowAnimations(
            R.style.DialogAnimation
        )
    }
}
