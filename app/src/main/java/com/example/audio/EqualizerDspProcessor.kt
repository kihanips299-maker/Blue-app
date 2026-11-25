package com.example.audio

import com.example.model.EqualizerPreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Real-time 5-band stereo cascaded Biquad Equalizer (60Hz Low Shelf, 230Hz/910Hz/3.6kHz Peaking,
 * 14kHz High Shelf) operating directly on the 44.1kHz stereo PCM stream before ring-buffer
 * distribution so every connected Bluetooth speaker receives identical EQ shaping.
 */
class EqualizerDspProcessor(
    private val sampleRate: Double = AudioConstants.SAMPLE_RATE.toDouble()
) {
    private data class BiquadCoeffs(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    )

    private class StereoBiquadState {
        var z1L = 0.0
        var z2L = 0.0
        var z1R = 0.0
        var z2R = 0.0

        fun reset() {
            z1L = 0.0
            z2L = 0.0
            z1R = 0.0
            z2R = 0.0
        }
    }

    private enum class FilterKind {
        LOW_SHELF,
        PEAKING,
        HIGH_SHELF
    }

    private val lock = Any()
    private val states = Array(5) { StereoBiquadState() }

    @Volatile
    private var activePreset: EqualizerPreset = EqualizerPreset.FLAT

    @Volatile
    private var activeCoeffs: List<BiquadCoeffs> = buildCoeffsForPreset(EqualizerPreset.FLAT, sampleRate)

    @Volatile
    private var headroomTrim: Double = 1.0

    fun currentPreset(): EqualizerPreset = activePreset

    fun setPreset(preset: EqualizerPreset) {
        if (activePreset == preset) return
        val nextCoeffs = buildCoeffsForPreset(preset, sampleRate)
        val maxBoostDb = preset.bandGainsDb.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        // Gentle headroom trim keeps bass boosts punchy without digital clipping
        val nextTrim = 1.0 / (1.0 + maxBoostDb * 0.035)
        synchronized(lock) {
            activePreset = preset
            activeCoeffs = nextCoeffs
            headroomTrim = nextTrim
            if (preset == EqualizerPreset.FLAT) {
                states.forEach { it.reset() }
            }
        }
    }

    fun resetStates() {
        synchronized(lock) {
            states.forEach { it.reset() }
        }
    }

    fun processStereoPcm(pcm: ShortArray, frames: Int = pcm.size / 2) {
        val preset = activePreset
        if (preset == EqualizerPreset.FLAT) return

        synchronized(lock) {
            val coeffs = activeCoeffs
            val trim = headroomTrim
            for (f in 0 until frames) {
                val idxL = f * 2
                val idxR = idxL + 1
                var xL = (pcm[idxL] / 32768.0) * trim
                var xR = (pcm[idxR] / 32768.0) * trim

                for (b in coeffs.indices) {
                    val c = coeffs[b]
                    val st = states[b]

                    val yL = c.b0 * xL + st.z1L
                    st.z1L = c.b1 * xL - c.a1 * yL + st.z2L
                    st.z2L = c.b2 * xL - c.a2 * yL
                    xL = yL

                    val yR = c.b0 * xR + st.z1R
                    st.z1R = c.b1 * xR - c.a1 * yR + st.z2R
                    st.z2R = c.b2 * xR - c.a2 * yR
                    xR = yR
                }

                // Soft-knee saturation prevents harsh clipping at high boost levels
                val outL = if (abs(xL) > 0.88) tanh(xL) else xL
                val outR = if (abs(xR) > 0.88) tanh(xR) else xR

                pcm[idxL] = (outL * 32767.0).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                pcm[idxR] = (outR * 32767.0).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
    }

    companion object {
        private val bandKinds = listOf(
            FilterKind.LOW_SHELF,
            FilterKind.PEAKING,
            FilterKind.PEAKING,
            FilterKind.PEAKING,
            FilterKind.HIGH_SHELF
        )

        private fun buildCoeffsForPreset(
            preset: EqualizerPreset,
            sampleRate: Double
        ): List<BiquadCoeffs> {
            val freqs = EqualizerPreset.BAND_FREQUENCIES_HZ
            return preset.bandGainsDb.mapIndexed { idx, gainDb ->
                createBiquad(
                    kind = bandKinds[idx],
                    freqHz = freqs[idx],
                    gainDb = gainDb.toDouble(),
                    sampleRate = sampleRate
                )
            }
        }

        private fun createBiquad(
            kind: FilterKind,
            freqHz: Double,
            gainDb: Double,
            sampleRate: Double
        ): BiquadCoeffs {
            if (abs(gainDb) < 0.01) {
                return BiquadCoeffs(1.0, 0.0, 0.0, 0.0, 0.0)
            }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate
            val cosW = cos(w0)
            val sinW = sin(w0)
            val q = if (kind == FilterKind.PEAKING) 1.0 else 0.70710678
            val alpha = sinW / (2.0 * q)

            val b0: Double
            val b1: Double
            val b2: Double
            val a0: Double
            val a1: Double
            val a2: Double

            when (kind) {
                FilterKind.LOW_SHELF -> {
                    val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                    b0 = a * ((a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha)
                    b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)
                    b2 = a * ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha)
                    a0 = (a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha
                    a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW)
                    a2 = (a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha
                }
                FilterKind.PEAKING -> {
                    b0 = 1.0 + alpha * a
                    b1 = -2.0 * cosW
                    b2 = 1.0 - alpha * a
                    a0 = 1.0 + alpha / a
                    a1 = -2.0 * cosW
                    a2 = 1.0 - alpha / a
                }
                FilterKind.HIGH_SHELF -> {
                    val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                    b0 = a * ((a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha)
                    b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW)
                    b2 = a * ((a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha)
                    a0 = (a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha
                    a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW)
                    a2 = (a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha
                }
            }

            return BiquadCoeffs(
                b0 = b0 / a0,
                b1 = b1 / a0,
                b2 = b2 / a0,
                a1 = a1 / a0,
                a2 = a2 / a0
            )
        }

        /**
         * Evaluates the exact combined magnitude response (in dB) across log-spaced frequencies
         * from 30 Hz to 18 kHz for crisp UI curve rendering.
         */
        fun computeFrequencyResponseDb(
            preset: EqualizerPreset,
            pointCount: Int = 40,
            sampleRate: Double = AudioConstants.SAMPLE_RATE.toDouble()
        ): List<Float> {
            val coeffs = buildCoeffsForPreset(preset, sampleRate)
            val minLog = log10(30.0)
            val maxLog = log10(18000.0)
            return List(pointCount.coerceAtLeast(2)) { i ->
                val frac = i.toDouble() / (pointCount - 1).coerceAtLeast(1)
                val freq = 10.0.pow(minLog + frac * (maxLog - minLog))
                val w = 2.0 * PI * freq / sampleRate
                val cosW = cos(w)
                val cos2W = cos(2.0 * w)
                val sinW = sin(w)
                val sin2W = sin(2.0 * w)

                var totalMagSq = 1.0
                for (c in coeffs) {
                    val numRe = c.b0 + c.b1 * cosW + c.b2 * cos2W
                    val numIm = -(c.b1 * sinW + c.b2 * sin2W)
                    val denRe = 1.0 + c.a1 * cosW + c.a2 * cos2W
                    val denIm = -(c.a1 * sinW + c.a2 * sin2W)
                    val magSq = (numRe * numRe + numIm * numIm) /
                        (denRe * denRe + denIm * denIm).coerceAtLeast(1e-12)
                    totalMagSq *= magSq
                }
                val db = 10.0 * log10(totalMagSq.coerceAtLeast(1e-9))
                db.toFloat().coerceIn(-12f, 12f)
            }
        }
    }
}
