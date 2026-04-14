package dev.skomlach.biometric.compat.crypto

object AppFlowCryptoFacade {

    fun registerKeyForAppFlow(keyName: String) {
        AppFlowCryptoRegistry.setAccessType(keyName, CryptoAccessType.APP_FLOW)
    }

    fun registerKeyForBiometric(keyName: String) {
        AppFlowCryptoRegistry.setAccessType(keyName, CryptoAccessType.BIOMETRIC)
    }

    fun unlockWithAppSecret(keyName: String, secret: CharArray) {
        AppFlowSessionStore.unlock(keyName, secret)
    }

    fun lockAppFlow(keyName: String) {
        AppFlowSessionStore.close(keyName)
    }
}
