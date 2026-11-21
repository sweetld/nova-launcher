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
 * default. Fires once per gesture after the fingers move together or apart past a threshold,
 * then takes over the rest of the touch stream so the two-finger swipe gestures don't also fire.
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
    private var triggered = false
    private var cumulativeScale = 1f

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
            triggered = false
            cumulativeScale = 1f
        }
        if (!tracking) return false
        detector.onTouchEvent(ev)
        return triggered
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        if (!tracking) return false
        detector.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            tracking = false
        }
        return true
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
