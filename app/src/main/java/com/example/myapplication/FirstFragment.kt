package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentFirstBinding
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonFirst.setOnClickListener {

            val biometricPromptCompat = BiometricPromptCompat.Builder(
                BiometricAuthRequest(BiometricApi.LEGACY_API, BiometricType.BIOMETRIC_FACE),
                requireActivity())
                    .setTitle("Mode: Legacy").setNegativeButton("Cancel", null).build()
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