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

import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.Fragment
//import com.readystatesoftware.chuck.Chuck
import dev.skomlach.biometric.app.databinding.FragmentFirstBinding
import dev.skomlach.biometric.app.utils.startBiometric
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.storage.SharedPreferenceProvider

//import leakcanary.LeakCanary

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)

        if (!App.isReady) {
            val dialog = ProgressDialog.show(
                activity, "",
                "Initialization in progress...", true
            )
            App.onInitListeners.add(object : App.OnInitFinished {
                override fun onFinished() {
                    try {
                        dialog.dismiss()
                    } catch (e: Throwable) {
                    }
                    fillList(inflater, binding?.buttonsList)
                    checkDeviceInfo()


                }
            })
        } else {
            fillList(inflater, binding?.buttonsList)
            checkDeviceInfo()
        }
        updateUi()
        return binding?.root?.apply {
            this.viewTreeObserver.addOnGlobalLayoutListener {
                updateUi()
            }
        }
    }

    private fun updateUi() {

        binding?.checkboxFullscreen?.isChecked =
            SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean("checkboxFullscreen", false)

        binding?.checkboxFullscreen?.setOnCheckedChangeListener { buttonView, isChecked ->
            SharedPreferenceProvider.getPreferences("app_settings").edit()
                .putBoolean("checkboxFullscreen", isChecked).apply()
            (activity as MainActivity).updateUI()
            Toast.makeText(AndroidContext.appContext, "Changes applied", Toast.LENGTH_LONG).show()
        }

        binding?.checkboxWindowSecure?.isChecked =
            SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean("checkboxWindowSecure", false)

        binding?.checkboxWindowSecure?.setOnCheckedChangeListener { buttonView, isChecked ->
            SharedPreferenceProvider.getPreferences("app_settings").edit()
                .putBoolean("checkboxWindowSecure", isChecked).apply()
            (activity as MainActivity).updateUI()
            Toast.makeText(AndroidContext.appContext, "Changes applied", Toast.LENGTH_LONG).show()
        }
        binding?.checkboxCrypto?.isChecked =
            SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean("crypto", false)
        binding?.allowDeviceCredentials?.isChecked =
            SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean(
                    "allowDeviceCredentials",
                    BiometricManagerCompat.isDeviceSecureAvailable()
                )
        binding?.checkboxCrypto?.setOnCheckedChangeListener { buttonView, isChecked ->
            SharedPreferenceProvider.getPreferences("app_settings").edit()
                .putBoolean("crypto", isChecked).apply()
        }
        binding?.allowDeviceCredentials?.setOnCheckedChangeListener { buttonView, isChecked ->
            SharedPreferenceProvider.getPreferences("app_settings").edit()
                .putBoolean("allowDeviceCredentials", isChecked).apply()
        }
        binding?.checkboxSilent?.isChecked =
            SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean("silent", false)
        binding?.checkboxSilent?.setOnCheckedChangeListener { buttonView, isChecked ->
            SharedPreferenceProvider.getPreferences("app_settings").edit()
                .putBoolean("silent", isChecked).apply()
        }

        binding?.buttonFirst?.setOnClickListener {
            (activity as MainActivity).sendLogs()

        }
        binding?.buttonSecond?.setOnClickListener {
            (activity as MainActivity).showDialog()
        }
//        binding?.buttonForth?.setOnClickListener {
//            (activity as MainActivity).startActivity(Chuck.getLaunchIntent(it.context))
//        }
//        binding?.buttonThird?.setOnClickListener {
////            activity?.startActivity(LeakCanary.newLeakDisplayActivityIntent())
//        }
    }

    private fun checkDeviceInfo() {
        val deviceInfo = BiometricPromptCompat.deviceInfo
        binding?.text?.text = deviceInfo.toString()
    }

    private fun fillList(inflater: LayoutInflater, buttonsList: LinearLayout?) {
        for (authRequest in App.authRequestList) {
            val container: FrameLayout =
                inflater.inflate(R.layout.button, buttonsList, false) as FrameLayout
            val button = container.findViewById<Button>(R.id.button)
            button.text = "${authRequest.api}/${authRequest.type}"
            button.setOnClickListener {
                startBiometric(
                    BiometricAuthRequest(authRequest.api, authRequest.type),
                    SharedPreferenceProvider.getPreferences("app_settings")
                        .getBoolean("silent", false),
                    SharedPreferenceProvider.getPreferences("app_settings")
                        .getBoolean("crypto", false),
                    SharedPreferenceProvider.getPreferences("app_settings")
                        .getBoolean(
                            "allowDeviceCredentials",
                            BiometricManagerCompat.isDeviceSecureAvailable()
                        )
                )
            }
            buttonsList?.addView(container)
        }

        BiometricAuthentication.availableBiometrics.forEach { type ->
            val biometricModule = BiometricAuthentication.getAvailableBiometricModule(type)
            biometricModule?.let { module ->
                if (module is AbstractBiometricModule) {
                    val sb = StringBuilder()
                    val list = module.getHashes()
                    if (list.isEmpty())
                        sb.append("${type?.name}").append(":No enrolled data")
                    else {
                        list.forEach {
                            sb.append("${type?.name}").append(":").append(it).append("\n")
                        }
                        sb.append("\n")
                    }

                    buttonsList?.addView(AppCompatTextView(requireActivity()).apply {
                        this.text = sb.toString()
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val authRequest = BiometricAuthRequest()
        startBiometric(
            BiometricAuthRequest(authRequest.api, authRequest.type),
            SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean("silent", false),
            SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean("crypto", false),
            SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean(
                    "allowDeviceCredentials",
                    BiometricManagerCompat.isDeviceSecureAvailable()
                )
        )
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}