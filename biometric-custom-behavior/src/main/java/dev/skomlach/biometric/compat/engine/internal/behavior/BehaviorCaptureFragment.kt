package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.DialogFragment

/** A child of the compat prompt: shares its window, title, cancellation and theme. */
class BehaviorCaptureFragment : Fragment() {
    internal var controller: BehaviorCaptureController? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FrameLayout(inflater.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            controller?.installIn(this)
        }

    override fun onDestroyView() {
        controller?.dispose()
        controller = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        if (controller == null) view.post {
            if (isAdded && this.view === view && controller == null) {
                val parent = parentFragment as? DialogFragment
                if (parent != null) parent.dialog?.cancel()
                else parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
            }
        }
    }
}
