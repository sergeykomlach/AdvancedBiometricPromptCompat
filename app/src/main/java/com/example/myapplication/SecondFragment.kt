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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.example.myapplication.databinding.FragmentSecondBinding
import com.example.myapplication.devtools.LogCat
import com.example.myapplication.utils.MailTo

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment(), LogCat.Log2ViewCallback {
    private var scrollView: ScrollView? = null
    private var logs: TextView? = null
    private var autoscroll = true
    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding?.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView = binding?.scrollView
        scrollView?.setOnTouchListener(OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                autoscroll = false
            } else if (event.action == MotionEvent.ACTION_UP) {
                autoscroll = true
            }
            false
        })
        logs = binding?.logs
        logs?.setOnLongClickListener {
            MailTo.startMailClient(
                requireActivity(),
                "s.komlach@gmail.com",
                "Advanced BiometricPromptCompat Logs",
                logs?.text.toString()
            )
            true
        }
        binding?.buttonSecond?.setOnClickListener {
            NavHostFragment.findNavController(this@SecondFragment)
                .navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
        LogCat.setFilter("Biometric")
        LogCat.setLog2ViewCallback(this@SecondFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun log(log: String) {
        val sb: StringBuilder = StringBuilder(logs?.text ?: "")
        sb.append(log).append("\n")
        logs?.text = sb.toString()
        if (autoscroll) {
            scrollView?.smoothScrollTo(0, logs?.bottom ?: return)
        }
    }
}