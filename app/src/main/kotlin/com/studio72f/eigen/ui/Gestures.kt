package com.studio72f.eigen.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.studio72f.eigen.EigenAccessibility

/**
 * Gestures on home and what each one does. A gesture maps to an action id; "app:<app id>" opens that app (Plus).
 * System actions (lock, notifications, quick settings, recents) go through [EigenAccessibility], which the person
 * turns on once after reading what it can and can't do.
 */
object Gestures {
    data class Gesture(val id: String, val name: String, val default: String)
    data class Action(val id: String, val name: String, val system: Boolean = false)

    val ALL = listOf(
        Gesture("double_tap", "Double-tap", "lock"),
        Gesture("swipe_down", "Swipe down", "drawers"),
        Gesture("two_down", "Two-finger swipe down", "notifications"),
        Gesture("two_up", "Two-finger swipe up", "none"),
        Gesture("home_again", "Home, when already home", "none"),
        Gesture("capsule_hold", "Hold the search capsule", "assistant"),
    )

    val ACTIONS = listOf(
        Action("none", "Nothing"),
        Action("drawers", "Open or close all drawers"),
        Action("app_drawer", "All apps"),
        Action("search", "Search"),
        Action("assistant", "Assistant"),
        Action("conversations", "Conversations"),
        Action("widgets", "Widgets page"),
        Action("lock", "Lock the screen", system = true),
        Action("notifications", "Notifications", system = true),
        Action("quick_settings", "Quick settings", system = true),
        Action("recents", "Recent apps", system = true),
        Action("torch", "Flashlight"),
        Action("camera", "Camera"),
        Action("private", "Private apps"),
        Action("settings", "Eigen settings"),
    )

    fun actionName(state: EigenState, id: String): String {
        if (id.startsWith("app:")) return state.allApps.firstOrNull { it.id == id.removePrefix("app:") }?.title() ?: "An app that's gone"
        return ACTIONS.firstOrNull { it.id == id }?.name ?: "Nothing"
    }

    fun actionFor(state: EigenState, gesture: String): String = state.gestures[gesture] ?: ALL.first { it.id == gesture }.default

    /** Runs whatever [gesture] is set to. */
    fun perform(activity: Activity, state: EigenState, host: EigenHost, gesture: String) = run(activity, state, host, actionFor(state, gesture))

    fun run(activity: Activity, state: EigenState, host: EigenHost, action: String) {
        when (action) {
            "none" -> Unit
            "drawers" -> {
                val names = state.drawers.map { it.name }
                state.openDrawers = if (state.openDrawers.containsAll(names)) emptySet() else names.toSet()
            }
            "app_drawer" -> state.appDrawerOpen = true
            "search" -> state.searching = true
            "assistant" -> state.openAssistant(null)
            "conversations" -> { state.leftTab = EigenState.TAB_CONVERSATIONS; state.showPage(EigenState.PAGE_LEFT) }
            "widgets" -> state.showPage(EigenState.PAGE_WIDGETS)
            "lock" -> system(activity, state, EigenAccessibility.LOCK, "Lock the screen")
            "notifications" -> system(activity, state, EigenAccessibility.NOTIFICATIONS, "Notifications")
            "quick_settings" -> system(activity, state, EigenAccessibility.QUICK_SETTINGS, "Quick settings")
            "recents" -> system(activity, state, EigenAccessibility.RECENTS, "Recent apps")
            "torch" -> torch(activity, state)
            "camera" -> try {
                activity.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            } catch (e: Exception) {
                Problems.show(state, activity, Problems.GES_APP, "gesture:camera", "Camera didn't open",
                    "No camera app answered. Set this gesture to open your camera app directly instead.",
                    "Android found no app that handles the still-camera shortcut.", e)
            }
            "private" -> host.openPrivateApps()
            "settings" -> host.openSettings()
            else -> if (action.startsWith("app:")) {
                val app = state.allApps.firstOrNull { it.id == action.removePrefix("app:") }
                if (app != null) host.launch(app)
                else Problems.show(state, activity, Problems.GES_APP, "gesture:app", "That app is gone",
                    "The app this gesture opened was uninstalled or its profile is paused. Choose another action in Settings → Gestures.",
                    "Eigen looked for the app by its id and found no match among the apps on this phone.")
            }
        }
    }

    private fun system(activity: Activity, state: EigenState, action: Int, name: String) {
        if (!EigenAccessibility.enabled(activity)) { state.accessibilityAsk = name; return }
        if (!EigenAccessibility.perform(action)) Problems.show(state, activity, Problems.GES_ACTION, "gesture:global-action", "$name didn't work",
            "Android didn't run the action. Turn Eigen's gesture service off and on again in Accessibility settings, then try once more.",
            "The accessibility service is switched on but wasn't connected, or Android refused global action $action.")
    }

    private var torchOn = false

    private fun torch(context: Context, state: EigenState) {
        val camera = context.getSystemService(CameraManager::class.java)
        try {
            val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?: throw IllegalStateException("no camera with a flash unit")
            torchOn = !torchOn
            camera.setTorchMode(id, torchOn)
        } catch (e: Exception) {
            torchOn = false
            Problems.show(state, context, Problems.GES_TORCH, "gesture:torch", "Flashlight didn't turn on",
                "Close any app using the camera and try again. Some phones have no flash at all.",
                "Android's camera service refused to switch the torch, usually because another app holds the camera.", e)
        }
    }
}

/**
 * Two-finger vertical swipes, seen before anything else handles the touch. One-finger touches pass straight
 * through, so scrolling and drawer pulls are unaffected.
 */
fun Modifier.twoFingerSwipes(onDown: () -> Unit, onUp: () -> Unit): Modifier = pointerInput(Unit) {
    val threshold = 72.dp.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var startY = Float.NaN
        var fired = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.filter { it.pressed }
            if (down.isEmpty()) break
            if (down.size >= 2) {
                val y = down.take(2).map { it.position.y }.average().toFloat()
                if (startY.isNaN()) startY = y
                val dy = y - startY
                if (!fired && kotlin.math.abs(dy) > threshold) { fired = true; if (dy > 0) onDown() else onUp() }
                // Two fingers belong to this gesture: nothing underneath scrolls with them.
                event.changes.forEach { it.consume() }
            }
        }
    }
}
