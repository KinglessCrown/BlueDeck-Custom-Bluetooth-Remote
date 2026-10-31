package com.example.btremote

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val perms = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}.launch(perms)
        val bg = Color(0xFF0B1220)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF3B82F6), background = bg, surface = bg)) {
                Surface(color = bg) { App(this) }
            }
        }
    }
}

class Line(val tx: Boolean, val data: ByteArray)

fun unesc(s: String) = s.replace("\\r", "\r").replace("\\n", "\n").replace("\\t", "\t").replace("\\0", "\u0000")

/** Text commands: prefix + text + suffix + line ending. "hex:E1 0A" = raw bytes. {v} = analog value. */
fun encode(s: String, v: Int?, cfg: Cfg): ByteArray {
    val t = if (v != null) s.replace("{v}", v.toString()) else s
    if (t.isBlank()) return ByteArray(0)
    if (t.startsWith("hex:")) return try {
        t.substring(4).split(Regex("[\\s,]+")).filter { it.isNotBlank() }
            .map { it.removePrefix("0x").toInt(16).toByte() }.toByteArray()
    } catch (e: Exception) { ByteArray(0) }
    return unesc(cfg.s("pre") + t + cfg.s("suf") + cfg.s("end", "\\n")).toByteArray()
}

fun txt(b: ByteArray, hex: Boolean) =
    if (hex) b.joinToString(" ") { "%02X".format(it) } else String(b).replace("\r", "\\r").replace("\n", "\\n")

val PALETTE = listOf(0xFF3B82F6, 0xFF22C55E, 0xFFEF4444, 0xFFF59E0B, 0xFFA855F7, 0xFF64748B).map { it.toInt() }
fun snap(v: Float, g: Float) = Math.round(v / g) * g

