package com.droidforge.gamepadmouse.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.droidforge.gamepadmouse.audio.AudioCue
import com.droidforge.gamepadmouse.audio.AudioPack
import com.droidforge.gamepadmouse.input.ChordDetector
import com.droidforge.gamepadmouse.input.DefaultBindings
import com.droidforge.gamepadmouse.input.MouseAction
import com.droidforge.gamepadmouse.input.ServiceMode
import com.droidforge.gamepadmouse.input.StickProcessor
import com.droidforge.gamepadmouse.settings.Settings
import com.droidforge.gamepadmouse.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Turns a gamepad into a system-wide pointer.
 *
 * Architecture:
 *  - Buttons arrive via [onKeyEvent] (accessibility key filtering — works regardless of focus).
 *  - Analog sticks arrive via a focusable, non-touchable accessibility overlay ([CursorOverlayView]),
 *    because AccessibilityService cannot filter MotionEvents.
 *  - Clicks / scrolls are injected with [dispatchGesture]; they pass through the overlay.
 *  - Back / Home / Recents use [performGlobalAction].
 */
class GamepadMouseService : AccessibilityService() {

    companion object {
        private const val TAG = "GamepadMouse"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _mode = MutableStateFlow(ServiceMode.GAMEPAD)
        val mode: StateFlow<ServiceMode> = _mode.asStateFlow()

        private val _controllerConnected = MutableStateFlow(false)
        val controllerConnected: StateFlow<Boolean> = _controllerConnected.asStateFlow()

        // Chord recording state
        private val _recordingChord = MutableStateFlow(false)
        val recordingChord: StateFlow<Boolean> = _recordingChord.asStateFlow()
        
        private val _recordedChord = MutableStateFlow<Set<Int>?>(null)
        val recordedChord: StateFlow<Set<Int>?> = _recordedChord.asStateFlow()

        @Volatile
        var instance: GamepadMouseService? = null
            private set

        private const val SCROLL_TICK_MS = 140L
        private const val TAP_MS = 60L
        private const val LONG_PRESS_MS = 650L
        
        fun startChordRecording() {
            _recordingChord.value = true
            _recordedChord.value = null
        }
        
        fun stopChordRecording() {
            _recordingChord.value = false
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: SettingsRepository
    @Volatile private var settings = Settings()

    private lateinit var windowManager: WindowManager
    private var overlay: CursorOverlayView? = null
    private var joystickCapture: JoystickCaptureView? = null
    private var displayW = 1080f
    private var displayH = 1920f

    private val chord = ChordDetector(DefaultBindings.toggleChord)
    private val heldModifiers = HashSet<MouseAction>()
    
    // Chord hold timer
    private var chordHoldJob: kotlinx.coroutines.Job? = null

    // Stick state (already deadzone-processed in onJoystick)
    private var moveX = 0f
    private var moveY = 0f
    private var scrollX = 0f
    private var scrollY = 0f
    private var prevScrollX = 0f   // previous frame right stick — for circular scroll
    private var prevScrollY = 0f
    private var lastFrameNs = 0L
    private var frameScheduled = false
    private var lastScrollAt = 0L
    private var tapGestureInFlight = false  // only blocks taps, not scrolls

    private lateinit var audioManager: com.droidforge.gamepadmouse.audio.AudioManager

    // ---------------------------------------------------------------- lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _running.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Cache real display size for use before overlay gets laid out
        @Suppress("DEPRECATION")
        val dm = resources.displayMetrics
        displayW = dm.widthPixels.toFloat()
        displayH = dm.heightPixels.toFloat()
        repo = SettingsRepository(this)
        audioManager = com.droidforge.gamepadmouse.audio.AudioManager(this)

        scope.launch {
            var first = true
            repo.settings.collect { s ->
                settings = s
                if (chord.chord != s.toggleChord) chord.chord = s.toggleChord
                chord.holdDurationMs = s.chordHoldDurationMs
                // Load audio pack when settings change
                try {
                    audioManager.loadPack(AudioPack.valueOf(s.audioPack))
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid audio pack: ${s.audioPack}, using MINIMAL")
                    audioManager.loadPack(AudioPack.MINIMAL)
                }
                // Update cursor style when settings change
                try {
                    overlay?.cursorStyle = CursorStyle.valueOf(s.cursorStyle)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid cursor style: ${s.cursorStyle}, using ARROW")
                    overlay?.cursorStyle = CursorStyle.ARROW
                }
                if (first) {
                    first = false
                    if (s.startInMouseMode) setMode(ServiceMode.MOUSE)
                }
            }
        }
        Log.i(TAG, "service connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        removeOverlay()
        scope.cancel()
        audioManager.release()
        instance = null
        _running.value = false
        _mode.value = ServiceMode.GAMEPAD
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Reclaim joystick-capture focus on window state changes.
        if (_mode.value == ServiceMode.MOUSE) {
            joystickCapture?.post { joystickCapture?.reclaimFocus() }
        }
    }
    override fun onInterrupt() { /* not needed */ }

    // ---------------------------------------------------------------- mode

    fun toggleMode() = setMode(if (_mode.value == ServiceMode.MOUSE) ServiceMode.GAMEPAD else ServiceMode.MOUSE)

    fun setMode(newMode: ServiceMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        heldModifiers.clear()
        moveX = 0f; moveY = 0f; scrollX = 0f; scrollY = 0f
        when (newMode) {
            ServiceMode.MOUSE -> {
                addOverlay()
                scheduleFrame()  // start frame loop immediately so focus is claimed before any stick input
                audioManager.play(AudioCue.MODE_SWITCH_MOUSE)
            }
            ServiceMode.GAMEPAD -> { removeOverlay(); audioManager.play(AudioCue.MODE_SWITCH_GAMEPAD) }
        }
        Log.i(TAG, "mode -> $newMode")
    }
    
    // Public method for audio preview
    fun playAudioCue(cue: AudioCue) {
        audioManager.play(cue)
    }

    // ---------------------------------------------------------------- overlay

    private fun addOverlay() {
        if (overlay != null) return

        // Window 1: full-screen, NON-focusable cursor drawing surface.
        // Never disappears because it never participates in focus arbitration.
        val cursorView = CursorOverlayView(this)
        // Pre-center cursor using known display size before first layout pass
        cursorView.setCursor(displayW / 2f, displayH / 2f)
        val cursorLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Window 2: full-screen, FOCUSABLE, transparent, touchable.
        // Touches are immediately passed to the accessibility gesture system (they don't
        // block the app) because TYPE_ACCESSIBILITY_OVERLAY touch events are handled by
        // the service, not consumed by the view.
        // Being full-screen AND focusable means Android never hands focus back to the app.
        val captureView = JoystickCaptureView(this, ::onJoystick)
        val captureLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }

        try {
            windowManager.addView(cursorView, cursorLp)
            overlay = cursorView
            windowManager.addView(captureView, captureLp)
            joystickCapture = captureView
            captureView.post { captureView.requestFocus() }
            Log.i(TAG, "overlay added")
        } catch (t: Throwable) {
            Log.e(TAG, "addOverlay failed", t)
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager.removeViewImmediate(it) } }
        overlay = null
        joystickCapture?.let { runCatching { windowManager.removeViewImmediate(it) } }
        joystickCapture = null
        frameScheduled = false
    }

