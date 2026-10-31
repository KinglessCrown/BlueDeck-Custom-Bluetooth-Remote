package com.example.btremote

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

/** mode: "D" hold, "T" toggle, "A" slider, "J" stick. x,y,w,h in dp. */
data class Btn(
    val id: Long, val label: String, val mode: String, val press: String,
    val release: String = "", val repeatMs: Int = 0, val min: Int = 0, val max: Int = 100,
    val x: Float = 20f, val y: Float = 20f, val w: Float = 90f, val h: Float = 80f,
    val color: Int = 0xFF3B82F6.toInt(),
    val textSize: Float = 16f, val radius: Float = 20f, val step: Int = 1,
    val dead: Int = 10, val invX: Boolean = false, val invY: Boolean = false
)

/** Global settings, reactive in Compose. */
class Cfg(ctx: Context) {
    private val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private val m = mutableStateMapOf<String, String>()
    init { p.all.forEach { (k, v) -> if (v is String && k != "btns") m[k] = v } }
    fun s(k: String, d: String = "") = m[k] ?: d
    fun i(k: String, d: Int) = m[k]?.toIntOrNull() ?: d
    fun b(k: String, d: Boolean) = m[k]?.let { it == "1" } ?: d
    fun set(k: String, v: String) { m[k] = v; p.edit().putString(k, v).apply() }
    fun set(k: String, v: Boolean) = set(k, if (v) "1" else "0")
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun defaults() = listOf(
        Btn(1, "FORWARD", "D", "E1", "E0", x = 110f, y = 10f),
        Btn(2, "BACK", "D", "E2", "E0", x = 110f, y = 190f),
        Btn(3, "LEFT", "D", "E3", "E0", x = 10f, y = 100f),
        Btn(4, "RIGHT", "D", "E4", "E0", x = 210f, y = 100f),
        Btn(5, "SPEED", "A", "E1.{v}", "", 0, 0, 100, x = 340f, y = 110f, w = 300f, h = 70f, color = 0xFFF59E0B.toInt()),
    )

    fun parse(s: String): List<Btn>? = try {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Btn(o.getLong("id"), o.getString("label"), o.getString("mode"), o.getString("press"),
                o.optString("release"), o.optInt("rep"), o.optInt("min"), o.optInt("max", 100),
                o.optDouble("x", 20.0 + (i % 3) * 100).toFloat(), o.optDouble("y", 20.0 + (i / 3) * 90).toFloat(),
                o.optDouble("w", 90.0).toFloat(), o.optDouble("h", 80.0).toFloat(),
                o.optInt("color", 0xFF3B82F6.toInt()), o.optDouble("ts", 16.0).toFloat(),
                o.optDouble("rad", 20.0).toFloat(), o.optInt("step", 1), o.optInt("dead", 10),
                o.optBoolean("ix"), o.optBoolean("iy"))
        }
    } catch (e: Exception) { null }

    fun toJson(list: List<Btn>): String {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("label", it.label).put("mode", it.mode)
                .put("press", it.press).put("release", it.release).put("rep", it.repeatMs)
                .put("min", it.min).put("max", it.max).put("x", it.x.toDouble()).put("y", it.y.toDouble())
                .put("w", it.w.toDouble()).put("h", it.h.toDouble()).put("color", it.color)
                .put("ts", it.textSize.toDouble()).put("rad", it.radius.toDouble()).put("step", it.step)
                .put("dead", it.dead).put("ix", it.invX).put("iy", it.invY))
        }
        return a.toString()
    }

    fun load(): List<Btn> = p.getString("btns", null)?.let { parse(it) } ?: defaults()
    fun save(list: List<Btn>) { p.edit().putString("btns", toJson(list)).apply() }
}