@Composable
fun App(act: ComponentActivity) {
    val store = remember { Store(act) }
    val cfg = remember { Cfg(act) }
    val btns = remember { mutableStateListOf<Btn>().apply { addAll(store.load()) } }
    val log = remember { mutableStateListOf<Line>() }
    var status by remember { mutableStateOf("Not connected") }
    var edit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Btn?>(null) }
    var picker by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var monitor by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current

    fun addLog(l: Line) { log.add(l); while (log.size > 200) log.removeAt(0) }
    val bt = remember { Bt(act, cfg, { status = it }, { d -> act.runOnUiThread { addLog(Line(false, d)) } }) }
    val send: (String, Int?) -> Unit = { s, v ->
        val d = encode(s, v, cfg)
        if (d.isNotEmpty()) { bt.send(d); addLog(Line(true, d)) }
    }
    fun save() = store.save(btns.toList())
    fun upd(n: Btn) { val i = btns.indexOfFirst { it.id == n.id }; if (i >= 0) btns[i] = n }
    fun settle(id: Long) {
        val g = cfg.i("snap", 4).toFloat().coerceAtLeast(1f)
        btns.firstOrNull { it.id == id }?.let { upd(it.copy(x = snap(it.x, g), y = snap(it.y, g), w = snap(it.w, g), h = snap(it.h, g))) }
        save()
    }

    val connected = status.startsWith("Connected")
    LaunchedEffect(Unit) { if (cfg.b("auto", false)) bt.connectLast() }
    LaunchedEffect(connected) { if (connected && cfg.s("init").isNotBlank()) { delay(300); send(cfg.s("init"), null) } }
    LaunchedEffect(connected, cfg.s("hb"), cfg.i("hbms", 1000)) {
        while (connected && cfg.s("hb").isNotBlank()) {
            delay(cfg.i("hbms", 1000).coerceAtLeast(50).toLong()); send(cfg.s("hb"), null)
        }
    }
    val orient = cfg.s("orient", "land")
    LaunchedEffect(orient) {
        act.requestedOrientation = when (orient) {
            "port" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    val keepOn = cfg.b("keepon", true)
    LaunchedEffect(keepOn) {
        if (keepOn) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    val dot = when {
        connected -> Color(0xFF22C55E)
        status.startsWith("Connecting") -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Text(status, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f))
            if (edit) {
                TextButton({ confirmReset = true }) { Text("Reset") }
                FilledTonalButton({ editing = Btn(System.currentTimeMillis(), "NEW", "D", "") }) { Text("+ Add") }
            }
            FilterChip(edit, { edit = !edit; if (!edit) save() }, label = { Text(if (edit) "Done" else "Edit") })
            Button({ picker = true }, shape = RoundedCornerShape(12.dp)) { Text(if (connected) "Device" else "Connect") }
            Box {
                TextButton({ menu = true }) { Text("⋮") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Monitor") }, { menu = false; monitor = true })
                    DropdownMenuItem({ Text("Settings") }, { menu = false; settings = true })
                }
            }
        }
        if (edit) Text("Drag to move  ·  corner dot to resize  ·  tap to edit",
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.03f))
            .then(if (edit) Modifier.drawBehind {
                val step = 24.dp.toPx(); var x = step
                while (x < size.width) {
                    var y = step
                    while (y < size.height) { drawCircle(Color.White.copy(alpha = 0.12f), 2f, Offset(x, y)); y += step }
                    x += step
                }
            } else Modifier)) {
            btns.forEach { b ->
                key(b.id) { BtnView(b, edit, cfg, send, onTap = { editing = b }, onChange = ::upd, onDone = { settle(b.id) }) }
            }
        }
    }

    editing?.let { cur ->
        Editor(cur, onSave = { n ->
            if (btns.any { it.id == n.id }) upd(n) else btns.add(n)
            save(); editing = null
        }, onDuplicate = { n ->
            btns.add(n.copy(id = System.currentTimeMillis(), x = n.x + 20, y = n.y + 20)); save(); editing = null
        }, onDelete = { btns.removeAll { it.id == cur.id }; save(); editing = null }, onClose = { editing = null })
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text("Reset layout?") }, text = { Text("This replaces all your buttons with the defaults.") },
        confirmButton = { TextButton({ btns.clear(); btns.addAll(store.defaults()); save(); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } })
    if (picker) Picker(bt) { picker = false }
    if (settings) SettingsDialog(cfg,
        onCopy = { clip.setText(AnnotatedString(store.toJson(btns.toList()))) },
        onPaste = { clip.getText()?.text?.let { store.parse(it) }?.let { btns.clear(); btns.addAll(it); save() } },
        onClose = { settings = false })
    if (monitor) Monitor(log, onSend = { send(it, null) }, onClear = { log.clear() }, onClose = { monitor = false })
}

@Composable
fun BtnView(b: Btn, edit: Boolean, cfg: Cfg, send: (String, Int?) -> Unit,
            onTap: () -> Unit, onChange: (Btn) -> Unit, onDone: () -> Unit) {
    val cur by rememberUpdatedState(b)
    val dens = LocalDensity.current.density
    val hap = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val base = Color(b.color)
    val shape = RoundedCornerShape(b.radius.dp)
    val solid = pressed || (b.mode == "T" && on)

    Box(
        Modifier.offset(b.x.dp, b.y.dp).size(b.w.dp, b.h.dp).clip(shape)
            .background(if (solid) base.copy(alpha = 0.9f) else base.copy(alpha = 0.16f))
            .border(1.5.dp, base.copy(alpha = 0.7f), shape)
            .then(if (!edit && b.mode != "A" && b.mode != "J") Modifier.pointerInput(b.id) {
                detectTapGestures(onPress = {
                    val c = cur
                    if (cfg.b("haptic", true)) hap.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (c.mode == "T") {
                        on = !on; send(if (on) c.press else c.release, null)
                    } else {
                        pressed = true; send(c.press, null)
                        val job = if (c.repeatMs > 0) scope.launch {
                            while (true) { delay(c.repeatMs.toLong()); send(c.press, null) }
                        } else null
                        tryAwaitRelease(); pressed = false; job?.cancel()
                        if (c.release.isNotBlank()) send(c.release, null)
                    }
                })
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when (b.mode) {
            "A" -> AnalogCtl(b, cfg, send)
            "J" -> Stick(b, cfg, send)
            else -> Text(b.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = b.textSize.sp)
        }
        if (edit) {
            Box(Modifier.matchParentSize().border(1.5.dp, Color.White.copy(alpha = 0.9f), shape)
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onDone() }) { c, d ->
                        c.consume(); onChange(cur.copy(x = max(0f, cur.x + d.x / dens), y = max(0f, cur.y + d.y / dens)))
                    }
                })
            Box(Modifier.align(Alignment.BottomEnd).size(28.dp).background(Color.White.copy(alpha = 0.9f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onDone() }) { c, d ->
                        c.consume(); onChange(cur.copy(w = max(48f, cur.w + d.x / dens), h = max(40f, cur.h + d.y / dens)))
                    }
                })
        }
    }
}

