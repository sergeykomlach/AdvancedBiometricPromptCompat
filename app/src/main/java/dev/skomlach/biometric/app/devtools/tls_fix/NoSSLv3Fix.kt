package dev.skomlach.biometric.app.devtools.tls_fix

import javax.net.ssl.HttpsURLConnection

object NoSSLv3Fix {
    fun setFix() {
        HttpsURLConnection.setDefaultSSLSocketFactory(NoSSLv3Factory())
    }
}