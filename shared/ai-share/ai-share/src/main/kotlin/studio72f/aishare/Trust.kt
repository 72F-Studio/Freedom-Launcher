package studio72f.aishare

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Who may ask for a model and who may answer: only 72F's own apps, both ways. A host never runs a stranger's prompt,
 * and a client never sends its prompt (which can be health notes or a CV) to an app pretending to be a 72F one.
 *
 * An app counts as 72F's when its package is on [PACKAGES] and either
 * - it is signed with that app's Play signing key (from Play Console > App signing) or with [TEST_CERTS], or
 * - Google Play installed it: Play only ever installs a package name from the developer account that owns it, which
 *   covers an app whose Play key isn't known yet (Eigen, Tofu Move before their first release).
 */
object Trust {
    const val PLAY_STORE = "com.android.vending"

    /** Package name to its Play app signing certificate SHA-256 (empty while Play hasn't made one yet). */
    val PACKAGES: Map<String, Set<String>> = mapOf(
        "com.studio72f.tofu" to setOf("82:5F:62:DD:E6:3E:12:E7:C0:EF:4C:0C:B5:6A:CE:75:60:28:C9:F9:5F:00:9D:7A:D3:ED:7E:09:F4:36:7E:C6"),
        "com.studio72f.remerit" to setOf("C6:32:2A:0C:23:6B:F5:BE:B6:7E:AE:4C:A4:44:30:DE:5F:B8:27:41:72:63:97:9E:5B:17:5B:31:13:79:FE:FF"),
        "com.studio72f.hypermind" to setOf("4C:30:8E:C5:04:03:F6:D9:66:F3:94:11:03:2F:18:14:90:B7:86:7B:58:71:C6:C3:0E:6B:DA:F9:B5:24:11:29"),
        "com.studio72f.mail" to setOf("EA:A4:C4:14:75:3E:68:EC:41:6B:17:E0:04:B8:C3:5D:32:A2:83:DB:A3:BA:2B:DF:7A:2C:58:8F:E5:82:30:E7"),
        "studio.seventytwo.gallery" to setOf("C9:E0:06:5B:5B:77:52:77:6F:51:51:57:7C:4E:EF:04:E4:F9:3A:21:10:5F:88:7C:52:D0:AA:B8:90:A3:C4:01"),
        "com.studio72f.eigen" to emptySet(),
        "com.studio72f.tofu.move" to emptySet(),
    )

    /**
     * The 72F build machine's own debug key. Release bundles installed on the test phone with bundletool are re-signed
     * with it, so the apps recognise each other there too. It never leaves that machine, so nobody else can sign with it.
     */
    val TEST_CERTS: Set<String> = setOf("8C:5C:DB:0D:9A:88:2B:98:A6:72:F7:A1:B6:AB:F4:7B:FF:4D:6C:2F:A5:A2:99:12:30:6A:E7:F0:E5:B3:4E:81")

    /** The rule itself, on plain values so it is tested without a phone. */
    fun decide(packageName: String, certs: Set<String>, installedBy: String?): Boolean {
        val known = PACKAGES[packageName] ?: return false
        return certs.any { it in known || it in TEST_CERTS } || installedBy == PLAY_STORE
    }

    /** Whether the installed [packageName] is one of 72F's apps. False when it isn't installed. */
    fun isTrusted(context: Context, packageName: String): Boolean = runCatching {
        if (packageName !in PACKAGES) return false
        decide(packageName, certsOf(context.packageManager, packageName), installedBy(context.packageManager, packageName))
    }.getOrDefault(false)

    /** Whether the app behind a binder call is one of 72F's. Shared user IDs are not used by 72F, so one package is expected. */
    fun isTrustedUid(context: Context, uid: Int): Boolean =
        context.packageManager.getPackagesForUid(uid)?.any { isTrusted(context, it) } == true

    fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0); true }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun certsOf(pm: PackageManager, packageName: String): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return emptySet()
            // The history covers a rotated key; several signers is a multi-signed APK, where every signer is listed.
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: return emptySet()
        return signatures.map { fingerprint(it.toByteArray()) }.toSet()
    }

    private fun installedBy(pm: PackageManager, packageName: String): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // The initiating package is who really started the install; `adb install -i` can't fake it.
            runCatching { pm.getInstallSourceInfo(packageName).initiatingPackageName }.getOrNull()
        } else null

    fun fingerprint(der: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(der).joinToString(":") { "%02X".format(it) }
}
