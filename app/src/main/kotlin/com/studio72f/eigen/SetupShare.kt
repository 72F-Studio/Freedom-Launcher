package com.studio72f.eigen

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A setup to share: the look, gestures, drawers and contexts as one small JSON file (.eigen). Unlike a backup it
 * never holds the wallpaper photo, notes, app widgets, labels, hidden or private apps, so it is safe to post.
 * Apps are named by component only (no profile), and ones missing on the importing phone are skipped.
 */
object SetupShare {
    const val FORMAT = "eigen.setup"
    const val VERSION = 1
    private const val MAX_BYTES = 256 * 1024

    /** Thrown for a file that isn't a setup ([newer] when a later Eigen wrote it). */
    class Invalid(message: String, val newer: Boolean = false) : IOException(message)

    private val LOOK_STRINGS = listOf("clock_face", "glass_texture", "dot_style", "icon_shape", "icons")
    private val LOOK_FLOATS = listOf("frost_level", "glass_level", "home_blur")
    private val LOOK_INTS = listOf("grid_columns", "wallpaperDim")
    private val LOOK_BOOLS = listOf("themed_icons", "monochrome")

    @JvmStatic
    fun export(prefs: SharedPreferences, name: String, out: OutputStream) {
        val root = JSONObject().put("format", FORMAT).put("version", VERSION).put("name", name.take(40))
        val look = JSONObject()
        runCatching { JSONObject(prefs.getString("theme", "{}") ?: "{}").optString("accent") }.getOrNull()?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }?.let { look.put("accent", it) }
        for (k in LOOK_STRINGS) prefs.getString(k, null)?.let { look.put(k, it) }
        for (k in LOOK_FLOATS) if (prefs.contains(k)) look.put(k, prefs.getFloat(k, 0f).toDouble())
        for (k in LOOK_INTS) if (prefs.contains(k)) look.put(k, prefs.getInt(k, 0))
        for (k in LOOK_BOOLS) if (prefs.contains(k)) look.put(k, prefs.getBoolean(k, false))
        root.put("look", look)
        root.put("gestures", JSONObject(prefs.getString("gestures", "{}") ?: "{}"))
        val drawers = JSONObject()
        for (k in listOf("custom", "order", "disabled")) drawers.put(k, JSONArray(prefs.getString(k, "[]") ?: "[]"))
        drawers.put("merges", JSONObject(prefs.getString("merges", "{}") ?: "{}"))
        // "serial:component" → "component": profiles differ between phones.
        val assignments = JSONObject()
        val own = JSONObject(prefs.getString("assignments", "{}") ?: "{}")
        own.keys().forEach { id -> assignments.put(id.substringAfter(':'), own.getString(id)) }
        drawers.put("assignments", assignments)
        root.put("drawers", drawers)
        // Contexts without app widgets: their ids only mean something on this phone.
        val contexts = ContextStore.parse(prefs.getString("contexts", "[]") ?: "[]")
        for (r in contexts) r.widgets.removeAll { it.startsWith("widget:") }
        root.put("contexts", ContextStore.write(contexts))
        out.write(root.toString(2).toByteArray())
    }

    /** Reads and validates [input] completely; nothing is applied. */
    @JvmStatic
    fun read(input: InputStream): JSONObject {
        val bytes = input.readNBytesCompat(MAX_BYTES + 1)
        if (bytes.size > MAX_BYTES) throw Invalid("Setup larger than ${MAX_BYTES / 1024} KB")
        val root = try { JSONObject(String(bytes)) } catch (e: Exception) { throw Invalid("Not JSON") }
        if (root.optString("format") != FORMAT) throw Invalid("Not an Eigen setup")
        val version = root.optInt("version", 0)
        if (version > VERSION) throw Invalid("Setup version $version", newer = true)
        if (version < 1) throw Invalid("Missing setup version")
        root.optJSONArray("contexts")?.let { ContextStore.parse(it.toString()) }
        val look = root.optJSONObject("look") ?: JSONObject()
        look.optString("accent").takeIf { it.isNotEmpty() }?.let { if (!it.matches(Regex("#[0-9A-Fa-f]{6}"))) throw Invalid("Bad accent") }
        for (k in LOOK_STRINGS) look.optString(k).let { if (it.length > 24 || !it.matches(Regex("[a-z_]*"))) throw Invalid("Bad $k") }
        val drawers = root.optJSONObject("drawers") ?: JSONObject()
        for (k in listOf("custom", "order", "disabled")) drawers.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) if (a.getString(i).length > 24) throw Invalid("Bad drawer name") }
        root.optJSONObject("gestures")?.let { g -> g.keys().forEach { if (g.getString(it).length > 200) throw Invalid("Bad gesture") } }
        return root
    }

    /** What applying [root] replaces, in words, for the preview. */
    @JvmStatic
    fun describe(root: JSONObject, installed: Set<String>): List<String> = buildList {
        val look = root.optJSONObject("look")
        if (look != null && look.length() > 0) add("Colour, clock, glass and icon style")
        if ((root.optJSONObject("gestures")?.length() ?: 0) > 0) add("Gestures")
        val drawers = root.optJSONObject("drawers")
        if (drawers != null) {
            val custom = drawers.optJSONArray("custom")?.length() ?: 0
            add("Drawer names and order" + if (custom > 0) " ($custom of your own)" else "")
            val a = drawers.optJSONObject("assignments")
            if (a != null && a.length() > 0) {
                val here = a.keys().asSequence().count { it in installed }
                add("Which drawer $here app" + (if (here == 1) "" else "s") + " sit in" + if (here < a.length()) " (${a.length() - here} not on this phone)" else "")
            }
        }
        val contexts = root.optJSONArray("contexts")?.length() ?: 0
        if (contexts > 0) add("$contexts context" + if (contexts == 1) "" else "s")
    }

    /** Applies a validated setup. [idsByComponent] maps "component" → this phone's app ids. */
    @JvmStatic
    fun apply(root: JSONObject, prefs: SharedPreferences, idsByComponent: Map<String, List<String>>, plus: Boolean, freeContexts: Int) {
        val edit = prefs.edit()
        val look = root.optJSONObject("look") ?: JSONObject()
        look.optString("accent").takeIf { it.isNotEmpty() }?.let { accent ->
            val theme = runCatching { JSONObject(prefs.getString("theme", "{}") ?: "{}") }.getOrDefault(JSONObject())
            edit.putString("theme", theme.put("accent", accent).toString())
        }
        for (k in LOOK_STRINGS) if (look.has(k)) edit.putString(k, look.getString(k))
        for (k in LOOK_FLOATS) if (look.has(k)) edit.putFloat(k, look.getDouble(k).toFloat().coerceIn(0f, 1f))
        if (look.has("grid_columns")) edit.putInt("grid_columns", look.getInt("grid_columns").coerceIn(4, 6))
        if (look.has("wallpaperDim")) edit.putInt("wallpaperDim", look.getInt("wallpaperDim").coerceIn(0, 92))
        for (k in LOOK_BOOLS) if (look.has(k)) edit.putBoolean(k, look.getBoolean(k))
        root.optJSONObject("gestures")?.let { g ->
            // A gesture that opens an app names it by component; map it to this phone's app or drop it.
            val out = JSONObject()
            g.keys().forEach { k ->
                val v = g.getString(k)
                if (!v.startsWith("app:")) out.put(k, v)
                else idsByComponent[v.removePrefix("app:").substringAfter(':')]?.firstOrNull()?.let { if (plus) out.put(k, "app:$it") }
            }
            edit.putString("gestures", out.toString())
        }
        root.optJSONObject("drawers")?.let { d ->
            for (k in listOf("custom", "order", "disabled")) d.optJSONArray(k)?.let { edit.putString(k, it.toString()) }
            d.optJSONObject("merges")?.let { edit.putString("merges", it.toString()) }
            d.optJSONObject("assignments")?.let { a ->
                val mine = JSONObject()
                a.keys().forEach { component -> idsByComponent[component]?.forEach { id -> mine.put(id, a.getString(component)) } }
                edit.putString("assignments", mine.toString())
            }
        }
        root.optJSONArray("contexts")?.let { c ->
            val rules = ContextStore.parse(c.toString())
            ContextRules.normalize(rules)
            // Free installs keep everyday plus their allowance; the rest arrive switched off, ready for Plus.
            if (!plus) rules.filter { !it.fallback }.drop(freeContexts).forEach { it.enabled = false }
            edit.putString("contexts", ContextStore.write(rules).toString())
        }
        if (!edit.commit()) throw IOException("Couldn't save the setup")
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
