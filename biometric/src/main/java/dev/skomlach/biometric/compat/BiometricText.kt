package dev.skomlach.biometric.compat

import androidx.annotation.StringRes
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.translate.LocalizationHelper

internal fun biometricText(@StringRes resId: Int, vararg args: Any): String {
    return LocalizationHelper.getLocalizedString(AndroidContext.appContext, resId, *args)
}

internal fun biometricApiDisabledDescription(): String =
    biometricText(R.string.biometriccompat_api_disabled_error)

internal fun biometricStartAuthenticationDescription(): String =
    biometricText(R.string.biometriccompat_start_authentication_error)

internal fun biometricUnknownErrorDescription(): String =
    biometricText(R.string.biometriccompat_generic_error)

internal fun biometricErrorWithCodeDescription(errorCode: Int): String =
    biometricText(R.string.biometriccompat_generic_error_with_code, errorCode)

internal fun biometricRequiredCryptoRejectedDescription(): String =
    biometricText(R.string.biometriccompat_required_crypto_rejected_error)

internal fun biometricRequiredCryptoMissingDescription(): String =
    biometricText(R.string.biometriccompat_required_crypto_missing_error)

internal fun softwareBiometricHardwareBackedCryptoUnsupportedDescription(name: String): String =
    biometricText(R.string.biometriccompat_software_hardware_backed_crypto_unsupported, name)

internal fun biometricActivityDestroyedDescription(): String =
    biometricText(R.string.biometriccompat_activity_destroyed_error)

internal fun biometricInternalErrorDescription(): String =
    biometricText(R.string.biometriccompat_internal_error)
