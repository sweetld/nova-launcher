package app.lawnchair.gestures

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.util.TouchController
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Pinch in / pinch out on the home screen.
 *
 * Does nothing unless at least one pinch action is set, so stock behaviour is unchanged by
 * default.
 *
 * The workspace pager blocks parent interception as soon as the fingers move sideways past touch
 * slop, so waiting for the pinch to grow before claiming the touch never works. Instead this
 * controller claims the gesture the moment a second finger lands. If the gesture ends without a
 * pinch but the fingers travelled mostly vertically, it runs the two-finger swipe actions itself,
 * so those keep working while pinch is enabled.
 */
class PinchTouchController(
    private val launcher: LawnchairLauncher,
    private val gestureController: GestureController,
) : TouchController,
    ScaleGestureDetector.SimpleOnScaleGestureListener() {

    private val prefs = PreferenceManager2.getInstance(launcher)
    private val detector = ScaleGestureDetector(launcher, this).apply {
        // Quick scale is a one-finger double-tap-and-drag gesture; it would clash with double tap.
        isQuickScaleEnabled = false
    }

    private var pinchInEnabled = false
    private var pinchOutEnabled = false

    private var tracking = false
    private var claimed = false
    private var triggered = false
    private var cumulativeScale = 1f
    private var startFocusX = 0f
    private var startFocusY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private val swipeThreshold =
        android.view.ViewConfiguration.get(launcher).scaledTouchSlop * 4f

    init {
        launcher.lifecycleScope.launch {
            prefs.pinchInGestureHandler.get()
                .onEach { pinchInEnabled = it !is GestureHandlerConfig.NoOp }
                .launchIn(this)
            prefs.pinchOutGestureHandler.get()
                .onEach { pinchOutEnabled = it !is GestureHandlerConfig.NoOp }
                .launchIn(this)
        }
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            tracking = (pinchInEnabled || pinchOutEnabled) && canIntercept(ev)
            claimed = false
            triggered = false
            cumulativeScale = 1f
        }
        if (!tracking) return false
        detector.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN && ev.pointerCount == 2) {
            // Claim now, before the pager locks us out. The workspace gets ACTION_CANCEL.
            claimed = true
            startFocusX = focusX(ev)
            startFocusY = focusY(ev)
            lastFocusX = startFocusX
            lastFocusY = startFocusY
            return true
        }
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        if (!tracking) return false
        detector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (ev.pointerCount >= 2) {
                lastFocusX = focusX(ev)
                lastFocusY = focusY(ev)
            }

            MotionEvent.ACTION_UP -> {
                if (claimed && !triggered) handleTwoFingerSwipe()
                tracking = false
            }

            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }

    /** No pinch happened: treat a mostly vertical two-finger drag as a two-finger swipe. */
    private fun handleTwoFingerSwipe() {
        // Uses the two-finger midpoint from the last move; at ACTION_UP only one finger is left.
        val dy = lastFocusY - startFocusY
        val dx = lastFocusX - startFocusX
        if (kotlin.math.abs(dy) < swipeThreshold || kotlin.math.abs(dy) < kotlin.math.abs(dx)) return
        if (dy < 0) gestureController.onTwoFingerSwipeUp() else gestureController.onTwoFingerSwipeDown()
    }

    private fun focusX(ev: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until ev.pointerCount) sum += ev.getX(i)
        return sum / ev.pointerCount
    }

    private fun focusY(ev: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until ev.pointerCount) sum += ev.getY(i)
        return sum / ev.pointerCount
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        if (triggered) return true
        cumulativeScale *= detector.scaleFactor
        when {
            pinchInEnabled && cumulativeScale < PINCH_IN_THRESHOLD -> {
                triggered = true
                gestureController.onPinchIn()
            }

            pinchOutEnabled && cumulativeScale > PINCH_OUT_THRESHOLD -> {
                triggered = true
                gestureController.onPinchOut()
            }
        }
        return true
    }

    private fun canIntercept(ev: MotionEvent): Boolean {
        if ((ev.edgeFlags and Utilities.EDGE_NAV_BAR) != 0) return false
        return AbstractFloatingView.getTopOpenView(launcher) == null &&
            launcher.isInState(LauncherState.NORMAL)
    }

    override fun dump() = "PinchTouchController(tracking=$tracking, triggered=$triggered)"

    companion object {
        private const val PINCH_IN_THRESHOLD = 0.75f
        private const val PINCH_OUT_THRESHOLD = 1.35f
    }
}