@Composable
fun AnalogCtl(b: Btn, cfg: Cfg, send: (String, Int?) -> Unit) {
    var v by remember { mutableFloatStateOf(b.min.toFloat()) }
    var last by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var lastT by remember { mutableLongStateOf(0L) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
        Text("${b.label}: ${v.toInt()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = b.textSize.sp)
        Slider(v, { nv ->
            val st = b.step.coerceAtLeast(1)
            val i = Math.round(nv / st) * st; v = i.toFloat(); val now = System.currentTimeMillis()
            if (i != last && now - lastT > cfg.i("rate", 40)) { last = i; lastT = now; send(b.press, i) }
        }, valueRange = b.min.toFloat()..max(b.max, b.min + 1).toFloat(),
            onValueChangeFinished = { send(b.press, v.toInt()); if (b.release.isNotBlank()) send(b.release, v.toInt()) },
            colors = SliderDefaults.colors(thumbColor = Color(b.color), activeTrackColor = Color(b.color),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)))
    }
}

/** Placeholders: {x} {y} in Min..Max, {a} angle 0-359 (0 = up, clockwise), {m} strength 0-100. */
@Composable
fun Stick(b: Btn, cfg: Cfg, send: (String, Int?) -> Unit) {
    var kx by remember { mutableFloatStateOf(0f) }
    var ky by remember { mutableFloatStateOf(0f) }
    var last by remember { mutableStateOf("") }
    var lastT by remember { mutableLongStateOf(0L) }
    val cur by rememberUpdatedState(b)

    fun fill(t: String): String {
        val c = cur
        var ex = kx; var ey = ky
        val mag = Math.hypot(ex.toDouble(), ey.toDouble()).toFloat().coerceAtMost(1f)
        if (mag < c.dead / 100f) { ex = 0f; ey = 0f }
        val m = if (ex == 0f && ey == 0f) 0 else Math.round(mag * 100)
        val a = if (m == 0) 0 else Math.round(((Math.toDegrees(Math.atan2(ex.toDouble(), -ey.toDouble())) + 360) % 360)).toInt()
        if (c.invX) ex = -ex
        if (c.invY) ey = -ey
        val mid = (c.min + c.max) / 2f; val half = (c.max - c.min) / 2f
        return t.replace("{x}", Math.round(mid + ex * half).toString())
            .replace("{y}", Math.round(mid - ey * half).toString())
            .replace("{a}", a.toString()).replace("{m}", m.toString())
    }
    fun out(force: Boolean) {
        val s = fill(cur.press); val now = System.currentTimeMillis()
        if (s != last && (force || now - lastT > cfg.i("rate", 40))) { last = s; lastT = now; send(s, null) }
    }

    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        val reach = minOf(size.width, size.height) / 2f * 0.65f
        fun move(p: Offset) {
            var dx = p.x - size.width / 2f; var dy = p.y - size.height / 2f
            val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (len > reach) { dx *= reach / len; dy *= reach / len }
            kx = dx / reach; ky = dy / reach; out(false)
        }
        fun stop() {
            kx = 0f; ky = 0f; out(true)
            if (cur.release.isNotBlank()) send(fill(cur.release), null)
        }
        detectDragGestures(onDragStart = { move(it) }, onDragEnd = { stop() }, onDragCancel = { stop() }) { c, _ ->
            c.consume(); move(c.position)
        }
    }) {
        val r = minOf(size.width, size.height) / 2f
        drawCircle(Color.White.copy(alpha = 0.15f), r * 0.98f, center)
        drawCircle(Color(b.color), r * 0.65f, center, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, r * 0.3f, Offset(center.x + kx * r * 0.65f, center.y + ky * r * 0.65f))
    }
}

