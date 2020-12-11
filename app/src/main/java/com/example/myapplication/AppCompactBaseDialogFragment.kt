package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason

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
        return inflater.inflate(
            R.layout.fragment_first,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        // 3.
        requireDialog().window?.setWindowAnimations(
            R.style.DialogAnimation
        )

        view.findViewById<Button>(R.id.button_first).setOnClickListener {

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

        }
    }
}
