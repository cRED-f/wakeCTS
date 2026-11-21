package com.example.ctsbutton

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import androidx.core.content.ContextCompat

class AssistantButtonService : AccessibilityService() {

    private val windowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }
    private val handler = Handler(Looper.getMainLooper())

    private var floatingButton: ImageButton? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var oceanView: OceanDropView? = null
    private var receiverRegistered = false
    private var eatAnimator: AnimatorSet? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isLongPressed = false
    private var isConsuming = false
    private var wasOverOcean = false
    private var tapCount = 0
    private var lastTapAt = 0L

    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    // Delay a single tap briefly so a triple tap can reveal the ocean without
    // triggering Circle to Search on the first two taps.
    private val pendingTapRunnable = Runnable {
        tapCount = 0
        if (!isConsuming && AppPreferences.isWakeEnabled) triggerSearch()
    }

    private val hideOceanRunnable = Runnable {
        if (!isDragging && !isLongPressed && !isConsuming) hideOceanDropZone()
    }

    private val longPressRunnable = Runnable {
        if (!isDragging && !isConsuming && floatingButton != null) {
            handler.removeCallbacks(pendingTapRunnable)
            tapCount = 0
            isLongPressed = true
            floatingButton?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showOceanDropZone()
        }
    }

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The preferences are also persisted, so these work after process recreation.
            AppPreferences.load(this@AssistantButtonService)
            when (intent.action) {
                ACTION_UPDATE_SETTINGS -> updateButtonSide(AppPreferences.isButtonLeft)
                ACTION_TOGGLE_BUTTON -> {
                    if (isConsuming) {
                        isConsuming = false
                        eatAnimator?.cancel()
                        eatAnimator = null
                    }
                    if (AppPreferences.isWakeEnabled) {
                        showFloatingButton()
                    } else {
                        hideFloatingButton()
                        hideOceanDropZone(immediate = true)
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppPreferences.load(this)
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_UPDATE_SETTINGS)
                addAction(ACTION_TOGGLE_BUTTON)
            }
            ContextCompat.registerReceiver(
                this, settingsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        if (AppPreferences.isWakeEnabled) showFloatingButton()
        WakeQuickSettingsTile.requestSync(this)
    }

    private fun showFloatingButton() {
        if (!AppPreferences.isWakeEnabled || floatingButton != null || isConsuming) return

        val button = ImageButton(this).apply {
            setImageResource(R.drawable.ic_search_pencil)
            background = ContextCompat.getDrawable(
                this@AssistantButtonService, R.drawable.bg_floating_button
            )
            contentDescription = getString(R.string.cts_button_description)
            alpha = 0.94f
            elevation = dp(6).toFloat()
            isClickable = true
            isLongClickable = true
            setOnClickListener { handleButtonTap() }
            // Always consume touch events: otherwise the framework and ACTION_UP can
            // each dispatch a click for the same tap.
            setOnTouchListener { view, event -> handleTouch(view, event) }
        }

        val size = dp(BUTTON_SIZE_DP)
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (AppPreferences.isButtonLeft) dp(16) else (width - size - dp(16)).coerceAtLeast(0)
            y = (height / 2 - size / 2).coerceAtLeast(0)
        }

        runCatching { windowManager.addView(button, params) }
            .onSuccess {
                floatingButton = button
                buttonParams = params
                tapCount = 0
            }
            .onFailure { Log.e(TAG, "Could not add the floating button", it) }
    }

    private fun handleButtonTap() {
        if (isConsuming || !AppPreferences.isWakeEnabled) return

        handler.removeCallbacks(pendingTapRunnable)
        val now = SystemClock.uptimeMillis()
        tapCount = if (now - lastTapAt <= MULTI_TAP_WINDOW_MS) tapCount + 1 else 1
        lastTapAt = now

        if (tapCount >= OCEAN_TAP_COUNT) {
            tapCount = 0
            floatingButton?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showOceanDropZone()
            scheduleOceanAutoHide()
            return
        }
        handler.postDelayed(pendingTapRunnable, MULTI_TAP_WINDOW_MS)
    }

    private fun triggerSearch() {
        if (AppPreferences.isHapticEnabled) {
            floatingButton?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val triggered = CtsTrigger.triggerCircleToSearch()
        if (!triggered && AppPreferences.isFallbackEnabled) {
            try {
                startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (error: ActivityNotFoundException) {
                Log.w(TAG, "No Assistant is available", error)
            } catch (error: SecurityException) {
                Log.e(TAG, "Launching Assistant was blocked", error)
            }
        }
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val params = buttonParams ?: return false
        if (isConsuming) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(longPressRunnable)
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                isLongPressed = false
                wasOverOcean = false
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                    isDragging = true
                    tapCount = 0
                    handler.removeCallbacks(pendingTapRunnable)
                    handler.removeCallbacks(longPressRunnable)
                    view.animate().cancel()
                }
                if (isDragging) {
                    val maxX = (resources.displayMetrics.widthPixels - dp(BUTTON_SIZE_DP)).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - dp(BUTTON_SIZE_DP)).coerceAtLeast(0)
                    params.x = (initialX + dx.toInt()).coerceIn(0, maxX)
                    params.y = (initialY + dy.toInt()).coerceIn(0, maxY)
                    updateFloatingPosition(view, params)

                    // Keep ordinary repositioning unobtrusive: only reveal the
                    // water when the button approaches the bottom of the display.
                    if (event.rawY >= resources.displayMetrics.heightPixels - dp(REVEAL_FROM_BOTTOM_DP)) {
                        showOceanDropZone()
                    }
                    oceanView?.let { ocean ->
                        val overOcean = isButtonOverOcean(view, ocean, event.rawY)
                        ocean.setHighlighted(overOcean)
                        if (overOcean && !wasOverOcean && AppPreferences.isHapticEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        wasOverOcean = overOcean
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (isDragging || isLongPressed) {
                    val ocean = oceanView
                    if (ocean != null && isButtonOverOcean(view, ocean, event.rawY)) {
                        consumeButton(view, ocean)
                    } else {
                        if (isDragging) playWaterBounce(view)
                        hideOceanDropZone()
                    }
                } else {
                    view.performClick() // Preserves accessibility click semantics.
                    if (oceanView != null) scheduleOceanAutoHide()
                }
                resetTouchState()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                resetTouchState()
                hideOceanDropZone()
                return true
            }
        }
        return true
    }

    private fun resetTouchState() {
        isDragging = false
        isLongPressed = false
        wasOverOcean = false
    }

    private fun isButtonOverOcean(button: View, ocean: OceanDropView, pointerY: Float): Boolean {
        val buttonLocation = IntArray(2)
        val oceanLocation = IntArray(2)
        button.getLocationOnScreen(buttonLocation)
        ocean.getLocationOnScreen(oceanLocation)
        val buttonBottom = buttonLocation[1] + button.height.coerceAtLeast(dp(BUTTON_SIZE_DP))
        val waterline = oceanLocation[1] + ocean.surfaceThresholdPx()
        return buttonBottom >= waterline || pointerY >= waterline
    }

    private fun showOceanDropZone() {
        if (isConsuming) return
        handler.removeCallbacks(hideOceanRunnable)
        if (oceanView != null) return

        val ocean = OceanDropView(this).apply { alpha = 0f }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(OCEAN_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        runCatching { windowManager.addView(ocean, params) }
            .onSuccess {
                oceanView = ocean
                ocean.startWaves()
                ocean.animate().alpha(1f).setDuration(260).start()
            }
            .onFailure { Log.e(TAG, "Could not add the ocean overlay", it) }
    }

    private fun scheduleOceanAutoHide() {
        handler.removeCallbacks(hideOceanRunnable)
        handler.postDelayed(hideOceanRunnable, OCEAN_IDLE_TIMEOUT_MS)
    }

    private fun hideOceanDropZone(immediate: Boolean = false) {
        handler.removeCallbacks(hideOceanRunnable)
        val ocean = oceanView ?: return
        oceanView = null
        ocean.animate().cancel()
        if (immediate) {
            ocean.stopWaves()
            runCatching { windowManager.removeView(ocean) }
        } else {
            ocean.animate().alpha(0f).setDuration(230).withEndAction {
                ocean.stopWaves()
                runCatching { windowManager.removeView(ocean) }
            }.start()
        }
    }

    private fun consumeButton(button: View, ocean: OceanDropView) {
        if (isConsuming) return
        isConsuming = true
        handler.removeCallbacks(hideOceanRunnable)
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(pendingTapRunnable)
        button.setOnTouchListener { _, _ -> true }
        button.isClickable = false

        val location = IntArray(2)
        button.getLocationOnScreen(location)
        // Start the ring as the floating control reaches the surface.
        handler.postDelayed({ if (isConsuming) ocean.swallowAt(location[0] + button.width / 2) }, 120L)
        if (AppPreferences.isHapticEnabled) {
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        val animation = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, View.SCALE_X, button.scaleX, 0.38f),
                ObjectAnimator.ofFloat(button, View.SCALE_Y, button.scaleY, 0.38f),
                ObjectAnimator.ofFloat(button, View.ALPHA, button.alpha, 0f),
                ObjectAnimator.ofFloat(button, View.TRANSLATION_Y, 0f, dp(66).toFloat())
            )
            duration = 610
            interpolator = AccelerateInterpolator(0.9f)
        }
        animation.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!isConsuming) return
                eatAnimator = null
                // Turning off wakeCTS does not disable Android's Accessibility
                // service; the user can immediately restore it from the app.
                AppPreferences.setWakeEnabled(this@AssistantButtonService, false)
                hideFloatingButton()
                WakeStateController.notifyStateChanged(this@AssistantButtonService)
                handler.postDelayed({
                    hideOceanDropZone()
                    isConsuming = false
                }, 350L)
            }
        })
        eatAnimator = animation
        animation.start()
    }

    private fun playWaterBounce(view: View) {
        view.animate().cancel()
        view.animate()
            .translationY(dp(4).toFloat())
            .scaleX(0.97f).scaleY(1.02f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(95)
            .withEndAction {
                view.animate()
                    .translationY(0f)
                    .scaleX(1f).scaleY(1f)
                    .setDuration(240)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun updateButtonSide(onLeft: Boolean) {
        val params = buttonParams ?: return
        val width = resources.displayMetrics.widthPixels
        params.x = if (onLeft) dp(16) else (width - dp(BUTTON_SIZE_DP) - dp(16)).coerceAtLeast(0)
        floatingButton?.let { updateFloatingPosition(it, params) }
    }

    private fun updateFloatingPosition(view: View, params: WindowManager.LayoutParams) {
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "Could not move floating button", it) }
    }

    private fun hideFloatingButton() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(pendingTapRunnable)
        floatingButton?.let { button ->
            button.animate().cancel()
            runCatching { windowManager.removeView(button) }
        }
        floatingButton = null
        buttonParams = null
        tapCount = 0
        resetTouchState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Recreate the overlay's glass drawable using the newly selected theme.
        floatingButton?.apply {
            background = ContextCompat.getDrawable(this@AssistantButtonService, R.drawable.bg_floating_button)
            setImageResource(R.drawable.ic_search_pencil)
        }
        // On rotation, keep the control onscreen instead of reusing old bounds.
        buttonParams?.let { params ->
            params.x = params.x.coerceIn(0, (resources.displayMetrics.widthPixels - dp(BUTTON_SIZE_DP)).coerceAtLeast(0))
            params.y = params.y.coerceIn(0, (resources.displayMetrics.heightPixels - dp(BUTTON_SIZE_DP)).coerceAtLeast(0))
            floatingButton?.let { updateFloatingPosition(it, params) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isConsuming = false
        eatAnimator?.cancel()
        eatAnimator = null
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            runCatching { unregisterReceiver(settingsReceiver) }
            receiverRegistered = false
        }
        hideOceanDropZone(immediate = true)
        hideFloatingButton()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "wakeCTS"
        private const val BUTTON_SIZE_DP = 56
        private const val OCEAN_HEIGHT_DP = 178
        private const val REVEAL_FROM_BOTTOM_DP = 255
        private const val LONG_PRESS_MS = 450L
        private const val MULTI_TAP_WINDOW_MS = 520L
        private const val OCEAN_TAP_COUNT = 3
        private const val OCEAN_IDLE_TIMEOUT_MS = 4000L

        const val ACTION_UPDATE_SETTINGS = "com.example.ctsbutton.UPDATE_SETTINGS"
        const val ACTION_TOGGLE_BUTTON = "com.example.ctsbutton.TOGGLE_BUTTON"
        const val ACTION_STATE_CHANGED = "com.example.ctsbutton.STATE_CHANGED"
    }
}
