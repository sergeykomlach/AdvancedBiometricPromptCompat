package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myapplication.databinding.FragmentSecondBinding
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSecond.setOnClickListener {
            val biometricPromptCompat = BiometricPromptCompat.Builder(
                BiometricAuthRequest(BiometricApi.BIOMETRIC_API, BiometricType.BIOMETRIC_IRIS),
                requireActivity())
                .setTitle("Mode: Auto").setNegativeButton("Cancel", null).build()
            biometricPromptCompat.authenticate(object : BiometricPromptCompat.Result {
                override fun onSucceeded() {
                    Toast.makeText(activity, "Succeeded", Toast.LENGTH_LONG).show()
                }

                override fun onCanceled() {
                    Toast.makeText(activity, "Canceled", Toast.LENGTH_LONG).show()
                }

                override fun onFailed(reason: AuthenticationFailureReason?) {
                    Toast.makeText(activity, "Error: $reason", Toast.LENGTH_LONG).show()
                }

                override fun onUIShown() {
                }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}