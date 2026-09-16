package com.droidforge.gamepadkeyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KeyboardViewTest {

    private class RecordingListener : KeyboardView.Listener {
        val keys = mutableListOf<String>()
        var backspaces = 0
        var enters = 0
        var spaces = 0
        var hides = 0
        val suggestions = mutableListOf<String>()
        val editorKeys = mutableListOf<Int>()
        var optionsOpened = 0

        override fun onKey(text: String) { keys.add(text) }
        override fun onSpace() { spaces++; keys.add(" ") }
        override fun onBackspace() { backspaces++ }
        override fun onEnter() { enters++ }
        override fun onHide() { hides++ }
        override fun onSuggestionPick(word: String) { suggestions.add(word) }
        override fun onEditorKey(keyCode: Int) { editorKeys.add(keyCode) }
        override fun onSelectAll() = Unit
        override fun onCopy() = Unit
        override fun onCut() = Unit
        override fun onPaste() = Unit
        override fun onMicInput() = Unit
        override fun onPinyinChanged(buffer: String) = Unit
        override fun onLanguageToggle() = Unit
        override fun onOpenOptions() { optionsOpened++ }
    }

    private fun viewWith(listener: RecordingListener): KeyboardView =
        KeyboardView(RuntimeEnvironment.getApplication()).apply {
            this.listener = listener
            measure(1080, 800)
            layout(0, 0, 1080, 800)
        }

    /** Deck letters grid rows: 0=num+⌫, 1=Tab+qwertz, 2=Caps+home, 3=Shift+zxcv, 4=bar. */
    private fun KeyboardView.toLetter(letter: Char) {
        page = KeyboardView.Page.LETTERS
        val grid = letterRows
        for ((r, row) in grid.withIndex()) {
            val c = row.indexOfFirst { it.label == letter.toString() }
            if (c >= 0) {
                // Navigate there via moves from the initial selection (row 1, col 0)
                moveSelection(r - 1, c)
                return
            }
        }
        error("letter $letter not on grid")
    }

    @Test
    fun `A button types the selected key`() {
        val l = RecordingListener()
        val v = viewWith(l)
        v.toLetter('a')
        assertTrue(v.onGamepadKeyDown(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals("a", l.keys.first())
    }

    @Test
    fun `B button hides and X backspaces and Start enters`() {
        val l = RecordingListener()
        val v = viewWith(l)
        assertTrue(v.onGamepadKeyDown(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(1, l.hides)
        assertTrue(v.onGamepadKeyDown(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(1, l.backspaces)
        assertTrue(v.onGamepadKeyDown(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(1, l.enters)
    }

    @Test
    fun `Y toggles shift and next letter comes out uppercase one-shot`() {
        val l = RecordingListener()
        val v = viewWith(l)
        assertTrue(v.onGamepadKeyDown(KeyEvent.KEYCODE_BUTTON_Y))
        v.toLetter('s')
        v.pressSelectedKey()
        assertEquals("S", l.keys.first())
        // one-shot consumed — next letter is lowercase
        v.moveSelection(0, 1)
        v.pressSelectedKey()
        assertEquals("d", l.keys.last())
    }

    @Test
    fun `d-pad navigation clamps at row start`() {
        val v = viewWith(RecordingListener())
        v.moveSelection(0, -5) // row 1 col 0 is Tab on the Deck grid
        assertEquals(KeyboardView.KEY_TAB, v.selectedKey())
        v.moveSelection(0, 1)
        assertEquals("q", v.selectedKey())
    }

    @Test
    fun `LB and RB cycle pages and selection survives`() {
        val v = viewWith(RecordingListener())
        assertTrue(v.onGamepadKeyDown(KeyEvent.KEYCODE_BUTTON_R1))
        assertTrue(v.onGamepadKeyDown(KeyEvent.KEYCODE_BUTTON_L1))
        assertTrue(v.selectedKey().isNotEmpty())
    }

    @Test
    fun `S key commits top suggestion through listener`() {
        val l = RecordingListener()
        val v = viewWith(l)
        v.setSuggestions(listOf("hello", "help", "hell"))
        v.pressTopSuggestion()
        assertEquals("hello", l.suggestions.first())
    }

    @Test
    fun `d-pad up reaches the suggestion strip and A commits`() {
        val l = RecordingListener()
        val v = viewWith(l)
        v.setSuggestions(listOf("alpha", "also"))
        v.moveSelection(-1, 0) // up from row 1 → row 0
        v.moveSelection(-1, 0) // up from row 0 → strip
        v.moveSelection(0, 1)  // highlight second candidate
        assertTrue(v.onGamepadKeyDown(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals("also", l.suggestions.first())
    }

    @Test
    fun `Fn page routes arrows and F-keys to editor listener`() {
        val l = RecordingListener()
        val v = viewWith(l)
        v.page = KeyboardView.Page.FN
        // fnRows: row0=F-keys, row1=clipboard+arrows+⌦, row2=bar. Initial row 1 = arrows row.
        var col = 0
        while (v.selectedKey() != "←" && col < 24) { v.moveSelection(0, 1); col++ }
        assertEquals("←", v.selectedKey())
        v.pressSelectedKey()
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, l.editorKeys.first())
        // up to F-row; esc sits at col 0, F1 is one step right of it
        v.moveSelection(-1, 0)
        repeat(24) { v.moveSelection(0, -1) }
        assertEquals("\u241B", v.selectedKey())
        v.moveSelection(0, 1)
        assertEquals("F1", v.selectedKey())
        v.pressSelectedKey()
        assertEquals(KeyEvent.KEYCODE_F1, l.editorKeys.last())
    }

    @Test
    fun `options key on the bar row notifies listener`() {
        val l = RecordingListener()
        val v = viewWith(l)
        // bottom bar is the last row of the LETTERS grid; ⚙ is the last key on it
        v.moveSelection(v.letterRows.lastIndex - 1, 0) // from row 1 down to the bar row
        var col = 0
        while (v.selectedKey() != KeyboardView.KEY_OPTIONS && col < 24) { v.moveSelection(0, 1); col++ }
        assertEquals(KeyboardView.KEY_OPTIONS, v.selectedKey())
        v.pressSelectedKey()
        assertEquals(1, l.optionsOpened)
    }

    @Test
    fun `emoji page exists and space bar still types`() {
        val l = RecordingListener()
        val v = viewWith(l)
        v.page = KeyboardView.Page.EMOJI
        assertTrue(v.selectedKey().isNotEmpty())
        v.pressKey(KeyboardView.KEY_SPACE)
        assertEquals(" ", l.keys.first())
    }

    @Test
    fun `numeric fields auto-open the numeric pad`() {
        assertEquals(
            KeyboardView.Page.NUMBERS,
            KeyboardView.pageForInputType(
                android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_NORMAL),
        )
        assertEquals(
            KeyboardView.Page.NUMBERS,
            KeyboardView.pageForInputType(android.text.InputType.TYPE_CLASS_PHONE),
        )
        assertEquals(
            KeyboardView.Page.NUMBERS,
            KeyboardView.pageForInputType(android.text.InputType.TYPE_CLASS_DATETIME),
        )
        assertEquals(
            KeyboardView.Page.LETTERS,
            KeyboardView.pageForInputType(android.text.InputType.TYPE_CLASS_TEXT),
        )
    }

    @Test
    fun `numeric pad types digits and math operators`() {
        val l = RecordingListener()
        val v = viewWith(l)
        v.page = KeyboardView.Page.NUMBERS
        v.moveSelection(-1, 0) // up to the 789 row (initial selection is row 1 = 456)
        v.moveSelection(0, 1) // 7 -> 8
        assertEquals("8", v.selectedKey())
        v.pressSelectedKey()
        assertEquals("8", l.keys.first())
        // ÷ lives on row 2 (1 2 3 × ÷)
        v.moveSelection(2, 0)
        var col = 0
        while (v.selectedKey() != "÷" && col < 12) { v.moveSelection(0, 1); col++ }
        assertEquals("÷", v.selectedKey())
        v.pressSelectedKey()
        assertEquals("÷", l.keys.last())
    }

    @Test
    fun `all skins apply and view measures without crashing`() {
        Skin.entries.forEach { skin ->
            val v = KeyboardView(RuntimeEnvironment.getApplication())
            v.skin = skin
            v.measure(1080, 800)
            v.layout(0, 0, 1080, 800)
        }
    }
}
