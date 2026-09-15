package com.droidforge.gamepadkeyboard

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Keyboard options: skin, sound pack, haptics, suggestions, height, repeat timing.
 * Plain-view UI (no Compose dependency in the :keyboard module). Changes persist
 * immediately and apply the next time the keyboard view starts.
 */
class OptionsActivity : Activity() {

    private lateinit var prefs: KeyboardPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = KeyboardPrefs(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val scroll = android.widget.ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        title("Gamepad Keyboard", root)

        // ---- Skin ----
        label("Skin", root)
        val skinSpinner = spinner(root, Skin.entries.map { it.label }) {
            prefs.skinName = Skin.entries[it].name
            toast("${Skin.entries[it].label} — reopens with the keyboard")
        }
        skinSpinner.setSelection(Skin.entries.indexOfFirst { it.name == prefs.skinName }.coerceAtLeast(0))

        // ---- Sound pack ----
        label("Key sounds", root)
        val soundSpinner = spinner(root, SoundPack.entries.map { it.label }) {
            prefs.soundPackName = SoundPack.entries[it].name
            toast(SoundPack.entries[it].label)
        }
        soundSpinner.setSelection(SoundPack.entries.indexOfFirst { it.name == prefs.soundPackName }.coerceAtLeast(0))

        // ---- Haptics ----
        label("Haptic feedback", root)
        switch(root, prefs.hapticsEnabled) { prefs.hapticsEnabled = it }

        // ---- Suggestions ----
        label("Autocomplete suggestions", root)
        switch(root, prefs.suggestionsEnabled) { prefs.suggestionsEnabled = it }

        // ---- Height ----
        label("Keyboard height — ${prefs.heightDp} dp", root, tag = "height_label")
        seekbar(root, 180, 360, prefs.heightDp) { v ->
            prefs.heightDp = v
            root.findViewWithTag<TextView>("height_label")?.text = "Keyboard height — $v dp"
        }

        // ---- Repeat ----
        label("Hold-repeat delay — ${prefs.repeatDelayMs} ms", root, tag = "repeat_label")
        seekbar(root, 150, 800, prefs.repeatDelayMs) { v ->
            prefs.repeatDelayMs = v
            root.findViewWithTag<TextView>("repeat_label")?.text = "Hold-repeat delay — $v ms"
        }
        label("Hold-repeat rate — ${prefs.repeatRateMs} ms", root, tag = "rate_label")
        seekbar(root, 25, 150, prefs.repeatRateMs) { v ->
            prefs.repeatRateMs = v
            root.findViewWithTag<TextView>("rate_label")?.text = "Hold-repeat rate — $v ms"
        }

        // ---- About ----
        label("Gamepad Keyboard ${KeyboardView.DISPLAY_VERSION}", root).apply {
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, 0)
        }

        setContentView(scroll)
    }

    // ---- tiny view builders ----

    private fun density() = resources.displayMetrics.density

    private fun title(text: String, root: LinearLayout) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, (8 * density()).toInt())
        }
        root.addView(tv)
    }

    private fun label(text: String, root: LinearLayout, tag: String? = null): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, (14 * density()).toInt(), 0, (4 * density()).toInt())
            this.tag = tag
        }
        root.addView(tv)
        return tv
    }

    private fun spinner(
        root: LinearLayout,
        items: List<String>,
        onPick: (Int) -> Unit,
    ): Spinner {
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = onPick(pos)
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) = Unit
        }
        root.addView(sp)
        return sp
    }

    private fun switch(root: LinearLayout, checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val sw = Switch(this)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        root.addView(sw)
        return sw
    }

    private fun seekbar(
        root: LinearLayout,
        min: Int,
        max: Int,
        value: Int,
        onChange: (Int) -> Unit,
    ): SeekBar {
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = (value - min).coerceIn(0, sb.max)
        sb.tag = "seek_${root.childCount}"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                onChange(progress + min)
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
        root.addView(sb)
        return sb
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
