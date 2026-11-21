package app.lawnchair.animation

import android.view.View
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R
import kotlin.math.abs

/**
 * Page transition effects for the home screen.
 *
 * Effects are applied to each page's icon/widget container, not the CellLayout, so they never
 * fight the workspace state animations (overview, spring-loaded drag), which scale the
 * Workspace and only animate the container's alpha.
 *
 * `progress` is Launcher3's scroll progress for a page: 0 = centred, +1 = one page to the left,
 * -1 = one page to the right.
 */
enum class WorkspaceScrollEffect(@StringRes val labelResId: Int) {
    NONE(R.string.scroll_effect_none),
    CUBE(R.string.scroll_effect_cube),
    CARD_STACK(R.string.scroll_effect_card_stack),
    ZOOM(R.string.scroll_effect_zoom),
    ROTATE(R.string.scroll_effect_rotate),
    FADE(R.string.scroll_effect_fade),
    ;

    /** True if the effect sets alpha itself, so the stock fade must not run afterwards. */
    val ownsAlpha get() = this == CARD_STACK || this == FADE

    companion object {
        private const val CAMERA_DISTANCE = 12000f
        private const val CUBE_MAX_DEGREES = 90f
        private const val ROTATE_MAX_DEGREES = 25f

        fun fromString(value: String) = entries.firstOrNull { it.name == value } ?: NONE

        fun listEntries(): List<ListPreferenceEntry<WorkspaceScrollEffect>> = entries.map {
            ListPreferenceEntry(value = it) { stringResource(id = it.labelResId) }
        }

        /**
         * @param page the page's ShortcutsAndWidgets container
         * @param pageDistance horizontal distance between two adjacent pages, in px
         * @param stockFadeActive true if Launcher3's own adjacent-page fade already set alpha
         */
        @JvmStatic
        fun apply(
            effect: WorkspaceScrollEffect,
            page: View,
            rawProgress: Float,
            pageDistance: Float,
            stockFadeActive: Boolean,
        ) {
            val p = rawProgress.coerceIn(-1f, 1f)
            val w = page.width.toFloat()
            val h = page.height.toFloat()
            reset(page, keepAlpha = stockFadeActive && !effect.ownsAlpha)
            page.pivotX = w / 2f
            page.pivotY = h / 2f
            if (p == 0f || w == 0f) return
            val d = abs(p)

            when (effect) {
                NONE -> Unit

                CUBE -> {
                    page.cameraDistance = CAMERA_DISTANCE * page.resources.displayMetrics.density
                    page.pivotX = if (p > 0) w else 0f
                    page.rotationY = -CUBE_MAX_DEGREES * p
                }

                CARD_STACK -> {
                    if (p < 0) {
                        // Incoming page from the right waits underneath, growing into place.
                        page.translationX = p * pageDistance
                        val s = 1f - 0.2f * d
                        page.scaleX = s
                        page.scaleY = s
                        page.alpha = 1f - d
                    } else {
                        // Outgoing page slides away normally.
                        page.alpha = 1f
                    }
                }

                ZOOM -> {
                    val s = 1f - 0.3f * d
                    page.scaleX = s
                    page.scaleY = s
                }

                ROTATE -> {
                    page.pivotY = h * 1.5f
                    page.rotation = -ROTATE_MAX_DEGREES * p
                }

                FADE -> page.alpha = 1f - d
            }
        }

        @JvmStatic
        fun reset(page: View, keepAlpha: Boolean) {
            page.rotation = 0f
            page.rotationY = 0f
            page.scaleX = 1f
            page.scaleY = 1f
            page.translationX = 0f
            if (!keepAlpha) page.alpha = 1f
        }
    }
}
