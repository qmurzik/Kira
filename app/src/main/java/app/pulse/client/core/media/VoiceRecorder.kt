package app.pulse.client.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Voice message recorder.
 * API 29+: OGG/Opus (the format VK uses for voice messages).
 * API 21-28: AAC in MP4 container — whether VK accepts it as audio_message is unverified.
 */
class VoiceRecorder(private val context: Context) {

    data class Result(val file: File, val mime: String, val durationMs: Long)

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var mime = "audio/ogg"

    val isRecording: Boolean get() = recorder != null

    fun start() {
        stopQuietly()
        val ogg = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        mime = if (ogg) "audio/ogg" else "audio/mp4"
        val out = File(context.cacheDir, "voice_${System.currentTimeMillis()}." + if (ogg) "ogg" else "m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else legacyRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        if (ogg) {
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
        } else {
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
        r.setAudioSamplingRate(48_000)
        r.setAudioEncodingBitRate(32_000)
        r.setAudioChannels(1)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = out
        startedAt = System.currentTimeMillis()
    }

    /** Stops and returns the file, or null if the recording was too short / failed. */
    fun stop(): Result? {
        val r = recorder ?: return null
        val f = file
        recorder = null
        val duration = System.currentTimeMillis() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || f == null || duration < 700) {
            f?.delete()
            return null
        }
        return Result(f, mime, duration)
    }

    @Suppress("DEPRECATION")
    private fun legacyRecorder() = MediaRecorder()

    fun cancel() {
        stopQuietly()
    }

    private fun stopQuietly() {
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
        file?.delete()
        file = null
    }
}
