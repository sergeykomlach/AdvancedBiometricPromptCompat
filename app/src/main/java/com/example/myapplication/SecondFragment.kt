package com.example.myapplication

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.example.myapplication.databinding.FragmentSecondBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment(), LogCat.Log2ViewCallback {
    lateinit var scrollView: ScrollView
    lateinit var logs: TextView
    var autoscroll = true
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView = binding.scrollView
        scrollView.setOnTouchListener(OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                autoscroll = false
            } else if (event.action == MotionEvent.ACTION_UP) {
                autoscroll = true
            }
            false
        })
        logs = binding.logs
        logs.setOnLongClickListener {
            val clipboardManager =
                it.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.text = logs.text
            Toast.makeText(it.context, "Log copied to clipboard", Toast.LENGTH_LONG).show()
            true
        }
        binding.buttonSecond.setOnClickListener {
            NavHostFragment.findNavController(this@SecondFragment)
                .navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
        LogCat.getInstance().setFilter("Biometric")
        LogCat.getInstance().setLog2ViewCallback(this@SecondFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun log(log: String?) {
        val sb: StringBuilder = StringBuilder(logs.text)
        sb.append(log).append("\n")
        logs.text = sb.toString()
        if (autoscroll) {
            scrollView.smoothScrollTo(0, logs.bottom)
        }
    }
}