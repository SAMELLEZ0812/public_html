package com.willi.app.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.willi.app.WilliApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Identification du locuteur par empreinte vocale MFCC.
 * Enregistre le profil du créateur et compare lors de chaque activation.
 */
class SpeakerIdentification(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 512
        const val NUM_MFCC = 13
        const val SIMILARITY_THRESHOLD = 0.78f
        const val ENROLLMENT_SECONDS = 5
    }

    private val gson = Gson()
    private val db = WilliApplication.instance.database

    // ─── Enrôlement du créateur ───────────────────────────────────────────────

    suspend fun enrollCreator(name: String, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val samples = recordAudio(ENROLLMENT_SECONDS, onProgress)
            if (samples.isEmpty()) return@withContext false

            val mfccFeatures = extractMFCC(samples)
            val mfccJson = gson.toJson(mfccFeatures)

            val creator = com.willi.app.data.models.CreatorProfile(
                name = name,
                voiceMfccData = mfccJson
            )
            db.creatorDao().save(creator)
            true
        }

    // ─── Vérification du locuteur ─────────────────────────────────────────────

    suspend fun verifySpeaker(audioSamples: ShortArray): Float = withContext(Dispatchers.IO) {
        val creator = db.creatorDao().get() ?: return@withContext 0f
        if (creator.voiceMfccData.isBlank()) return@withContext 1f // Pas encore enrôlé → accepte tout

        val storedType = object : TypeToken<List<DoubleArray>>() {}.type
        val storedMfcc: List<DoubleArray> = try {
            gson.fromJson(creator.voiceMfccData, storedType)
        } catch (e: Exception) {
            return@withContext 0f
        }

        val inputMfcc = extractMFCC(audioSamples)
        computeSimilarity(storedMfcc, inputMfcc)
    }

    fun isCreator(similarity: Float) = similarity >= SIMILARITY_THRESHOLD

    // ─── Enregistrement audio ─────────────────────────────────────────────────

    private fun recordAudio(seconds: Int, onProgress: (Float) -> Unit): ShortArray {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(SAMPLE_RATE * seconds * 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
        )

        val totalSamples = SAMPLE_RATE * seconds
        val buffer = ShortArray(totalSamples)
        var offset = 0

        recorder.startRecording()
        val chunkSize = SAMPLE_RATE / 10
        while (offset < totalSamples) {
            val chunk = ShortArray(chunkSize.coerceAtMost(totalSamples - offset))
            val read = recorder.read(chunk, 0, chunk.size)
            if (read > 0) {
                System.arraycopy(chunk, 0, buffer, offset, read)
                offset += read
                onProgress(offset.toFloat() / totalSamples)
            }
        }

        recorder.stop()
        recorder.release()
        return buffer
    }

    // ─── Extraction MFCC ─────────────────────────────────────────────────────

    private fun extractMFCC(samples: ShortArray): List<DoubleArray> {
        val frames = mutableListOf<DoubleArray>()
        var i = 0
        while (i + FRAME_SIZE <= samples.size) {
            val frame = DoubleArray(FRAME_SIZE) { j ->
                samples[i + j].toDouble() / 32768.0
            }
            applyHammingWindow(frame)
            val spectrum = computePowerSpectrum(frame)
            val mfcc = computeMelFilterbank(spectrum)
            frames.add(mfcc)
            i += FRAME_SIZE / 2 // 50% overlap
        }
        return frames
    }

    private fun applyHammingWindow(frame: DoubleArray) {
        val n = frame.size
        for (i in frame.indices) {
            frame[i] *= 0.54 - 0.46 * cos(2 * PI * i / (n - 1))
        }
    }

    private fun computePowerSpectrum(frame: DoubleArray): DoubleArray {
        val n = frame.size
        val spectrum = DoubleArray(n / 2)
        for (k in spectrum.indices) {
            var real = 0.0
            var imag = 0.0
            for (t in frame.indices) {
                val angle = 2 * PI * k * t / n
                real += frame[t] * cos(angle)
                imag -= frame[t] * sin(angle)
            }
            spectrum[k] = real * real + imag * imag
        }
        return spectrum
    }

    private fun computeMelFilterbank(spectrum: DoubleArray): DoubleArray {
        val numFilters = NUM_MFCC + 2
        val mfcc = DoubleArray(NUM_MFCC)
        val melMin = hzToMel(300.0)
        val melMax = hzToMel(SAMPLE_RATE / 2.0)

        val melPoints = DoubleArray(numFilters) { i ->
            melToHz(melMin + i * (melMax - melMin) / (numFilters - 1))
        }

        for (m in 0 until NUM_MFCC) {
            var sum = 0.0
            for (k in spectrum.indices) {
                val freq = k.toDouble() * SAMPLE_RATE / spectrum.size / 2
                if (freq > melPoints[m] && freq <= melPoints[m + 1]) {
                    sum += spectrum[k] * (freq - melPoints[m]) / (melPoints[m + 1] - melPoints[m])
                } else if (freq > melPoints[m + 1] && freq <= melPoints[m + 2]) {
                    sum += spectrum[k] * (melPoints[m + 2] - freq) / (melPoints[m + 2] - melPoints[m + 1])
                }
            }
            mfcc[m] = if (sum > 0) ln(sum) else 0.0
        }

        // DCT
        val result = DoubleArray(NUM_MFCC)
        for (n in result.indices) {
            var dct = 0.0
            for (m in mfcc.indices) {
                dct += mfcc[m] * cos(PI * n * (2 * m + 1) / (2 * NUM_MFCC))
            }
            result[n] = dct
        }
        return result
    }

    private fun hzToMel(hz: Double) = 2595.0 * log10(1 + hz / 700.0)
    private fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1)

    // ─── Comparaison de similarité DTW ───────────────────────────────────────

    private fun computeSimilarity(ref: List<DoubleArray>, input: List<DoubleArray>): Float {
        if (ref.isEmpty() || input.isEmpty()) return 0f

        // Distance cosinus moyenne entre les frames
        val n = minOf(ref.size, input.size, 50)
        var totalSim = 0.0

        for (i in 0 until n) {
            val refFrame = ref[i % ref.size]
            val inpFrame = input[i % input.size]
            totalSim += cosineSimilarity(refFrame, inpFrame)
        }

        return (totalSim / n).toFloat().coerceIn(0f, 1f)
    }

    private fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices.take(b.size)) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0) (dot / denom + 1) / 2 else 0.5 // normalise [0,1]
    }
}
