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

        // ---- Layout ----
        label("Language", root)
        val langSpinner = spinner(root, LanguagePack.entries.map { it.label }) {
            prefs.languageCode = LanguagePack.entries[it].code
            toast("${LanguagePack.entries[it].label} — reopens with the keyboard")
        }
        langSpinner.setSelection(
            LanguagePack.entries.indexOfFirst { it.code == prefs.languageCode }.coerceAtLeast(0))

        label("Show ◀ ▶ cursor keys", root)
        switch(root, prefs.arrowsVisible) { prefs.arrowsVisible = it }

        label("Escalating backspace (hold to delete words)", root)
        switch(root, prefs.escalatingBackspace) { prefs.escalatingBackspace = it }

        // ---- Voice input ----
        label("Microphone (voice dictation)", root)
        root.addView(android.widget.Button(this).apply {
            text = "Grant microphone permission"
            setOnClickListener {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 7001)
            }
        })

        // ---- Learned words ----
        label("Learned words", root)
        val learner = WordLearner.instance ?: WordLearner(this)
        val listTag = "learned_list"
        val addInput = android.widget.EditText(this).apply {
            hint = "Add a word manually…"
            setSingleLine(true)
        }
        root.addView(addInput)
        root.addView(android.widget.Button(this).apply {
            text = "Add word"
            setOnClickListener {
                val w = addInput.text.toString().trim()
                if (w.length >= 2) {
                    learner.record(w, boost = 5)  // manual add = strong
                    addInput.setText("")
                    rebuildLearnedList(listTag, learner)
                    toast("Learned \"$w\"")
                }
            }
        })
        val listTag2 = listTag
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = listTag2
        })
        rebuildLearnedList(listTag2, learner)
        root.addView(android.widget.Button(this).apply {
            text = "Forget all learned words"
            setOnClickListener {
                learner.all().keys.forEach { learner.forget(it) }
                rebuildLearnedList(listTag, learner)
                toast("Dictionary reset")
            }
        })

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

    /** Refresh the learned-words list: "word ×count [Forget]" rows, top 100. */
    private fun rebuildLearnedList(listTag: String, learner: WordLearner) {
        val list = window.decorView.findViewWithTag<LinearLayout>(listTag) ?: return
        list.removeAllViews()
        val entries = learner.top(100)
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "(no learned words yet — type + space to teach me)"
                textSize = 13f
            })
            return
        }
        for ((word, freq) in entries) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply {
                text = "$word  ×$freq"
                textSize = 15f
                setPadding(0, 0, (12 * density()).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                )
            })
            row.addView(android.widget.Button(this).apply {
                text = "Forget"
                setOnClickListener {
                    learner.forget(word)
                    rebuildLearnedList(listTag, learner)
                }
            })
            list.addView(row)
        }
    }
}
