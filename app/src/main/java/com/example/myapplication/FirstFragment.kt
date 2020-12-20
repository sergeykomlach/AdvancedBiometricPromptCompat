package com.example.myapplication

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
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)


        if (!App.isReady) {
            App.onInitListeners.add(object : App.OnInitFinished {
                override fun onFinished() {
                    fillList(inflater, binding.buttonsList)
                }
            })
        } else {
            fillList(inflater, binding.buttonsList)
        }

        return binding.root
    }

    private fun fillList(inflater: LayoutInflater, buttonsList: LinearLayout) {
        for (authRequest in App.authRequestList) {
            val container: FrameLayout =
                inflater.inflate(R.layout.button, buttonsList, false) as FrameLayout
            val button = container.findViewById<Button>(R.id.button)
            button.text = "${authRequest.api}/${authRequest.type}"
            button.setOnClickListener {
                startBiometric(authRequest)
            }
            buttonsList.addView(container)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}