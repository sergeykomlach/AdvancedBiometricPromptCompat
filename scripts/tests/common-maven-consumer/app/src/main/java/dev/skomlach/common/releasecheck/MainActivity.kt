package dev.skomlach.common.releasecheck

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import dev.skomlach.common.network.NetworkApi

/** Exercises the public Internet API from an independently resolved release AAR, also with R8. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this)
        setContentView(text)
        if (intent.getBooleanExtra("skipNetworkInit", false)) return
        val states = NetworkApi.networkState
        val poll = object : Runnable {
            override fun run() {
                val state = states.value
                text.text = "Internet: ${state.internetAccess} (${state.isConnected})"
                Log.i("CommonReleaseQA", "COMMON_RELEASE_STATE=${state.internetAccess}; connected=${NetworkApi.hasInternet()}")
                handler.postDelayed(this, 1_000)
            }
        }
        handler.post(poll)
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
