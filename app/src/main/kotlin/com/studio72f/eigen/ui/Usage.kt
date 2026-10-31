package com.studio72f.eigen.ui

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.UserHandle
import java.util.Calendar

/**
 * Screen time from Android's own usage stats, read only after the person grants usage access. Nothing is stored
 * or sent; it is read fresh when home resumes or a dashboard opens.
 */
object Usage {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Whole minutes in the foreground since midnight, by package. */
    fun todayMinutes(context: Context): Map<String, Int> {
        if (!hasAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        return runCatching {
            usm.queryAndAggregateUsageStats(startOfDay(), System.currentTimeMillis())
                .mapValues { (it.value.totalTimeInForeground / 60_000L).toInt() }
                .filterValues { it > 0 }
        }.getOrDefault(emptyMap())
    }

    /** Minutes in the foreground in each hour of today (24 values) for one package. */
    fun hourly(context: Context, pkg: String): IntArray {
        val out = IntArray(24)
        if (!hasAccess(context)) return out
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return out
        val start = startOfDay()
        val events = runCatching { usm.queryEvents(start, System.currentTimeMillis()) }.getOrNull() ?: return out
        val e = UsageEvents.Event()
        var resumedAt = -1L
        fun add(from: Long, to: Long) {
            var t = from
            while (t < to) {
                val hour = ((t - start) / 3_600_000L).toInt().coerceIn(0, 23)
                val hourEnd = start + (hour + 1) * 3_600_000L
                val end = minOf(to, hourEnd)
                out[hour] += ((end - t) / 60_000L).toInt()
                t = end
            }
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName != pkg) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> if (resumedAt >= 0) { add(resumedAt, e.timeStamp); resumedAt = -1 }
            }
        }
        if (resumedAt >= 0) add(resumedAt, System.currentTimeMillis())
        return out
    }

    /** App, data and cache bytes on the device, or null without usage access. */
    fun storageBytes(context: Context, pkg: String, user: UserHandle): Long? {
        if (!hasAccess(context)) return null
        val ssm = context.getSystemService(StorageStatsManager::class.java) ?: return null
        return runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            val stats = ssm.queryStatsForPackage(info.storageUuid, pkg, user)
            stats.appBytes + stats.dataBytes + stats.cacheBytes
        }.getOrNull()
    }

    @Suppress("DEPRECATION") // the flags overload is API 33+
    fun version(context: Context, pkg: String): String? = runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()

    fun formatMinutes(min: Int): String = if (min < 60) "${min}min" else "${min / 60}h ${min % 60}min"

    fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(java.util.Locale.getDefault(), "%.1f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(java.util.Locale.getDefault(), "%.1f MB", b / (1L shl 20).toDouble())
        else -> "${b / 1024} KB"
    }
}
