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
    private val loadedSounds = mutableMapOf<AudioCue, MutableList<Int>>()  // Changed to list for variants
    private var currentPack: AudioPack = AudioPack.MINIMAL
    private val random = kotlin.random.Random.Default
    
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
                    // Use system tones for minimal
                }
                AudioPack.MECHANICAL -> {
                    // Load mechanical sound files with variants
                    loadedSounds[AudioCue.TAP] = mutableListOf(
                        pool.load(context, R.raw.mechanical_tap, 1),
                        pool.load(context, R.raw.mechanical_tap2, 1)
                    )
                    loadedSounds[AudioCue.LONG_PRESS] = mutableListOf(
                        pool.load(context, R.raw.mechanical_long, 1)
                    )
                    loadedSounds[AudioCue.MODE_SWITCH_MOUSE] = mutableListOf(
                        pool.load(context, R.raw.mechanical_toggle, 1)
                    )
                    loadedSounds[AudioCue.MODE_SWITCH_GAMEPAD] = mutableListOf(
                        pool.load(context, R.raw.mechanical_toggle, 1)
                    )
                    loadedSounds[AudioCue.SCROLL] = mutableListOf(
                        pool.load(context, R.raw.mechanical_tap2, 1)
                    )
                }
                AudioPack.RETRO -> {
                    // Load retro sound files
                    loadedSounds[AudioCue.TAP] = mutableListOf(
                        pool.load(context, R.raw.retro_tap, 1)
                    )
                    loadedSounds[AudioCue.LONG_PRESS] = mutableListOf(
                        pool.load(context, R.raw.retro_long, 1)
                    )
                    loadedSounds[AudioCue.MODE_SWITCH_MOUSE] = mutableListOf(
                        pool.load(context, R.raw.retro_toggle, 1)
                    )
                    loadedSounds[AudioCue.MODE_SWITCH_GAMEPAD] = mutableListOf(
                        pool.load(context, R.raw.retro_toggle, 1)
                    )
                    loadedSounds[AudioCue.SCROLL] = mutableListOf(
                        pool.load(context, R.raw.retro_tap, 1)
                    )
                }
                AudioPack.SCIFI -> {
                    // Load sci-fi sound files with variants
                    loadedSounds[AudioCue.TAP] = mutableListOf(
                        pool.load(context, R.raw.scifi_tap, 1),
                        pool.load(context, R.raw.scifi_tap2, 1),
                        pool.load(context, R.raw.scifi_tap3, 1)
                    )
                    loadedSounds[AudioCue.LONG_PRESS] = mutableListOf(
                        pool.load(context, R.raw.scifi_long, 1)
                    )
                    loadedSounds[AudioCue.MODE_SWITCH_MOUSE] = mutableListOf(
                        pool.load(context, R.raw.scifi_toggle, 1)
                    )
                    loadedSounds[AudioCue.MODE_SWITCH_GAMEPAD] = mutableListOf(
                        pool.load(context, R.raw.scifi_toggle, 1)
                    )
                    loadedSounds[AudioCue.SCROLL] = mutableListOf(
                        pool.load(context, R.raw.scifi_tap2, 1)
                    )
                }
                AudioPack.CUSTOM -> {
                    // User-selected sounds (would load from user-selected paths)
                    // TODO: Implement file picker integration
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
        
        // Try to play from loaded sounds first
        loadedSounds[cue]?.let { variants ->
            if (variants.isNotEmpty()) {
                // Pick a random variant
                val soundId = variants.random(random)
                
                // Random pitch variation: 0.9 to 1.1 (±10%)
                val pitch = 0.9f + random.nextFloat() * 0.2f
                
                soundPool?.play(soundId, volume, volume, 1, 0, pitch)
                return
            }
        }
        
        // Fallback to system tones if no sound file loaded (Minimal pack or loading failed)
        playSystemTone(cue)
    }
    
    private fun playSystemTone(cue: AudioCue) {
        // This is a temporary fallback - in production we'd use actual sound files
        val toneType = when (currentPack) {
            AudioPack.MECHANICAL -> {
                // Use sharper, click-like tones for mechanical pack
                when (cue) {
                    AudioCue.MODE_SWITCH_MOUSE -> android.media.ToneGenerator.TONE_PROP_PROMPT
                    AudioCue.MODE_SWITCH_GAMEPAD -> android.media.ToneGenerator.TONE_PROP_BEEP
                    AudioCue.TAP -> android.media.ToneGenerator.TONE_DTMF_1  // Sharp click
                    AudioCue.LONG_PRESS -> android.media.ToneGenerator.TONE_DTMF_2
                    AudioCue.SCROLL -> android.media.ToneGenerator.TONE_DTMF_0
                }
            }
            AudioPack.RETRO -> {
                // 8-bit style tones
                when (cue) {
                    AudioCue.MODE_SWITCH_MOUSE -> android.media.ToneGenerator.TONE_CDMA_PIP
                    AudioCue.MODE_SWITCH_GAMEPAD -> android.media.ToneGenerator.TONE_CDMA_ABBR_ALERT
                    AudioCue.TAP -> android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                    AudioCue.LONG_PRESS -> android.media.ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE
                    AudioCue.SCROLL -> android.media.ToneGenerator.TONE_CDMA_PIP
                }
            }
            AudioPack.SCIFI -> {
                // Futuristic tones
                when (cue) {
                    AudioCue.MODE_SWITCH_MOUSE -> android.media.ToneGenerator.TONE_CDMA_HIGH_L
                    AudioCue.MODE_SWITCH_GAMEPAD -> android.media.ToneGenerator.TONE_CDMA_LOW_L
                    AudioCue.TAP -> android.media.ToneGenerator.TONE_CDMA_MED_L
                    AudioCue.LONG_PRESS -> android.media.ToneGenerator.TONE_CDMA_HIGH_SS
                    AudioCue.SCROLL -> android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
                }
            }
            else -> {
                // Minimal/default
                when (cue) {
                    AudioCue.MODE_SWITCH_MOUSE -> android.media.ToneGenerator.TONE_PROP_BEEP2
                    AudioCue.MODE_SWITCH_GAMEPAD -> android.media.ToneGenerator.TONE_PROP_BEEP
                    AudioCue.TAP -> android.media.ToneGenerator.TONE_PROP_ACK
                    AudioCue.LONG_PRESS -> android.media.ToneGenerator.TONE_PROP_ACK
                    AudioCue.SCROLL -> android.media.ToneGenerator.TONE_CDMA_ABBR_ALERT
                }
            }
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
    MECHANICAL("Mechanical (Clicks)"),
    RETRO("Retro (8-bit)"),
    SCIFI("Sci-Fi"),
    SILENT("Silent (No sounds)"),
    CUSTOM("Custom (Your files)")
}

enum class AudioCue {
    MODE_SWITCH_MOUSE,
    MODE_SWITCH_GAMEPAD,
    TAP,
    LONG_PRESS,
    SCROLL
}
