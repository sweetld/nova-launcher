package app.lawnchair.folder

import android.view.MotionEvent
import android.view.ViewConfiguration
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.LawnchairApp
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * "Swipe up to open folders" mode: a tap on a folder launches its first app (lowest rank,
 * i.e. the top-left icon inside the folder); a swipe up on the folder opens it.
 */
object FolderSwipeHelper {

    @JvmStatic
    fun isEnabled(): Boolean = try {
        PreferenceManager2.getInstance(LawnchairApp.instance).folderSwipeToOpen.firstCached()
    } catch (e: Exception) {
        false
    }

    @JvmStatic
    fun firstApp(info: FolderInfo?): WorkspaceItemInfo? =
        info?.getContents()?.filterIsInstance<WorkspaceItemInfo>()?.minByOrNull { it.rank }

    /**
     * Tracks one touch sequence on a folder icon. Returns true once the finger has moved up far
     * enough to count as a swipe; the caller then opens the folder and cancels the click.
     */
    class Tracker(icon: FolderIcon) {
        private val threshold = ViewConfiguration.get(icon.context).scaledTouchSlop * 3f
        private var downY = 0f
        private var downX = 0f
        private var fired = false

        val hasFired get() = fired

        fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    fired = false
                }

                MotionEvent.ACTION_MOVE -> if (!fired) {
                    val dy = downY - ev.y
                    val dx = kotlin.math.abs(ev.x - downX)
                    if (dy > threshold && dy > dx) {
                        fired = true
                        return true
                    }
                }
            }
            return false
        }
    }
}
