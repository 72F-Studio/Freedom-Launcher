package com.studio72f.eigen.ui

import androidx.core.net.toUri
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserManager
import android.provider.ContactsContract
import android.provider.Settings
import com.studio72f.eigen.CategoryRules
import com.studio72f.eigen.QuickMath

/**
 * Everything search finds besides apps, all on the phone: contacts (with permission), app shortcuts, Android
 * settings pages and sums or unit conversions. Web, Maps and Play searches hand the words to the app that handles
 * them; Eigen itself never sends a search anywhere.
 */
object SearchProviders {
    data class SettingPage(val name: String, val action: String, val words: String)

    sealed class Hit(val key: String) {
        class Contact(val name: String, val lookup: Uri, val photo: Uri?) : Hit("contact:$lookup")
        class Shortcut(val hit: ShortcutHit) : Hit("shortcut:${hit.pkg}/${hit.id}/${hit.user.hashCode()}")
        class Setting(val page: SettingPage) : Hit("setting:${page.action}")
        class Answer(val text: String) : Hit("answer")
        class Handoff(val label: String, val intent: Intent) : Hit("handoff:$label")
    }

    val SETTINGS = listOf(
        SettingPage("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, "wifi wi-fi wireless network internet"),
        SettingPage("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "bluetooth pair headphones earbuds"),
        SettingPage("Mobile data", Settings.ACTION_DATA_ROAMING_SETTINGS, "mobile data cellular sim roaming"),
        SettingPage("Hotspot and tethering", "android.settings.TETHER_SETTINGS", "hotspot tether tethering share internet"),
        SettingPage("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, "airplane aeroplane flight mode"),
        SettingPage("VPN", Settings.ACTION_VPN_SETTINGS, "vpn private network"),
        SettingPage("Display", Settings.ACTION_DISPLAY_SETTINGS, "display brightness screen timeout dark theme font size"),
        SettingPage("Night light", "android.settings.NIGHT_DISPLAY_SETTINGS", "night light blue light eye comfort"),
        SettingPage("Sound and vibration", Settings.ACTION_SOUND_SETTINGS, "sound volume ringtone vibration"),
        SettingPage("Do Not Disturb", "android.settings.ZEN_MODE_SETTINGS", "do not disturb dnd silence quiet focus"),
        SettingPage("Notifications", "android.settings.NOTIFICATION_SETTINGS", "notifications alerts"),
        SettingPage("Battery", Intent.ACTION_POWER_USAGE_SUMMARY, "battery power saver usage"),
        SettingPage("Battery saver", Settings.ACTION_BATTERY_SAVER_SETTINGS, "battery saver power saving"),
        SettingPage("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "storage space free up memory"),
        SettingPage("Apps", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, "apps applications manage"),
        SettingPage("Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, "default apps browser home launcher"),
        SettingPage("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "location gps"),
        SettingPage("Security", Settings.ACTION_SECURITY_SETTINGS, "security screen lock fingerprint face unlock"),
        SettingPage("Privacy", Settings.ACTION_PRIVACY_SETTINGS, "privacy permissions"),
        SettingPage("Accounts", Settings.ACTION_SYNC_SETTINGS, "accounts sync google"),
        SettingPage("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, "accessibility talkback magnification"),
        SettingPage("Languages and input", Settings.ACTION_LOCALE_SETTINGS, "language languages locale keyboard"),
        SettingPage("Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, "keyboard input method gboard"),
        SettingPage("Date and time", Settings.ACTION_DATE_SETTINGS, "date time timezone clock"),
        SettingPage("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "developer options usb debugging adb animation scale"),
        SettingPage("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, "about phone model build number android version"),
        SettingPage("NFC", Settings.ACTION_NFC_SETTINGS, "nfc tap pay contactless"),
        SettingPage("Cast", Settings.ACTION_CAST_SETTINGS, "cast screen mirror chromecast"),
        SettingPage("Wallpaper", Intent.ACTION_SET_WALLPAPER, "wallpaper background"),
        SettingPage("Usage access", Settings.ACTION_USAGE_ACCESS_SETTINGS, "usage access screen time"),
        SettingPage("Notification access", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, "notification access listener"),
        SettingPage("All settings", Settings.ACTION_SETTINGS, "settings preferences"),
    )

    fun settings(q: String): List<Hit> {
        val n = CategoryRules.normalized(q)
        if (n.length < 2) return emptyList()
        return SETTINGS.filter { CategoryRules.normalized(it.name).contains(n) || it.words.split(' ').any { w -> w.startsWith(n) } }
            .sortedBy { if (CategoryRules.normalized(it.name).startsWith(n)) 0 else 1 }.take(3).map { Hit.Setting(it) }
    }

    fun shortcuts(state: EigenState, q: String): List<Hit> {
        val n = CategoryRules.normalized(q)
        if (n.length < 2) return emptyList()
        return state.shortcutIndex.filter { CategoryRules.normalized(it.label).contains(n) && it.appId !in state.privateIds }
            .sortedBy { if (CategoryRules.normalized(it.label).startsWith(n)) 0 else 1 }.take(4).map { Hit.Shortcut(it) }
    }

    fun answer(q: String): List<Hit> = QuickMath.answer(q)?.let { listOf(Hit.Answer(it)) } ?: emptyList()

    /** Contacts whose name matches; needs READ_CONTACTS. Throws on a provider failure so search can say so. */
    fun contacts(context: Context, q: String): List<Hit> {
        if (q.length < 2 || context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
        val out = ArrayList<Hit>()
        context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI), null, null, null)?.use { c ->
            while (c.moveToNext() && out.size < 4) {
                val lookup = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)) ?: continue
                out.add(Hit.Contact(c.getString(2) ?: continue, lookup, c.getString(3)?.let(Uri::parse)))
            }
        }
        return out
    }

    fun handoffs(q: String): List<Hit> = listOf(
        Hit.Handoff("Search the web", Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)),
        Hit.Handoff("Find in Maps", Intent(Intent.ACTION_VIEW, ("geo:0,0?q=" + Uri.encode(q)).toUri())),
        Hit.Handoff("Search Play Store", Intent(Intent.ACTION_VIEW, ("market://search?q=" + Uri.encode(q)).toUri())),
    )

    /**
     * Every enabled shortcut of every app (dynamic, manifest and pinned), across profiles. Only the default home
     * app may read them; otherwise empty. Runs off the main thread.
     */
    fun indexShortcuts(context: Context, apps: List<com.studio72f.eigen.MainActivity.App>): List<ShortcutHit> {
        val launcher = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        if (runCatching { launcher.hasShortcutHostPermission() }.getOrDefault(false).not()) return emptyList()
        val users = context.getSystemService(UserManager::class.java)?.userProfiles ?: return emptyList()
        val out = ArrayList<ShortcutHit>()
        for (user in users) {
            val query = LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
            val found = runCatching { launcher.getShortcuts(query, user) }.getOrNull() ?: continue // a locked work profile
            for (s in found) {
                if (!s.isEnabled) continue
                val label = (s.shortLabel ?: s.longLabel)?.toString() ?: continue
                val app = apps.firstOrNull { it.user == user && it.component.packageName == s.`package` } ?: continue
                out.add(ShortcutHit(s.`package`, s.id, label, app.title(), user, app.id))
            }
            if (out.size > 600) break
        }
        return out
    }

    fun open(context: Context, state: EigenState, intent: Intent, what: String) {
        try {
            context.startActivity(intent)
            state.searching = false; state.query = ""
        } catch (e: Exception) {
            Problems.show(state, context, Problems.SRC_HANDOFF, "search:handoff", "No app for $what",
                "Nothing on this phone can take that search. Install a browser, maps or store app, or turn this result off in Settings → Search.",
                "Android found no app that handles this kind of request, so Eigen had nowhere to hand your words.", e)
        }
    }
}
