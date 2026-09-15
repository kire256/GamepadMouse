package com.droidforge.gamepadkeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/** Key-press sound packs rendered from res/raw WAVs. */
enum class SoundPack(val label: String, val tapRes: Int, val enterRes: Int) {
    NONE("Silent", 0, 0),
    CLASSIC("Classic", R.raw.snd_tap_classic, R.raw.snd_enter_classic),
    SOFT("Soft", R.raw.snd_tap_soft, R.raw.snd_enter_soft),
}

/**
 * Tiny SoundPool wrapper: preloaded tap/enter samples, [tap] and [enter] are
 * no-ops when the pack is NONE or loading failed. Swap packs at runtime.
 */
class KeyboardAudio(private val context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var pack: SoundPack = SoundPack.NONE
    private var tapId = 0
    private var enterId = 0
    private var loaded = false

    init {
        pool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
    }

    fun setPack(new: SoundPack) {
        if (new == pack) return
        if (tapId != 0) pool.unload(tapId)
        if (enterId != 0) pool.unload(enterId)
        tapId = 0; enterId = 0; loaded = false
        pack = new
        if (new.tapRes != 0) {
            tapId = pool.load(context, new.tapRes, 1)
            enterId = pool.load(context, new.enterRes, 1)
        }
        Log.d("GPKeyboard", "sound pack ${new.name}")
    }

    fun tap() = play(tapId)
    fun enter() = play(enterId)

    private fun play(id: Int) {
        if (pack == SoundPack.NONE || id == 0 || !loaded) return
        runCatching { pool.play(id, 0.6f, 0.6f, 1, 0, 1f) }
    }

    fun release() = runCatching { pool.release() }
}
