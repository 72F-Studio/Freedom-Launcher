package studio72f.kelvin

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun KelvinWindowGlass(): Boolean {
    val view = LocalView.current
    val params = Kelvin.glass
    val density = LocalDensity.current
    val blur = kelvinCrossWindowBlur()
    val radius = with(density) { (params.frostDp * 1.6f + 8f).dp.roundToPx() }
    DisposableEffect(view, blur, radius) {
        val window = view.dialogWindow()
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blur) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.also { it.blurBehindRadius = radius }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
        onDispose { }
    }
    return blur
}

private fun View.dialogWindow(): android.view.Window? {
    var v: Any? = this
    while (v is View) {
        if (v is DialogWindowProvider) return v.window
        v = v.parent
    }
    return null
}

@Composable
actual fun kelvinCrossWindowBlur(): Boolean {
    val context = LocalContext.current
    return !Kelvin.glass.isSolid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        runCatching { context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled }.getOrDefault(false)
}
