package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

/** Stop may unblock a read, but only the recording worker may release its recorder. */
internal class VoiceRecordingLifetime(
    private val startRecording: () -> Unit,
    private val stopRecording: () -> Unit,
    private val releaseRecorder: () -> Unit
) {
    @Volatile
    private var cancelled = false
    @Volatile
    private var finished = false
    private var started = false

    val isActive: Boolean get() = !cancelled && !finished

    @Synchronized
    fun start(): Boolean {
        if (!isActive) return false
        started = true
        startRecording()
        return true
    }

    @Synchronized
    fun cancel() {
        cancelled = true
        stopIfStarted()
    }

    /** Called in the worker's finally block, after its last read has returned. */
    @Synchronized
    fun finish() {
        if (finished) return
        finished = true
        try {
            stopIfStarted()
        } finally {
            releaseRecorder()
        }
    }

    private fun stopIfStarted() {
        if (!started) return
        started = false
        stopRecording()
    }
}

