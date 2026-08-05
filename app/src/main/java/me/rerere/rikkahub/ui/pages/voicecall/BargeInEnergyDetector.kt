package me.rerere.rikkahub.ui.pages.voicecall

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.asr.calculateRmsAmplitude
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Lightweight mic energy monitor used only while TTS is speaking.
 * Does not take exclusive audio focus so playback can continue until barge-in fires.
 */
class BargeInEnergyDetector(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private val fired = AtomicBoolean(false)

    fun start(onDetected: () -> Unit) {
        stop()
        fired.set(false)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted, barge-in disabled")
            return
        }
        job = scope.launch(Dispatchers.IO) {
            delay(GRACE_MS)
            if (!isActive || fired.get()) return@launch
            val detected = runDetector()
            if (detected && isActive) {
                withContext(Dispatchers.Main.immediate) {
                    onDetected()
                }
            }
        }
    }

    fun stop() {
        fired.set(true)
        job?.cancel()
        job = null
    }

    /**
     * @return true if sustained speech energy was detected (mic released before return).
     */
    private suspend fun runDetector(): Boolean {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            Log.w(TAG, "invalid minBufferSize=$minBufferSize")
            return false
        }
        val bufferSize = minBufferSize.coerceAtLeast(SAMPLE_RATE / 10 * 2).coerceAtLeast(4096)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord create failed", e)
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord not initialized")
            recorder.release()
            return false
        }

        var loudSince = 0L
        var detected = false
        try {
            recorder.startRecording()
            val buffer = ByteArray(bufferSize)
            while (coroutineContext.isActive && !fired.get()) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val rms = calculateRmsAmplitude(buffer, read)
                    val now = System.currentTimeMillis()
                    if (rms >= RMS_THRESHOLD) {
                        if (loudSince == 0L) loudSince = now
                        if (now - loudSince >= HOLD_MS && fired.compareAndSet(false, true)) {
                            VoiceCallDiag.log(TAG, "detected rms=$rms hold=${now - loudSince}ms")
                            detected = true
                            break
                        }
                    } else {
                        loudSince = 0L
                    }
                } else if (read < 0) {
                    Log.w(TAG, "AudioRecord read error: $read")
                    break
                }
            }
        } catch (e: Exception) {
            if (!fired.get()) {
                Log.e(TAG, "barge-in detector failed", e)
            }
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {
            }
            try {
                recorder.release()
            } catch (_: Exception) {
            }
        }
        return detected
    }

    private companion object {
        private const val TAG = "BargeInEnergy"
        private const val SAMPLE_RATE = 16_000
        private const val GRACE_MS = 400L
        private const val HOLD_MS = 300L
        /** Higher than Listening UI_SPEECH_RMS to reduce speaker echo false triggers. */
        private const val RMS_THRESHOLD = 0.28f
    }
}
