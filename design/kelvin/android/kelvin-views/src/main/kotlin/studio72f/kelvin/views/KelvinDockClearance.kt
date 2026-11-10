package studio72f.kelvin.views

import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** The floating dock's height (64dp) with its air above and below (12dp each). */
const val KELVIN_DOCK_ROOM_DP = 88

/**
 * Lets a scrolling View run beneath Kelvin's floating dock (GUIDELINES.md, Chrome stays put): content scrolls under the
 * dock, and the last item comes to rest [extraDp] above it rather than behind it. Keeps the navigation bar in the sum.
 */
fun ViewGroup.clearKelvinDock(extraDp: Int = 16) {
    clipToPadding = false
    val room = ((KELVIN_DOCK_ROOM_DP + extraDp) * resources.displayMetrics.density).toInt()
    updatePadding(bottom = room)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        view.updatePadding(bottom = room + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
        insets
    }
}
