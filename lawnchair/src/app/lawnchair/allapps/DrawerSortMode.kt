package app.lawnchair.allapps

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import java.util.concurrent.TimeUnit

/**
 * Order of apps in the app drawer.
 *
 * MOST_USED needs "Usage access" (Settings → Apps → Special access → Usage data access).
 * Without it, usage is unknown and the drawer falls back to A–Z.
 */
enum class DrawerSortMode(@StringRes val labelResId: Int) {
    ALPHABETICAL(R.string.drawer_sort_alphabetical),
    MOST_USED(R.string.drawer_sort_most_used),
    NEWEST_FIRST(R.string.drawer_sort_newest),
    ;

    companion object {
        fun fromString(value: String) = entries.firstOrNull { it.name == value } ?: ALPHABETICAL

        fun listEntries(): List<ListPreferenceEntry<DrawerSortMode>> = entries.map {
            ListPreferenceEntry(value = it) { stringResource(id = it.labelResId) }
        }
    }
}

/**
 * Sorts drawer apps for the non-alphabetical modes. The input list arrives already in A–Z
 * order, and Kotlin's sort is stable, so ties keep A–Z order.
 */
class DrawerAppSorter(context: Context) {

    private val appContext = context.applicationContext
    private var usageCache: Map<String, Long> = emptyMap()
    private var usageCacheTime = 0L
    private val installTimeCache = HashMap<String, Long>()

    fun sort(apps: List<AppInfo>, mode: DrawerSortMode): List<AppInfo> = when (mode) {
        DrawerSortMode.ALPHABETICAL -> apps
        DrawerSortMode.MOST_USED -> {
            val usage = usage()
            if (usage.isEmpty()) apps else apps.sortedByDescending { usage[it.targetPackage] ?: 0L }
        }
        DrawerSortMode.NEWEST_FIRST -> apps.sortedByDescending { installTime(it.targetPackage) }
    }

    /** Foreground time per package over the last 30 days, cached for 10 minutes. */
    private fun usage(): Map<String, Long> {
        val now = SystemClock.elapsedRealtime()
        if (usageCacheTime != 0L && now - usageCacheTime < CACHE_MS) return usageCache
        usageCache = try {
            val usm = appContext.getSystemService(UsageStatsManager::class.java)
            val end = System.currentTimeMillis()
            usm?.queryAndAggregateUsageStats(end - TimeUnit.DAYS.toMillis(30), end)
                ?.mapValues { it.value.totalTimeInForeground }
                ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Usage stats unavailable", e)
            emptyMap()
        }
        usageCacheTime = now
        return usageCache
    }

    private fun installTime(pkg: String?): Long {
        if (pkg == null) return 0L
        return installTimeCache.getOrPut(pkg) {
            try {
                appContext.packageManager.getPackageInfo(pkg, 0).firstInstallTime
            } catch (e: PackageManager.NameNotFoundException) {
                0L
            }
        }
    }

    /** Drop cached install times, e.g. after an app is reinstalled. */
    fun invalidate() {
        installTimeCache.clear()
        usageCacheTime = 0L
    }

    companion object {
        private const val TAG = "DrawerAppSorter"
        private val CACHE_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