@Composable
fun Editor(b: Btn, onSave: (Btn) -> Unit, onDuplicate: (Btn) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    var label by remember { mutableStateOf(b.label) }
    var mode by remember { mutableStateOf(b.mode) }
    var press by remember { mutableStateOf(b.press) }
    var release by remember { mutableStateOf(b.release) }
    var rep by remember { mutableStateOf(b.repeatMs.toString()) }
    var min by remember { mutableStateOf(b.min.toString()) }
    var max by remember { mutableStateOf(b.max.toString()) }
    var step by remember { mutableStateOf(b.step.toString()) }
    var dead by remember { mutableStateOf(b.dead.toString()) }
    var invX by remember { mutableStateOf(b.invX) }
    var invY by remember { mutableStateOf(b.invY) }
    var tsz by remember { mutableStateOf(b.textSize.toString()) }
    var rad by remember { mutableStateOf(b.radius.toString()) }
    var color by remember { mutableIntStateOf(b.color) }
    fun build() = b.copy(label = label, mode = mode, press = press, release = release,
        repeatMs = rep.toIntOrNull() ?: 0, min = min.toIntOrNull() ?: 0, max = max.toIntOrNull() ?: 100,
        step = step.toIntOrNull() ?: 1, dead = dead.toIntOrNull() ?: 10, invX = invX, invY = invY,
        textSize = tsz.toFloatOrNull() ?: 16f, radius = rad.toFloatOrNull() ?: 20f, color = color)

    AlertDialog(onDismissRequest = onClose, title = { Text("Edit button") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("D" to "Hold", "T" to "Toggle", "A" to "Slider", "J" to "Stick").forEach { (m, n) ->
                    FilterChip(mode == m, {
                        mode = m
                        if (m == "J" && min == "0" && max == "100") min = "-100"
                        if (m != "J" && min == "-100") min = "0"
                    }, label = { Text(n) })
                }
            }
            OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true)
            OutlinedTextField(press, { press = it }, singleLine = true, label = {
                Text(when (mode) {
                    "A" -> "Sends, e.g. E1.{v}"; "J" -> "Sends, e.g. J{x},{y}"
                    "T" -> "Sends when ON"; else -> "Sends on press, e.g. E1"
                })
            })
            OutlinedTextField(release, { release = it }, singleLine = true, label = {
                Text(if (mode == "T") "Sends when OFF" else "Sends on release (optional)")
            })
            when (mode) {
                "A", "J" -> {
                    OutlinedTextField(min, { min = it }, label = { Text("Min") }, singleLine = true)
                    OutlinedTextField(max, { max = it }, label = { Text("Max") }, singleLine = true)
                    if (mode == "A") OutlinedTextField(step, { step = it }, label = { Text("Step") }, singleLine = true)
                    else {
                        OutlinedTextField(dead, { dead = it }, label = { Text("Dead zone %") }, singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Invert X "); Switch(invX, { invX = it }) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Invert Y "); Switch(invY, { invY = it }) }
                    }
                }
                "D" -> OutlinedTextField(rep, { rep = it }, label = { Text("Repeat while held (ms, 0 = off)") }, singleLine = true)
            }
            OutlinedTextField(tsz, { tsz = it }, label = { Text("Text size") }, singleLine = true)
            OutlinedTextField(rad, { rad = it }, label = { Text("Corner radius (0 = square, 99 = round)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PALETTE.forEach { c ->
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == color) 3.dp else 0.dp, Color.White, CircleShape).clickable { color = c })
                }
            }
            Text("Stick placeholders: {x} {y} {a} angle 0-359 {m} strength 0-100.", style = MaterialTheme.typography.bodySmall)
            Text("Raw bytes: start with hex: e.g. hex:E1 0A", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({ onSave(build()) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onDelete) { Text("Delete") }
                TextButton({ onDuplicate(build()) }) { Text("Copy") }
                TextButton(onClose) { Text("Cancel") }
            }
        })
}
