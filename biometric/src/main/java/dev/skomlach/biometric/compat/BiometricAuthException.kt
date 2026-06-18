package dev.skomlach.biometric.compat

/**
 * Exception type used by coroutine/KTX authentication APIs for terminal auth failures.
 */
class BiometricAuthException : Exception {
    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

}
