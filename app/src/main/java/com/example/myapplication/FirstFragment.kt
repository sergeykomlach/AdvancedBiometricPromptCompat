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

package com.example.myapplication

import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentFirstBinding
import com.example.myapplication.utils.startBiometric
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.common.storage.SharedPreferenceProvider
import dev.skomlach.common.storage.applyOrCommit
import leakcanary.LeakCanary

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
                    } catch (e : Throwable){}
                    fillList(inflater, binding?.buttonsList)
                    checkDeviceInfo()


                }
            })
        } else {
            fillList(inflater, binding?.buttonsList)
        }
        binding?.checkboxFullscreen?.isChecked =
            SharedPreferenceProvider.getPreferences("app_settings").getBoolean("checkboxFullscreen", false)

        binding?.checkboxFullscreen?.setOnCheckedChangeListener { buttonView, isChecked ->
            SharedPreferenceProvider.getPreferences("app_settings").edit()
                .putBoolean("checkboxFullscreen", isChecked).applyOrCommit()
            (activity as MainActivity).updateUI()
            Toast.makeText(context, "Changes applied", Toast.LENGTH_LONG).show()
        }

        binding?.checkboxWindowSecure?.isChecked =
            SharedPreferenceProvider.getPreferences("app_settings").getBoolean("checkboxWindowSecure", false)

        binding?.checkboxWindowSecure?.setOnCheckedChangeListener { buttonView, isChecked ->
            SharedPreferenceProvider.getPreferences("app_settings").edit()
                .putBoolean("checkboxWindowSecure", isChecked).applyOrCommit()
            (activity as MainActivity).updateUI()
            Toast.makeText(context, "Changes applied", Toast.LENGTH_LONG).show()
        }

        binding?.buttonFirst?.setOnClickListener {
            (activity as MainActivity).sendLogs()

        }
        binding?.buttonSecond?.setOnClickListener {
            (activity as MainActivity).showDialog()
        }
        binding?.buttonThird?.setOnClickListener {
            activity?.startActivity(LeakCanary.newLeakDisplayActivityIntent())
        }
        return binding?.root
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
                startBiometric(BiometricAuthRequest(authRequest.api, authRequest.type))
            }
            buttonsList?.addView(container)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}