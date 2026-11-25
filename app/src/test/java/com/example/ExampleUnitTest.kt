package com.example

import com.example.audio.AudioConstants
import com.example.audio.PartySynthEngine
import com.example.model.SynthGroovePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun synthEngine_generatesNonSilentStereoAudioForAllPresets() {
        val synth = PartySynthEngine()
        val pcm = ShortArray(AudioConstants.FRAMES_PER_BLOCK * AudioConstants.CHANNELS)

        SynthGroovePreset.entries.forEach { preset ->
            synth.reset()
            synth.renderBlock(preset, pcm)
            val maxPeak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("Preset $preset produced peak=$maxPeak", maxPeak > 1000)
        }
    }

    @Test
    fun discoveredBluetoothDevice_mapsSignalStrengthBarsCorrectly() {
        val base = com.example.model.DiscoveredBluetoothDevice(
            name = "Party Speaker",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -48,
            isAudioDevice = true,
            bondState = 10
        )
        assertEquals(com.example.model.SignalStrengthLevel.EXCELLENT, base.signalLevel)
        assertEquals(4, base.signalLevel.bars)

        assertEquals(com.example.model.SignalStrengthLevel.GOOD, base.copy(rssi = -62).signalLevel)
        assertEquals(com.example.model.SignalStrengthLevel.FAIR, base.copy(rssi = -76).signalLevel)
        assertEquals(com.example.model.SignalStrengthLevel.WEAK, base.copy(rssi = -89).signalLevel)
        assertEquals(com.example.model.SignalStrengthLevel.UNKNOWN, base.copy(rssi = null).signalLevel)
    }

    @Test
    fun masterVolume_proportionallyScalesMultipleSpeakersWhilePreservingRatios() {
        val speakerA = 0.80f
        val speakerB = 0.40f

        val outAHalf = com.example.audio.MultiSinkAudioEngine.computeProportionalLinearLevel(
            masterVolume = 0.50f,
            speakerVolume = speakerA,
            isMasterMuted = false,
            isSpeakerMuted = false
        )
        val outBHalf = com.example.audio.MultiSinkAudioEngine.computeProportionalLinearLevel(
            masterVolume = 0.50f,
            speakerVolume = speakerB,
            isMasterMuted = false,
            isSpeakerMuted = false
        )

        assertEquals(0.40f, outAHalf, 0.0001f)
        assertEquals(0.20f, outBHalf, 0.0001f)
        assertEquals(2.0f, outAHalf / outBHalf, 0.0001f)

        val outMuted = com.example.audio.MultiSinkAudioEngine.computeProportionalLinearLevel(
            masterVolume = 0.85f,
            speakerVolume = speakerA,
            isMasterMuted = true,
            isSpeakerMuted = false
        )
        assertEquals(0f, outMuted, 0.0001f)
    }

    @Test
    fun equalizerDsp_shapesBassBoostAndAcousticDifferentlyFromFlat() {
        val processor = com.example.audio.EqualizerDspProcessor()
        val frames = 2048
        val baseSine60Hz = ShortArray(frames * 2) { idx ->
            val frame = idx / 2
            val sample = kotlin.math.sin(2.0 * kotlin.math.PI * 60.0 * frame / AudioConstants.SAMPLE_RATE) * 8000.0
            sample.toInt().toShort()
        }

        val flatBuffer = baseSine60Hz.copyOf()
        processor.setPreset(com.example.model.EqualizerPreset.FLAT)
        processor.processStereoPcm(flatBuffer, frames)
        assertEquals(baseSine60Hz.toList(), flatBuffer.toList())

        val bassBuffer = baseSine60Hz.copyOf()
        processor.setPreset(com.example.model.EqualizerPreset.BASS_BOOST)
        processor.processStereoPcm(bassBuffer, frames)

        val vocalBuffer = baseSine60Hz.copyOf()
        processor.resetStates()
        processor.setPreset(com.example.model.EqualizerPreset.VOCAL)
        processor.processStereoPcm(vocalBuffer, frames)

        // Measure steady-state second half RMS at 60Hz
        val bassRms = bassBuffer.drop(frames).map { it.toDouble() * it.toDouble() }.average()
        val vocalRms = vocalBuffer.drop(frames).map { it.toDouble() * it.toDouble() }.average()
        assertTrue("Expected Bass Boost 60Hz energy ($bassRms) > Vocal ($vocalRms)", bassRms > vocalRms * 1.5)

        // Verify response curves exist and match expected shapes
        val flatCurve = com.example.audio.EqualizerDspProcessor.computeFrequencyResponseDb(
            com.example.model.EqualizerPreset.FLAT
        )
        assertTrue(flatCurve.all { abs(it) < 0.05f })

        val partyCurve = com.example.audio.EqualizerDspProcessor.computeFrequencyResponseDb(
            com.example.model.EqualizerPreset.PARTY
        )
        val acousticCurve = com.example.audio.EqualizerDspProcessor.computeFrequencyResponseDb(
            com.example.model.EqualizerPreset.ACOUSTIC
        )
        assertTrue(partyCurve.first() > 2.5f)
        assertTrue(acousticCurve[acousticCurve.size / 2] > 1.5f)
    }
}
