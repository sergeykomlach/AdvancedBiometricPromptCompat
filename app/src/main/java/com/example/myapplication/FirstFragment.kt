package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentFirstBinding
import dev.skomlach.biometric.compat.BiometricPromptCompat
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
            BiometricPromptCompat.init(Runnable {
                val biometricPromptCompat = BiometricPromptCompat.Builder(requireActivity())
                    .setTitle("Test").setNegativeButton("Cancel", null).build()
                biometricPromptCompat.authenticate(object : BiometricPromptCompat.Result {
                    override fun onSucceeded() {
                    }

                    override fun onCanceled() {
                    }

                    override fun onFailed(reason: AuthenticationFailureReason?) {
                    }

                    override fun onUIShown() {
                    }
                })
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}