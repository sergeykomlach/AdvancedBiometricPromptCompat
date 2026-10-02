package dev.skomlach.common.releasecheck

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/** A deliberately stalled, dual-stack tunnel, used only on an isolated QA device. */
class BlackholeVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            closeTunnel()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        closeTunnel()
        tunnel = Builder().setSession("Common QA black hole")
            .addAddress("10.77.0.1", 24).addDnsServer("10.77.0.2").addRoute("0.0.0.0", 0)
            .addAddress("fd77::1", 64).addRoute("::", 0).establish()
        return START_NOT_STICKY
    }
    private fun closeTunnel() {
        val closing = tunnel
        tunnel = null
        closing?.close()
    }
    override fun onDestroy() { try { closeTunnel() } finally { super.onDestroy() } }
    companion object { const val ACTION_STOP = "dev.skomlach.common.releasecheck.STOP_VPN" }
}