    // ---------------------------------------------------------------- buttons

    private val heldForRecording = HashSet<Int>()
    private val heldChordButtons = HashSet<Int>()

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val src = event.source
        val fromGamepad = src and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            src and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            src and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        if (fromGamepad) _controllerConnected.value = true
        if (!fromGamepad && !KeyEvent.isGamepadButton(event.keyCode)) return false

        val code = event.keyCode
        
        // Chord recording mode intercepts everything
        if (_recordingChord.value) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        heldForRecording.add(code)
                        Log.d(TAG, "Recording: held buttons = $heldForRecording")
                    }
                }
                KeyEvent.ACTION_UP -> {
                    // When user starts releasing buttons, capture what they had held
                    if (heldForRecording.size >= 2 && _recordedChord.value == null) {
                        _recordedChord.value = heldForRecording.toSet()
                        Log.d(TAG, "Recorded chord: ${heldForRecording.toSet()}")
                        // Stop recording mode automatically so user can navigate UI
                        _recordingChord.value = false
                    }
                    heldForRecording.remove(code)
                }
            }
            return true  // Always consume events in recording mode
        }
        
        // Manual chord detection with hold duration support
        val s = settings
        val isChordButton = code in s.toggleChord
        
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (isChordButton && event.repeatCount == 0) {
                    heldChordButtons.add(code)
                    
                    // Check if full chord is now held
                    if (heldChordButtons.containsAll(s.toggleChord)) {
                        if (s.chordHoldDurationMs <= 0L) {
                            // Instant toggle
                            toggleMode()
                            return true
                        } else {
                            // Start hold timer
                            chordHoldJob?.cancel()
                            chordHoldJob = scope.launch {
                                kotlinx.coroutines.delay(s.chordHoldDurationMs)
                                // Check if chord is still held after delay
                                if (heldChordButtons.containsAll(s.toggleChord)) {
                                    toggleMode()
                                }
                            }
                        }
                        return true
                    }
                }
                
                if (_mode.value != ServiceMode.MOUSE) return false
                if (event.repeatCount > 0) return DefaultBindings.isGamepadKey(code)
                handleButtonDown(code)
                return DefaultBindings.isGamepadKey(code)
            }
            KeyEvent.ACTION_UP -> {
                if (isChordButton) {
                    heldChordButtons.remove(code)
                    // Cancel hold timer if user releases before duration
                    if (!heldChordButtons.containsAll(s.toggleChord)) {
                        chordHoldJob?.cancel()
                        chordHoldJob = null
                    }
                }
                
                // In GAMEPAD mode we pass everything through untouched
                if (_mode.value != ServiceMode.MOUSE) return false
                handleButtonUp(code)
                return DefaultBindings.isGamepadKey(code)
            }
        }
        return false
    }

    private fun handleButtonDown(code: Int) {
        val action = settings.buttonBindings[code] ?: dpadFallback(code) ?: return
        when (action) {
            MouseAction.TAP -> tapAtCursor(TAP_MS)
            MouseAction.LONG_PRESS -> tapAtCursor(LONG_PRESS_MS)
            MouseAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            MouseAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            MouseAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            MouseAction.SLOW, MouseAction.FAST -> { heldModifiers.add(action); scheduleFrame() }
            MouseAction.TOGGLE_MODE -> toggleMode()
            // System actions
            MouseAction.SCREENSHOT -> takeScreenshot()
            MouseAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            MouseAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            MouseAction.APP_PICKER -> performGlobalAction(GLOBAL_ACTION_RECENTS)  // Same as recents
            MouseAction.POWER_MENU -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            // Media controls
            MouseAction.MEDIA_PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            MouseAction.MEDIA_NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            MouseAction.MEDIA_PREVIOUS -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            MouseAction.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            MouseAction.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            MouseAction.VOLUME_MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            MouseAction.NONE -> Unit
        }
    }

    private fun handleButtonUp(code: Int) {
        val action = settings.buttonBindings[code] ?: return
        if (action == MouseAction.SLOW || action == MouseAction.FAST) heldModifiers.remove(action)
    }

    /** D-pad nudges the cursor a fixed step when no analog stick is present (e.g. INMO ring). */
    private fun dpadFallback(code: Int): MouseAction? {
        val step = 40f
        val ov = overlay ?: return null
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> ov.setCursor(ov.cursorX, (ov.cursorY - step).coerceAtLeast(0f))
            KeyEvent.KEYCODE_DPAD_DOWN -> ov.setCursor(ov.cursorX, (ov.cursorY + step).coerceAtMost(ov.height.toFloat()))
            KeyEvent.KEYCODE_DPAD_LEFT -> ov.setCursor((ov.cursorX - step).coerceAtLeast(0f), ov.cursorY)
            KeyEvent.KEYCODE_DPAD_RIGHT -> ov.setCursor((ov.cursorX + step).coerceAtMost(ov.width.toFloat()), ov.cursorY)
            KeyEvent.KEYCODE_DPAD_CENTER -> return MouseAction.TAP
            else -> return null
        }
        return MouseAction.NONE
    }

    // ---------------------------------------------------------------- sticks

    private fun onJoystick(event: MotionEvent): Boolean {
        _controllerConnected.value = true
        val s = settings
        var lx = event.getAxisValue(MotionEvent.AXIS_X)
        var ly = event.getAxisValue(MotionEvent.AXIS_Y)
        var rx = event.getAxisValue(MotionEvent.AXIS_Z)
        var ry = event.getAxisValue(MotionEvent.AXIS_RZ)
        if (rx == 0f && ry == 0f) {
            rx = event.getAxisValue(MotionEvent.AXIS_RX)
            ry = event.getAxisValue(MotionEvent.AXIS_RY)
        }
        if (s.swapSticks) {
            val tx = lx; val ty = ly
            lx = rx; ly = ry; rx = tx; ry = ty
        }
        // Hat switch (d-pad reported as axes) also moves the cursor.
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (lx == 0f && ly == 0f && (hx != 0f || hy != 0f)) { lx = hx; ly = hy }

        val (dx, dy) = StickProcessor.applyDeadzone(lx, ly, s.deadzone)
        moveX = dx; moveY = dy
        // Store raw right-stick (not deadzone-processed) — circular scroll uses raw values
        scrollX = rx; scrollY = ry
        Log.d(TAG, "onJoystick: moveX=$moveX moveY=$moveY scrollX=$scrollX scrollY=$scrollY")
        scheduleFrame()
        return true
    }

    private val frameCallback = Choreographer.FrameCallback { nowNs -> onFrame(nowNs) }

    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun onFrame(nowNs: Long) {
        frameScheduled = false
        val ov = overlay ?: return
        joystickCapture?.reclaimFocus()
        val s = settings
        val dt = if (lastFrameNs == 0L) 1f / 60f else ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        lastFrameNs = nowNs

        val mult = when {
            MouseAction.SLOW in heldModifiers -> s.slowMultiplier
            MouseAction.FAST in heldModifiers -> s.fastMultiplier
            else -> 1f
        }
        // moveX/moveY are already deadzone-rescaled, so pass deadzone=0 here.
        val v = StickProcessor.toVelocity(moveX, moveY, 0f, s.curveExponent, s.baseSpeedPxPerSec, mult)
        if (v.vx.isNaN() || v.vy.isNaN()) {
            Log.w(TAG, "NaN velocity detected, skipping cursor movement (moveX=$moveX moveY=$moveY)")
            // Don't return - we still need to process scroll!
        } else if (!v.isZero) {
            val w = if (ov.width > 0) ov.width.toFloat() else displayW
            val h = if (ov.height > 0) ov.height.toFloat() else displayH
            val (nx, ny) = StickProcessor.step(ov.cursorX, ov.cursorY, v, dt, w, h)
            ov.setCursor(nx, ny)
        }

        val now = System.currentTimeMillis()
        val w = if (ov.width > 0) ov.width.toFloat() else displayW
        val h = if (ov.height > 0) ov.height.toFloat() else displayH

        val scrollDist: Float
        if (s.circularScroll) {
            // Raw right-stick values (not deadzone-processed) stored as scrollX/scrollY
            val raw = StickProcessor.circularScrollDelta(
                prevScrollX, prevScrollY, scrollX, scrollY, s.deadzone, s.scrollStepPx
            )
            scrollDist = if (s.invertScroll) -raw else raw
            Log.d(TAG, "CIRCULAR: prevX=$prevScrollX prevY=$prevScrollY currX=$scrollX currY=$scrollY raw=$raw scrollDist=$scrollDist")
        } else {
            val sy = StickProcessor.scrollDistance(scrollY, s.deadzone, s.scrollStepPx)
            scrollDist = if (s.invertScroll) sy else -sy  // stick down (+Y) = swipe up = scroll down
            Log.d(TAG, "LINEAR: scrollY=$scrollY sy=$sy invertScroll=${s.invertScroll} scrollDist=$scrollDist")
        }
        prevScrollX = scrollX; prevScrollY = scrollY
        Log.d(TAG, "SCROLL CHECK: dist=$scrollDist scrollY=$scrollY tapGestureInFlight=$tapGestureInFlight timeSinceScroll=${now - lastScrollAt} threshold=$SCROLL_TICK_MS")

        if (scrollDist != 0f && now - lastScrollAt >= SCROLL_TICK_MS) {
            lastScrollAt = now
            val cx = ov.cursorX.coerceIn(0f, w)
            val cy = ov.cursorY.coerceIn(0f, h)
            Log.d(TAG, "scroll swipe dist=$scrollDist from ($cx,$cy) scrollY=$scrollY")
            swipeScroll(cx, cy, cx, (cy + scrollDist).coerceIn(0f, h), SCROLL_TICK_MS - 20)
        }

        val keepGoing = true  // keep loop alive in mouse mode so joystick focus is maintained
        if (keepGoing) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            lastFrameNs = 0L
        }
    }

    // ---------------------------------------------------------------- gestures

    private fun tapAtCursor(durationMs: Long) {
        val ov = overlay ?: return
        val path = Path().apply { moveTo(ov.cursorX, ov.cursorY) }
        dispatchTap(GestureDescription.StrokeDescription(path, 0, durationMs))
        if (durationMs <= TAP_MS) {
            audioManager.play(AudioCue.TAP)
        } else {
            audioManager.play(AudioCue.LONG_PRESS)
        }
    }

    private fun swipeScroll(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val ov = overlay ?: return
        val w = ov.width.toFloat(); val h = ov.height.toFloat()
        
        // Don't dispatch gestures smaller than MIN_SCROLL_DISTANCE — they register as taps
        val distance = kotlin.math.abs(y2 - y1)
        if (distance < 20f) {  // Increased from 10px to 20px for more safety
            Log.d(TAG, "Scroll distance too small ($distance px), skipping to avoid tap")
            return
        }
        
        val path = Path().apply {
            moveTo(x1.coerceIn(0f, w), y1.coerceIn(0f, h))
            lineTo(x2.coerceIn(0f, w), y2.coerceIn(0f, h))
        }
        // Scroll gestures don't block — fire and forget
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, durationMs)
        ).build()
        val dispatched = dispatchGesture(gesture, null, null)
        if (!dispatched) {
            Log.w(TAG, "Scroll gesture failed to dispatch")
        }
    }

    private fun dispatchTap(stroke: GestureDescription.StrokeDescription) {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        tapGestureInFlight = true
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { tapGestureInFlight = false }
            override fun onCancelled(g: GestureDescription?) { tapGestureInFlight = false }
        }, null)
        if (!ok) tapGestureInFlight = false
    }

    // ---------------------------------------------------------------- system actions

    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            Log.w(TAG, "Screenshot action requires Android 9+")
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager?.dispatchMediaKeyEvent(eventDown)
        audioManager?.dispatchMediaKeyEvent(eventUp)
    }

    private fun adjustVolume(direction: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        audioManager?.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }
}
