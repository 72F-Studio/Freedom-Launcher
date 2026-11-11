package com.studio72f.eigen.ui

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Unlocks private apps with the phone's own screen lock: fingerprint or face where set up, else the PIN, pattern or
 * password. Eigen never sees the credential; Android checks it and says yes or no.
 */
object PrivateLock {
    const val CONFIRM_REQUEST = 70

    /** Asks for the screen lock. [onOk] runs on success; failures other than the person cancelling become problems. */
    @JvmStatic
    fun unlock(activity: Activity, state: EigenState, onOk: Runnable) {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            Problems.show(state, activity, Problems.PRV_NO_LOCK, "private:no-lock", "Set a screen lock first",
                "Private apps are protected by your phone's screen lock. Add a PIN, pattern or password in Android's security settings, then try again.",
                "This phone has no secure screen lock, so there's nothing to check before showing private apps.")
            return
        }
        val builder = BiometricPrompt.Builder(activity).setTitle("Private apps").setSubtitle("Unlock to see them")
        when {
            Build.VERSION.SDK_INT >= 30 -> builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            Build.VERSION.SDK_INT == 29 -> @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
            else -> builder.setNegativeButton("Use screen lock", activity.mainExecutor) { _, _ -> confirmWithLock(activity, keyguard) }
        }
        try {
            builder.build().authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk.run()
                override fun onAuthenticationError(code: Int, message: CharSequence?) {
                    when (code) {
                        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, BiometricPrompt.BIOMETRIC_ERROR_CANCELED -> Unit
                        // Android 9 without fingerprint: go straight to the PIN screen.
                        BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT, BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE, BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ->
                            if (Build.VERSION.SDK_INT < 29) confirmWithLock(activity, keyguard) else fail(activity, state, code)
                        else -> fail(activity, state, code)
                    }
                }
            })
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT < 29) confirmWithLock(activity, keyguard)
            else Problems.show(state, activity, Problems.PRV_AUTH, "private:prompt", "Screen lock didn't open",
                "Try again. If it keeps happening, restart the phone.", "Android's biometric prompt failed to start.", e)
        }
    }

    private fun confirmWithLock(activity: Activity, keyguard: KeyguardManager) {
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent("Private apps", "Unlock to see them") ?: return
        activity.startActivityForResult(intent, CONFIRM_REQUEST)
    }

    private fun fail(activity: Activity, state: EigenState, code: Int) {
        Problems.show(state, Problems.of(activity, Problems.PRV_AUTH, "private:authenticate", "Private apps stayed locked",
            if (code == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT || code == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT)
                "Too many tries. Wait a little, or unlock the phone with your PIN first, then try again."
            else "Android couldn't check your screen lock just now. Try again.",
            "Android's biometric prompt ended with an error instead of a yes or a cancel.",
            lines = listOf("Biometric error: $code")))
    }
}
