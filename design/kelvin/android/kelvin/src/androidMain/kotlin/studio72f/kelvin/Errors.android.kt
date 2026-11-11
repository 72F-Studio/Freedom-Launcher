package studio72f.kelvin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

internal actual fun kelvinSystemLine(): String =
    "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"

/**
 * Opens the person's own mail app with an error's report filled in, for [KelvinErrorPanel]'s logs button.
 * Nothing is sent until they send it. False when the phone has no mail app; the report stays on screen under
 * View more.
 */
fun Context.kelvinEmailLogs(address: String, subject: String, body: String): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        if (this@kelvinEmailLogs !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** The app's name and version for an error's technical detail ("Rediom 2.4.1"). */
fun Context.kelvinAppLine(): String {
    val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
    val name = runCatching { applicationInfo.loadLabel(packageManager).toString() }.getOrNull() ?: packageName
    return "$name ${version ?: "unknown version"}"
}
