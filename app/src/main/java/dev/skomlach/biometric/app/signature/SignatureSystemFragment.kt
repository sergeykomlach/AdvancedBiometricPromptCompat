package dev.skomlach.biometric.app.signature

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.skomlach.biometric.app.R
import dev.skomlach.biometric.app.MainActivity
import dev.skomlach.biometric.app.databinding.FragmentSignatureSystemBinding
import dev.skomlach.biometric.compat.SupplementalBiometricOutcome
import dev.skomlach.biometric.compat.engine.internal.behavior.BehaviorQualityIssue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignatureSystemFragment : Fragment() {
    private var binding: FragmentSignatureSystemBinding? = null
    private var flow: SignatureSystemFlow? = null
    private var securedWindow: Window? = null
    private var acquiredSecureFlag = false
    private var secureContent: AutoCloseable? = null
    private var sampleCheck: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentSignatureSystemBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = binding ?: return
        val behavior = BehaviorSignalClient(requireContext())
        val system = SystemBiometricClient(requireActivity())
        flow = SignatureSystemFlow(behavior::evaluate, system::authenticate, ::render)
        ui.signaturePad.onInputRejected = {
            binding?.signatureStatus?.setText(R.string.signature_system_input_rejected)
        }
        ui.signatureSubmit.setOnClickListener {
            val current = flow ?: return@setOnClickListener
            if (current.isRunning || sampleCheck?.isActive == true) return@setOnClickListener
            val sample = ui.signaturePad.snapshotSample()
            if (sample == null) {
                ui.signatureStatus.setText(R.string.signature_system_need_signature)
                return@setOnClickListener
            }
            ui.signaturePad.isEnabled = false
            ui.signatureSubmit.isEnabled = false
            sampleCheck = viewLifecycleOwner.lifecycleScope.launch {
                val valid = withContext(Dispatchers.Default) { sample.qualityIssue() == BehaviorQualityIssue.NONE }
                if (valid) {
                    ui.signaturePad.abortCapture()
                    current.submit(sample)
                } else {
                    ui.signaturePad.isEnabled = true
                    ui.signatureSubmit.isEnabled = true
                    ui.signatureStatus.setText(R.string.signature_system_need_signature)
                }
            }
        }
        ui.signatureRestart.setOnClickListener {
            sampleCheck?.cancel()
            flow?.reset()
            ui.signaturePad.beginCapture()
        }
        ui.signatureCancel.setOnClickListener {
            sampleCheck?.cancel()
            flow?.cancel()
            ui.signaturePad.abortCapture()
        }
    }

    override fun onStart() {
        super.onStart()
        flow?.reset()
        binding?.signaturePad?.beginCapture()
    }

    override fun onResume() {
        super.onResume()
        val host = activity
        if (host is MainActivity) {
            if (secureContent == null) secureContent = host.acquireSecureContent()
            return
        }
        activity?.window?.let { window ->
            if (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) {
                acquiredSecureFlag = true
                securedWindow = window
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    private fun render(state: SignatureSystemState) {
        val ui = binding ?: return
        val running = state.stage == SignatureSystemStage.BEHAVIOR || state.stage == SignatureSystemStage.SYSTEM
        ui.signaturePad.isEnabled = !running
        ui.signatureSubmit.isEnabled = !running
        ui.signatureRestart.isEnabled = !running
        ui.signatureCancel.isEnabled = running
        ui.behaviorStatus.setText(when (state.behavior) {
            SupplementalBiometricOutcome.MATCHED -> R.string.signature_system_matched
            SupplementalBiometricOutcome.NOT_MATCHED -> R.string.signature_system_not_matched
            SupplementalBiometricOutcome.UNAVAILABLE -> R.string.signature_system_unavailable
            null -> R.string.signature_system_no_signal
        })
        ui.signatureStatus.setText(when (state.stage) {
            SignatureSystemStage.READY -> R.string.signature_system_ready
            SignatureSystemStage.BEHAVIOR -> R.string.signature_system_checking
            SignatureSystemStage.SYSTEM -> R.string.signature_system_prompt
            SignatureSystemStage.AUTHORIZED -> R.string.signature_system_success
            SignatureSystemStage.FAILED -> R.string.signature_system_failed
            SignatureSystemStage.CANCELED -> R.string.signature_system_canceled
        })
    }

    override fun onStop() {
        sampleCheck?.cancel()
        flow?.cancel()
        binding?.signaturePad?.abortCapture()
        super.onStop()
    }

    override fun onDestroyView() {
        sampleCheck?.cancel()
        sampleCheck = null
        flow?.close()
        flow = null
        binding?.signaturePad?.onInputRejected = null
        binding?.signaturePad?.abortCapture()
        binding = null
        secureContent?.close()
        secureContent = null
        if (acquiredSecureFlag) securedWindow?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        acquiredSecureFlag = false
        securedWindow = null
        super.onDestroyView()
    }
}
