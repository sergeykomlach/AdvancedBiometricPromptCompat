package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import dev.skomlach.biometric.compat.BiometricAuthRequest
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
        val view = inflater.inflate(
            R.layout.fragment_first,
            container,
            false
        )

        val buttonsList = view.findViewById<LinearLayout>(R.id.buttons_list)

        if (!App.isReady) {
            App.onInitListeners.add(object : App.OnInitFinished {
                override fun onFinished() {
                    fillList(inflater, buttonsList)
                }
            })
        } else {
            fillList(inflater, buttonsList)
        }

        return view
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

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        // 3.
        requireDialog().window?.setWindowAnimations(
            R.style.DialogAnimation
        )
    }

    fun startBiometric(biometricAuthRequest: BiometricAuthRequest) {

        val biometricPromptCompat = BiometricPromptCompat.Builder(
            biometricAuthRequest,
            requireActivity()
        )
            .setTitle("Biometric for DialogFragment").setNegativeButton("Cancel", null).build()
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
