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
import com.droidforge.gamepadmouse.input.KeyboardCommand
import com.droidforge.gamepadmouse.input.KeyboardInputRouter
import com.droidforge.gamepadmouse.input.HatNavigationDetector
import com.droidforge.gamepadmouse.input.BindingMatcher
import com.droidforge.gamepadmouse.input.ButtonBinding
import com.droidforge.gamepadmouse.input.MouseAction
import com.droidforge.gamepadmouse.input.ModeTransitions
import com.droidforge.gamepadmouse.input.ServiceMode
import com.droidforge.gamepadmouse.input.StickProcessor
import com.droidforge.gamepadmouse.settings.Settings
import com.droidforge.gamepadmouse.settings.SettingsRepository
import com.droidforge.gamepadmouse.settings.ProfileManager
import com.droidforge.gamepadmouse.settings.toProfileSettings
import com.droidforge.gamepadmouse.settings.toSettings
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
        
        private val _currentDevice = MutableStateFlow<String?>(null)
        val currentDevice: StateFlow<String?> = _currentDevice.asStateFlow()

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
            _recordedChord.value = null
            instance?.beginChordRecording()
        }
        
        fun stopChordRecording() {
            _recordingChord.value = false
            _recordedChord.value = null
            instance?.heldForRecording?.clear()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: SettingsRepository
    private lateinit var profileManager: ProfileManager
    @Volatile private var settings = Settings()
    @Volatile private var currentDeviceId: String? = null
    @Volatile private var currentDeviceName: String? = null

    private lateinit var windowManager: WindowManager
    private var overlay: CursorOverlayView? = null
    private var keyboardOverlay: KeyboardOverlayView? = null
    private var keyboardTargetNode: android.view.accessibility.AccessibilityNodeInfo? = null
    private var suppressAutoKeyboardUntil = 0L
    /** True while an editable field holds input focus (tracked from a11y focus events). */
    private var editableFieldFocused = false
    /** True while the capture window is demoted to NOT_FOCUSABLE so the system IME can open. */
    private var imeShield = false
    private var captureParams: WindowManager.LayoutParams? = null
    private var savedImeShowMode = -1
    
    // Cursor auto-hide timer
    private var hideJob: kotlinx.coroutines.Job? = null
    private var lastCursorActivity = 0L
    private var joystickCapture: JoystickCaptureView? = null
    private var displayW = 1080f
    private var displayH = 1920f

    private val chord = ChordDetector(DefaultBindings.toggleChord)
    private val heldModifiers = HashSet<MouseAction>()
    private val bindingMatcher = BindingMatcher()
    private val keyboardHatNavigation = HatNavigationDetector()
    private val bindingHoldJobs = mutableMapOf<ButtonBinding, kotlinx.coroutines.Job>()
    
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
        // API 34+: receive joystick/hat motion directly — no focusable capture
        // window needed (which stole window focus and broke the system IME).
        if (Build.VERSION.SDK_INT >= 34) enableMotionEventSources(true)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Cache real display size for use before overlay gets laid out
        @Suppress("DEPRECATION")
        val dm = resources.displayMetrics
        displayW = dm.widthPixels.toFloat()
        displayH = dm.heightPixels.toFloat()
        repo = SettingsRepository(this)
        profileManager = com.droidforge.gamepadmouse.settings.ProfileManager(this)
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
                // Update cursor size and color
                overlay?.cursorSizeMultiplier = s.cursorSize
                overlay?.cursorColor = s.cursorColor
                keyboardOverlay?.widthPercent = s.keyboardWidthPercent
                keyboardOverlay?.heightPercent = s.keyboardHeightPercent
                keyboardOverlay?.atTop = s.keyboardAtTop
                keyboardOverlay?.showNumberRow = s.keyboardShowNumberRow
                keyboardOverlay?.showSystemKeys = s.keyboardShowSystemKeys
                keyboardOverlay?.keyboardColor = s.keyboardColor
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
        removeKeyboardOverlay()
        scope.cancel()
        if (::audioManager.isInitialized) audioManager.release()
        instance = null
        _running.value = false
        _mode.value = ServiceMode.GAMEPAD
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.source?.let { source ->
            // Samsung often delivers the focused editable as the source of
            // WINDOW_CONTENT_CHANGED events, so accept ANY event type here (like the
            // original auto-show path) — not just TYPE_VIEW_FOCUSED.
            if (source.isEditable && source.isFocused) {
                if (!editableFieldFocused) {
                    editableFieldFocused = true
                    keyboardTargetNode = source
                    Log.d(TAG, "editable focus detected (eventType=${event.eventType})")
                }
                if (_mode.value == ServiceMode.MOUSE) {
                    if (settings.autoShowKeyboardOnTextField &&
                        android.os.SystemClock.uptimeMillis() >= suppressAutoKeyboardUntil
                    ) {
                        setMode(ServiceMode.KEYBOARD)
                    } else if (!imeShield) {
                        // Text box tapped by hand in mouse mode: demote the capture window
                        // to NOT_FOCUSABLE so window focus returns to the app, then send a
                        // synthetic tap on the field to trigger the native
                        // showSoftInputOnFocus path (opens the SYSTEM keyboard).
                        imeShield = true
                        setCaptureWindowFocusable(false)
                        source.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
                        scheduleImeTap(source)
                    }
                }
            } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                editableFieldFocused = false
            }
        }
        // Reclaim joystick-capture focus ONLY in mouse mode and only while no editable
        // field holds input focus. Reclaiming while a text field is focused steals focus
        // from the app, which blocks ACTION_SET_TEXT (typed text never reaches the field)
        // and prevents the system keyboard from opening.
        if (_mode.value == ServiceMode.MOUSE && !editableFieldFocused) {
            joystickCapture?.post { joystickCapture?.reclaimFocus() }
        }
    }
    override fun onInterrupt() { /* not needed */ }

    /** Toggle global joystick motion delivery. Off in GAMEPAD mode so games receive input. */
    private fun enableMotionEventSources(enable: Boolean) {
        try {
            serviceInfo = serviceInfo.apply {
                motionEventSources = if (enable) {
                    InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD
                } else 0
            }
            Log.i(TAG, "motionEventSources enabled=$enable")
        } catch (t: Throwable) {
            Log.w(TAG, "motionEventSources: ${t.message}")
        }
    }

    /** Joystick/hat events delivered straight to the service (API 34+, no window). */
    override fun onMotionEvent(event: android.view.MotionEvent) {
        when (_mode.value) {
            ServiceMode.MOUSE -> onJoystick(event)
            ServiceMode.KEYBOARD -> {
                val kb = keyboardOverlay ?: return
                val hx = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
                val hy = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)
                when (keyboardHatNavigation.update(hx, hy)) {
                    KeyboardCommand.UP -> kb.moveSelection(-1, 0)
                    KeyboardCommand.DOWN -> kb.moveSelection(1, 0)
                    KeyboardCommand.LEFT -> kb.moveSelection(0, -1)
                    KeyboardCommand.RIGHT -> kb.moveSelection(0, 1)
                    else -> Unit
                }
            }
            ServiceMode.GAMEPAD -> Unit
        }
    }

    // ---------------------------------------------------------------- mode

    fun toggleMode() = setMode(if (_mode.value == ServiceMode.MOUSE) ServiceMode.GAMEPAD else ServiceMode.MOUSE)

    fun showKeyboardForFocusedField() {
        if (settings.autoShowKeyboardOnTextField && _mode.value == ServiceMode.MOUSE) {
            scope.launch {
                kotlinx.coroutines.delay(120)
                setMode(ServiceMode.KEYBOARD)
            }
        }
    }

    fun setMode(newMode: ServiceMode) {
        if (_mode.value == newMode) return
        Log.d(TAG, "mode request ${_mode.value} -> $newMode")
        bindingMatcher.reset()
        bindingHoldJobs.values.forEach { it.cancel() }
        bindingHoldJobs.clear()
        _mode.value = newMode
        heldModifiers.clear()
        moveX = 0f; moveY = 0f; scrollX = 0f; scrollY = 0f
        when (newMode) {
            ServiceMode.MOUSE -> {
                removeKeyboardOverlay()
                addOverlay()
                scheduleFrame()
                editableFieldFocused = false  // full mouse control; stop protecting field focus
                imeShield = false
                if (Build.VERSION.SDK_INT >= 34) enableMotionEventSources(true)
                setSystemImeHidden(false)
                audioManager.play(AudioCue.MODE_SWITCH_MOUSE)
            }
            ServiceMode.GAMEPAD -> {
                removeOverlay()
                removeKeyboardOverlay()
                if (Build.VERSION.SDK_INT >= 34) enableMotionEventSources(false)  // pass sticks to games
                setSystemImeHidden(false)
                audioManager.play(AudioCue.MODE_SWITCH_GAMEPAD)
            }
            ServiceMode.KEYBOARD -> {
                keyboardTargetNode = findEditableInputNode()
                removeOverlay()
                addKeyboardOverlay()
                if (Build.VERSION.SDK_INT >= 34) enableMotionEventSources(true)
                setSystemImeHidden(true)  // our overlay replaces the system IME
                audioManager.play(AudioCue.MODE_SWITCH_MOUSE)
            }
        }
        Log.d(TAG, "mode -> $newMode")
    }
    
    fun playAudioCue(cue: AudioCue) {
        audioManager.play(cue)
    }
    
    // Cursor visibility management
    private fun showCursor() {
        overlay?.isVisible = true
        resetAutoHideTimer()
    }
    
    private fun hideCursor() {
        overlay?.isVisible = false
        hideJob?.cancel()
        hideJob = null
    }
    
    private fun resetAutoHideTimer() {
        val timeout = settings.autoHideTimeoutMs
        if (timeout <= 0) return  // Disabled
        
        lastCursorActivity = System.currentTimeMillis()
        hideJob?.cancel()
        hideJob = scope.launch {
            kotlinx.coroutines.delay(timeout)
            // Check if there was activity during the delay
            if (System.currentTimeMillis() - lastCursorActivity >= timeout) {
                hideCursor()
            }
        }
    }
    
    // Device detection and profile switching
    private fun detectAndSwitchDevice(androidDeviceId: Int) {
        val device = InputDevice.getDevice(androidDeviceId) ?: return
        val deviceId = "device_${device.descriptor.hashCode()}"
        val deviceName = device.name ?: "Unknown Controller"
        
        // If it's the same device, just update last-used timestamp
        if (currentDeviceId == deviceId) {
            scope.launch {
                profileManager.touchProfile(deviceId)
            }
            return
        }
        
        // New device detected - switch profile
        currentDeviceId = deviceId
        currentDeviceName = deviceName
        _currentDevice.value = deviceName
        
        scope.launch {
            // Get or create profile for this device
            val profile = profileManager.getOrCreateProfile(
                deviceId = deviceId,
                deviceName = deviceName,
                defaultSettings = settings.toProfileSettings()
            )
            
            // Set as active device
            profileManager.setActiveDevice(deviceId)
            
            // Load profile settings into current settings
            val newSettings = profile.settings.toSettings()
            
            // Apply each setting individually through the repository
            // This ensures proper persistence and reactivity
            repo.setBaseSpeed(newSettings.baseSpeedPxPerSec)
            repo.setSlowMultiplier(newSettings.slowMultiplier)
            repo.setFastMultiplier(newSettings.fastMultiplier)
            repo.setDeadzone(newSettings.deadzone)
            repo.setCurveExponent(newSettings.curveExponent)
            repo.setScrollStep(newSettings.scrollStepPx)
            repo.setInvertScroll(newSettings.invertScroll)
            repo.setSwapSticks(newSettings.swapSticks)
            repo.setCircularScroll(newSettings.circularScroll)
            repo.setStartInMouseMode(newSettings.startInMouseMode)
            repo.setToggleChord(newSettings.toggleChord)
            repo.setChordHoldDuration(newSettings.chordHoldDurationMs)
            repo.setBindings(newSettings.buttonBindings)
            repo.setDetailedBindings(newSettings.detailedBindings)
            repo.setAudioPack(newSettings.audioPack)
            repo.setCursorStyle(newSettings.cursorStyle)
            repo.setCursorSize(newSettings.cursorSize)
            repo.setCursorColor(newSettings.cursorColor)
            repo.setAutoHideTimeout(newSettings.autoHideTimeoutMs)
            repo.setKeyboardWidthPercent(newSettings.keyboardWidthPercent)
            repo.setKeyboardHeightPercent(newSettings.keyboardHeightPercent)
            repo.setKeyboardAtTop(newSettings.keyboardAtTop)
            repo.setKeyboardShowNumberRow(newSettings.keyboardShowNumberRow)
            repo.setKeyboardShowSystemKeys(newSettings.keyboardShowSystemKeys)
            repo.setKeyboardColor(newSettings.keyboardColor)
            repo.setAutoShowKeyboardOnTextField(newSettings.autoShowKeyboardOnTextField)
            
            Log.i(TAG, "Switched to profile: $deviceName ($deviceId)")
        }
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or  // Cursor must not block touches
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
        val captureView = JoystickCaptureView(this, ::onJoystick, ::onCapturedKeyEvent, ::onCaptureWindowFocusLost)
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
            
            // Note: Manual tap detection disabled for now to avoid blocking touch input
            // The cursor overlay must have FLAG_NOT_TOUCHABLE to let touches pass through
            
            // API 34+: sticks arrive via onMotionEvent — no capture window (it steals
            // window focus and blocks the system IME). Legacy: capture window required.
            if (Build.VERSION.SDK_INT < 34) {
                windowManager.addView(captureView, captureLp)
                joystickCapture = captureView
                captureParams = captureLp
                captureView.post { captureView.requestFocus() }
            }
            Log.i(TAG, "overlay added (captureWindow=${joystickCapture != null})")
        } catch (t: Throwable) {
            Log.e(TAG, "addOverlay failed", t)
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager.removeViewImmediate(it) } }
        overlay = null
        joystickCapture?.let { runCatching { windowManager.removeViewImmediate(it) } }
        joystickCapture = null
        captureParams = null
        frameScheduled = false
    }

    /**
     * Toggles the capture window between focusable (joystick capture) and
     * NOT_FOCUSABLE (lets the app window take focus so the system IME shows).
     * clearFocus() alone never moves WINDOW focus — only a flag change does.
     */
    private fun setCaptureWindowFocusable(focusable: Boolean) {
        val view = joystickCapture ?: return
        val lp = captureParams ?: return
        val newFlags = if (focusable) lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (newFlags != lp.flags) {
            lp.flags = newFlags
            runCatching { windowManager.updateViewLayout(view, lp) }
        }
        if (!focusable) view.clearFocus()
        Log.d(TAG, "capture focusable=$focusable")
    }

    /**
     * Called by the capture view when it loses WINDOW focus — i.e. the user just
     * tapped something in the app underneath. Shield immediately: demote the capture
     * window so the app keeps focus and the system IME can open. (Waiting for an
     * accessibility focus event doesn't work — the per-frame focus reclaim steals
     * window focus back before the event can be delivered.)
     */
    private fun onCaptureWindowFocusLost() {
        if (_mode.value != ServiceMode.MOUSE || imeShield) return
        imeShield = true
        editableFieldFocused = true  // stop the frame loop from reclaiming focus
        setCaptureWindowFocusable(false)
        Log.d(TAG, "app took window focus -> ime shield on")
    }

    /** Hide/show the system IME (used while our overlay keyboard is up). */
    private fun setSystemImeHidden(hidden: Boolean) {
        try {
            val skc = softKeyboardController
            if (hidden) {
                savedImeShowMode = skc.showMode
                skc.setShowMode(AccessibilityService.SHOW_MODE_HIDDEN)
            } else if (savedImeShowMode >= 0) {
                skc.setShowMode(savedImeShowMode)
                savedImeShowMode = -1
            }
        } catch (t: Throwable) {
            Log.w(TAG, "softKeyboardController: ${t.message}")
        }
    }

    /**
     * After demoting the capture window, tap the field's center through the gesture
     * pipeline. The touch lands on the app's EditText as a real user tap, so the
     * system IME opens via the normal showSoftInputOnFocus flow.
     */
    private fun scheduleImeTap(node: android.view.accessibility.AccessibilityNodeInfo) {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return
        val cx = rect.exactCenterX().coerceIn(0f, displayW)
        val cy = rect.exactCenterY().coerceIn(0f, displayH)
        scope.launch {
            kotlinx.coroutines.delay(150)  // let window focus settle after the flag flip
            val path = Path().apply { moveTo(cx, cy) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            val ok = dispatchGesture(gesture, null, null)
            Log.d(TAG, "ime tap at ($cx,$cy) dispatched=$ok")
        }
    }
    
    private fun addKeyboardOverlay() {
        if (keyboardOverlay != null) return
       
        val kbView = KeyboardOverlayView(this)
        kbView.widthPercent = 100f
        kbView.heightPercent = 100f
        kbView.atTop = settings.keyboardAtTop
        kbView.showNumberRow = settings.keyboardShowNumberRow
        kbView.showSystemKeys = settings.keyboardShowSystemKeys
        kbView.keyboardColor = settings.keyboardColor
        kbView.onKeyPressed = ::activateKeyboardKey
        kbView.audioManager = audioManager
        val keyboardWidth = (displayW * settings.keyboardWidthPercent / 100f).toInt()
        val keyboardHeight = (displayH * settings.keyboardHeightPercent / 100f).toInt()
        val kbLp = WindowManager.LayoutParams(
            keyboardWidth,
            keyboardHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = (if (settings.keyboardAtTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        
        try {
            windowManager.addView(kbView, kbLp)
            keyboardOverlay = kbView
            // NOTE: no focusable capture window in keyboard mode. It would steal window
            // focus from the app, which makes ACTION_SET_TEXT fail (typed text never
            // reaches the field). Gamepad/d-pad buttons still arrive via onKeyEvent
            // (system-wide key filtering needs no window focus).
            Log.i(TAG, "keyboard overlay added (no capture window; app keeps focus)")
        } catch (t: Throwable) {
            Log.e(TAG, "addKeyboardOverlay failed", t)
        }
    }

    private fun removeKeyboardOverlay() {
        keyboardOverlay?.let { runCatching { windowManager.removeViewImmediate(it) } }
        keyboardOverlay = null
        joystickCapture?.let { runCatching { windowManager.removeViewImmediate(it) } }
        joystickCapture = null
    }

    // ---------------------------------------------------------------- buttons

    private val heldForRecording = HashSet<Int>()
    private var recordingArmedAt = 0L

    private fun beginChordRecording() {
        heldForRecording.clear()
        recordingArmedAt = android.os.SystemClock.uptimeMillis() + 250L
        _recordingChord.value = true
    }
    private val heldChordButtons = HashSet<Int>()

    override fun onKeyEvent(event: KeyEvent): Boolean = handleGamepadKeyEvent(event)

    private fun onCapturedKeyEvent(event: KeyEvent): Boolean = handleGamepadKeyEvent(event)

    private fun handleGamepadKeyEvent(event: KeyEvent): Boolean {
        val src = event.source
        val fromGamepad = src and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            src and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            src and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        if (fromGamepad) {
            _controllerConnected.value = true
            // Detect device and switch profile if needed
            detectAndSwitchDevice(event.deviceId)
        }
        if (!fromGamepad && !KeyEvent.isGamepadButton(event.keyCode)) return false

        val code = event.keyCode
        if (_mode.value == ServiceMode.MOUSE && imeShield && event.action == KeyEvent.ACTION_DOWN) {
            // User grabbed the gamepad again: restore joystick capture focus.
            imeShield = false
            editableFieldFocused = false
            setCaptureWindowFocusable(true)
            joystickCapture?.requestFocus()
        }
        
        // Chord recording mode intercepts everything
        if (_recordingChord.value) {
            if (android.os.SystemClock.uptimeMillis() < recordingArmedAt) return true
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        heldForRecording.add(code)
                        Log.d(TAG, "Recording: held buttons = $heldForRecording")
                    }
                }
                KeyEvent.ACTION_UP -> {
                    // When user starts releasing buttons, capture what they had held
                    if (heldForRecording.isNotEmpty() && _recordedChord.value == null) {
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
                
                if (_mode.value == ServiceMode.KEYBOARD) {
                    if (event.repeatCount > 0) return DefaultBindings.isGamepadKey(code)
                    bindingMatcher.keyDown(code)
                    val keyboardToggle = s.detailedBindings
                        .filter { it.action == MouseAction.KEYBOARD_MODE }
                        .sortedByDescending { it.keyCodes.size }
                        .firstOrNull { bindingMatcher.isHeld(it) }
                    if (keyboardToggle != null) {
                        setMode(ServiceMode.MOUSE)
                        return true
                    }
                    val keyboardBindings = s.detailedBindings.filter { it.appliesIn(ServiceMode.KEYBOARD) }
                    val matched = bindingMatcher.matching(keyboardBindings, ServiceMode.KEYBOARD)
                    if (matched.isNotEmpty()) {
                        matched.forEach { binding ->
                            bindingMatcher.markFired(binding)
                            executeAction(binding.action)
                        }
                        return true
                    }
                    handleKeyboardButtonDown(code)
                    return DefaultBindings.isGamepadKey(code)
                }

                if (event.repeatCount > 0) return DefaultBindings.isGamepadKey(code)
                bindingMatcher.keyDown(code)
                val modeBindings = s.detailedBindings.filter { it.appliesIn(_mode.value) }
                bindingMatcher.matching(modeBindings, _mode.value).forEach { binding ->
                    bindingHoldJobs[binding]?.cancel()
                    if (binding.holdDurationMs <= 0L) {
                        bindingMatcher.markFired(binding)
                        executeAction(binding.action)
                    } else {
                        bindingHoldJobs[binding] = scope.launch {
                            kotlinx.coroutines.delay(binding.holdDurationMs)
                            if (bindingMatcher.isHeld(binding)) {
                                executeAction(binding.action)
                                bindingMatcher.markFired(binding)
                            }
                        }
                    }
                }
                val consumedByBinding = modeBindings.any { code in it.keyCodes }
                return if (_mode.value == ServiceMode.GAMEPAD) consumedByBinding else
                    consumedByBinding || DefaultBindings.isGamepadKey(code)
            }
            KeyEvent.ACTION_UP -> {
                bindingMatcher.keyUp(code)
                bindingHoldJobs.filterKeys { code in it.keyCodes }.values.forEach { it.cancel() }
                bindingHoldJobs.keys.removeAll { code in it.keyCodes }
                if (isChordButton) {
                    heldChordButtons.remove(code)
                    // Cancel hold timer if user releases before duration
                    if (!heldChordButtons.containsAll(s.toggleChord)) {
                        chordHoldJob?.cancel()
                        chordHoldJob = null
                    }
                }
                
                if (_mode.value == ServiceMode.KEYBOARD) {
                    handleKeyboardButtonUp(code)
                    return DefaultBindings.isGamepadKey(code)
                }
                val consumedByBinding = s.detailedBindings.any {
                    it.appliesIn(_mode.value) && code in it.keyCodes
                }
                s.detailedBindings.filter {
                    it.appliesIn(_mode.value) && code in it.keyCodes &&
                        (it.action == MouseAction.SLOW || it.action == MouseAction.FAST)
                }.forEach { heldModifiers.remove(it.action) }
                return if (_mode.value == ServiceMode.GAMEPAD) consumedByBinding else
                    consumedByBinding || DefaultBindings.isGamepadKey(code)
            }
        }
        return false
    }

    private fun executeAction(action: MouseAction) {
        when (action) {
            MouseAction.TAP -> tapAtCursor(TAP_MS)
            MouseAction.LONG_PRESS -> tapAtCursor(LONG_PRESS_MS)
            MouseAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            MouseAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            MouseAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            MouseAction.SLOW, MouseAction.FAST -> { heldModifiers.add(action); scheduleFrame() }
            MouseAction.TOGGLE_MODE -> toggleMode()
            MouseAction.SCREENSHOT -> takeScreenshot()
            MouseAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            MouseAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            MouseAction.APP_PICKER -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            MouseAction.POWER_MENU -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            MouseAction.MEDIA_PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            MouseAction.MEDIA_NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            MouseAction.MEDIA_PREVIOUS -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            MouseAction.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            MouseAction.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            MouseAction.VOLUME_MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            MouseAction.KEYBOARD_MODE -> {
                if (_mode.value == ServiceMode.KEYBOARD) {
                    suppressAutoKeyboardUntil = android.os.SystemClock.uptimeMillis() + 1500L
                }
                setMode(ModeTransitions.keyboardActionTarget(_mode.value))
            }
            MouseAction.KEYBOARD_PRESS -> if (_mode.value == ServiceMode.KEYBOARD) {
                keyboardOverlay?.getCurrentSelectedKey()?.let(::activateKeyboardKey)
            }
            MouseAction.KEYBOARD_BACK -> if (_mode.value == ServiceMode.KEYBOARD) {
                keyboardOverlay?.let(::backspaceText)
            }
            MouseAction.KEYBOARD_MOVE -> if (_mode.value == ServiceMode.KEYBOARD) {
                keyboardOverlay?.let { kb ->
                    scope.launch { repo.setKeyboardAtTop(!settings.keyboardAtTop) }
                    setMode(ServiceMode.MOUSE)
                    scope.launch { kotlinx.coroutines.delay(100); setMode(ServiceMode.KEYBOARD) }
                }
            }
            MouseAction.KEYBOARD_HIDE -> if (_mode.value == ServiceMode.KEYBOARD) setMode(ServiceMode.MOUSE)
            MouseAction.NONE -> Unit
        }
    }
    
    private fun handleKeyboardButtonDown(code: Int) {
        val kb = keyboardOverlay ?: return
        when (KeyboardInputRouter.commandFor(code)) {
            KeyboardCommand.UP -> kb.moveSelection(-1, 0)
            KeyboardCommand.DOWN -> kb.moveSelection(1, 0)
            KeyboardCommand.LEFT -> kb.moveSelection(0, -1)
            KeyboardCommand.RIGHT -> kb.moveSelection(0, 1)
            KeyboardCommand.SELECT -> activateKeyboardKey(kb.getCurrentSelectedKey())
            KeyboardCommand.BACKSPACE -> backspaceText(kb)
            KeyboardCommand.SPACE -> appendText(kb, " ")
            KeyboardCommand.SHIFT -> {
                // Toggle shift (uppercase/lowercase)
                kb.shiftEnabled = !kb.shiftEnabled
            }
            KeyboardCommand.PREVIOUS_LAYOUT -> {
                // Previous layout
                kb.currentLayout = when (kb.currentLayout) {
                    KeyboardOverlayView.KeyboardLayout.LETTERS -> KeyboardOverlayView.KeyboardLayout.SYMBOLS
                    KeyboardOverlayView.KeyboardLayout.NUMBERS -> KeyboardOverlayView.KeyboardLayout.LETTERS
                    KeyboardOverlayView.KeyboardLayout.SYMBOLS -> KeyboardOverlayView.KeyboardLayout.NUMBERS
                }
            }
            KeyboardCommand.NEXT_LAYOUT -> {
                // Next layout
                kb.currentLayout = when (kb.currentLayout) {
                    KeyboardOverlayView.KeyboardLayout.LETTERS -> KeyboardOverlayView.KeyboardLayout.NUMBERS
                    KeyboardOverlayView.KeyboardLayout.NUMBERS -> KeyboardOverlayView.KeyboardLayout.SYMBOLS
                    KeyboardOverlayView.KeyboardLayout.SYMBOLS -> KeyboardOverlayView.KeyboardLayout.LETTERS
                }
            }
            KeyboardCommand.ENTER -> appendText(kb, "\n")
            KeyboardCommand.EXIT -> {
                // Exit keyboard mode
                setMode(ServiceMode.MOUSE)
            }
            KeyboardCommand.NONE -> Unit
        }
    }
    
    private fun handleKeyboardButtonUp(code: Int) {
        // No-op for now, all actions happen on button down
    }
    
    private fun activateKeyboardKey(key: String) {
        val kb = keyboardOverlay ?: return
        when (key) {
            KeyboardOverlayView.KEY_SHIFT -> {
                kb.shiftEnabled = !kb.shiftEnabled
                audioManager.play(AudioCue.KEYBOARD_TAP)
            }
            KeyboardOverlayView.KEY_CAPS -> {
                kb.capsLockEnabled = !kb.capsLockEnabled
                audioManager.play(AudioCue.KEYBOARD_TAP)
            }
            KeyboardOverlayView.KEY_BACKSPACE -> {
                backspaceText(kb)
                audioManager.play(AudioCue.KEYBOARD_TAP)
            }
            KeyboardOverlayView.KEY_SPACE -> {
                appendText(kb, " ")
                audioManager.play(AudioCue.KEYBOARD_TAP)
            }
            KeyboardOverlayView.KEY_ENTER -> {
                appendText(kb, "\n")
                audioManager.play(AudioCue.KEYBOARD_ENTER)
            }
            KeyboardOverlayView.KEY_POSITION -> {
                scope.launch { repo.setKeyboardAtTop(!settings.keyboardAtTop) }
                setMode(ServiceMode.MOUSE)
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    setMode(ServiceMode.KEYBOARD)
                }
            }
            KeyboardOverlayView.KEY_HIDE -> setMode(ServiceMode.MOUSE)
            else -> {
                appendText(kb, kb.displayCharacter(key))
                kb.consumeOneShotShift()
                audioManager.play(AudioCue.KEYBOARD_TAP)
            }
        }
    }

    private fun appendText(kb: KeyboardOverlayView, text: String) {
        val updated = kb.currentText + text
        // Only mirror the buffer once the field actually accepted the text, so a
        // failed write doesn't show phantom characters above the keyboard.
        if (setFocusedText(updated)) kb.currentText = updated
    }

    private fun backspaceText(kb: KeyboardOverlayView) {
        if (kb.currentText.isEmpty()) return
        val updated = kb.currentText.dropLast(1)
        if (setFocusedText(updated)) kb.currentText = updated
    }

    private fun setFocusedText(text: String): Boolean {
        val node = findEditableInputNode() ?: return false
        // ACTION_SET_TEXT only lands on the node holding input focus; nudge it first.
        if (!node.isFocused) {
            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
        }
        val args = android.os.Bundle().apply {
            putCharSequence(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }
        val ok = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "setFocusedText len=${text.length} ok=$ok")
        return ok
    }

    /**
     * Resolves the editable text target, preferring the node captured from the last
     * focus event (refreshed), then scanning every window for the input-focused field.
     * Cached nodes go stale quickly — never trust one across redraws.
     */
    private fun findEditableInputNode(): android.view.accessibility.AccessibilityNodeInfo? {
        keyboardTargetNode?.let { cached ->
            if (cached.refresh()) {
                if (cached.isEditable) return cached
            } else {
                keyboardTargetNode = null  // stale handle — drop it
            }
        }
        for (window in windows) {
            val root = window.root ?: continue
            val focused = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                keyboardTargetNode = focused
                return focused
            }
        }
        return null
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
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (_mode.value == ServiceMode.KEYBOARD) {
            val kb = keyboardOverlay ?: return true
            when (keyboardHatNavigation.update(hx, hy)) {
                KeyboardCommand.UP -> kb.moveSelection(-1, 0)
                KeyboardCommand.DOWN -> kb.moveSelection(1, 0)
                KeyboardCommand.LEFT -> kb.moveSelection(0, -1)
                KeyboardCommand.RIGHT -> kb.moveSelection(0, 1)
                else -> Unit
            }
            return true
        }
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
        if (_mode.value == ServiceMode.MOUSE && imeShield) {
            imeShield = false
            editableFieldFocused = false
            setCaptureWindowFocusable(true)
            joystickCapture?.requestFocus()
        }
        // Hat switch (d-pad reported as axes) also moves the cursor.
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
        if (!editableFieldFocused) joystickCapture?.reclaimFocus()
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
            showCursor()  // Show cursor when gamepad moves it
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
            showCursor()  // Show cursor on scroll
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
        // Don't call showCursor() here - let dispatchTap handle hiding if needed
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
