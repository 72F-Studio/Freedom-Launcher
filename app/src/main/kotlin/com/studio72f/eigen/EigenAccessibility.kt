package com.studio72f.eigen

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Optional. Lets a gesture on home lock the screen, pull down notifications or quick settings, or show recent apps,
 * through Android's global actions. It watches no windows and reads no screen content (no event types, no
 * window retrieval in res/xml/eigen_accessibility.xml); it only performs the action a gesture asks for.
 */
class EigenAccessibility : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile private var instance: EigenAccessibility? = null

        const val LOCK = AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
        const val NOTIFICATIONS = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        const val QUICK_SETTINGS = AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        const val RECENTS = AccessibilityService.GLOBAL_ACTION_RECENTS

        /** True when the service is switched on in Android's accessibility settings. */
        @JvmStatic fun enabled(context: Context): Boolean {
            val on = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, EigenAccessibility::class.java).flattenToString()
            return on.split(':').any { it.equals(me, ignoreCase = true) }
        }

        /** Performs [action]; false when the service isn't running or Android refused. */
        @JvmStatic fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }
}
