package com.droidforge.gamepadmouse.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.droidforge.gamepadmouse.R

/**
 * Manages audio feedback for user interactions.
 * Supports bundled sound packs and custom user files.
 */
class AudioManager(private val context: Context) {
    private val TAG = "AudioManager"
    
    private var soundPool: SoundPool? = null
    private val loadedSounds = mutableMapOf<AudioCue, Int>()
    private var currentPack: AudioPack = AudioPack.MINIMAL
    
    init {
        initSoundPool()
    }
    
    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
    }
    
    fun loadPack(pack: AudioPack) {
        currentPack = pack
        loadedSounds.clear()
        
        soundPool?.let { pool ->
            when (pack) {
                AudioPack.MINIMAL -> {
                    // Simple single-frequency tones (we'll use ToneGenerator as fallback for now)
                    // In a production app, you'd have actual sound files here
                }
                AudioPack.RETRO -> {
                    // 8-bit style beeps (would load from res/raw if we had files)
                }
                AudioPack.SCIFI -> {
                    // Futuristic UI sounds (would load from res/raw if we had files)
                }
                AudioPack.SILENT -> {
                    // No sounds
                }
            }
        }
        
        Log.i(TAG, "Loaded audio pack: $pack")
    }
    
    fun play(cue: AudioCue, volume: Float = 1.0f) {
        if (currentPack == AudioPack.SILENT) return
        
        // For now, fall back to ToneGenerator since we don't have actual audio files yet
        // In production, this would play from SoundPool:
        // loadedSounds[cue]?.let { soundId ->
        //     soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
        // }
        
        // Fallback to system tones for now
        playSystemTone(cue)
    }
    
    private fun playSystemTone(cue: AudioCue) {
        // This is a temporary fallback - in production we'd use actual sound files
        val toneType = when (cue) {
            AudioCue.MODE_SWITCH_MOUSE -> android.media.ToneGenerator.TONE_PROP_BEEP2
            AudioCue.MODE_SWITCH_GAMEPAD -> android.media.ToneGenerator.TONE_PROP_BEEP
            AudioCue.TAP -> android.media.ToneGenerator.TONE_PROP_ACK
            AudioCue.LONG_PRESS -> android.media.ToneGenerator.TONE_PROP_ACK
            AudioCue.SCROLL -> android.media.ToneGenerator.TONE_CDMA_ABBR_ALERT
        }
        
        try {
            val tone = android.media.ToneGenerator(
                android.media.AudioManager.STREAM_NOTIFICATION,
                android.media.ToneGenerator.MAX_VOLUME / 2
            )
            tone.startTone(toneType, 80)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play tone: ${e.message}")
        }
    }
    
    fun release() {
        soundPool?.release()
        soundPool = null
        loadedSounds.clear()
    }
}

enum class AudioPack(val label: String) {
    MINIMAL("Minimal (System tones)"),
    RETRO("Retro (8-bit)"),
    SCIFI("Sci-Fi"),
    SILENT("Silent (No sounds)")
}

enum class AudioCue {
    MODE_SWITCH_MOUSE,
    MODE_SWITCH_GAMEPAD,
    TAP,
    LONG_PRESS,
    SCROLL
}
