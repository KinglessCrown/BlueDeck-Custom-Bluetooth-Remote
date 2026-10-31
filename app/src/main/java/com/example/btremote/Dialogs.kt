package com.example.btremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@SuppressLint("MissingPermission")
@Composable
fun Picker(bt: Bt, onClose: () -> Unit) {
    var ble by remember { mutableStateOf(false) }
    val found = remember { mutableStateMapOf<String, BluetoothDevice>() }
    DisposableEffect(ble) {
        val stop = if (ble) bt.scan { found[it.address] = it } else ({})
        onDispose { stop() }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Connect") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Classic SPP"); Switch(ble, { ble = it }); Text("BLE") }
            HorizontalDivider()
            val list = if (ble) found.values.sortedBy { it.name == null } else bt.bonded()
            if (!ble && list.isEmpty()) Text("Pair the module in Android settings first.")
            list.forEach { d -> TextButton({ bt.connect(d, ble); onClose() }) { Text("${d.name ?: "?"}  ${d.address}") } }
        }
    }, confirmButton = { TextButton(onClose) { Text("Close") } },
        dismissButton = { TextButton({ bt.disconnect(); onClose() }) { Text("Disconnect") } })
}

@Composable fun Sec(t: String) {
    Text(t, color = Color(0xFF60A5FA), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
}
@Composable fun CfgField(cfg: Cfg, key: String, label: String, def: String = "") {
    OutlinedTextField(cfg.s(key, def), { cfg.set(key, it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) })
}
@Composable fun CfgSwitch(cfg: Cfg, key: String, label: String, def: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(cfg.b(key, def), { cfg.set(key, it) })
    }
}
@Composable fun CfgChoice(cfg: Cfg, key: String, label: String, opts: List<Pair<String, String>>, def: String) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        opts.forEach { (v, n) -> FilterChip(cfg.s(key, def) == v, { cfg.set(key, v) }, label = { Text(n) }) }
    }
}

@Composable
fun SettingsDialog(cfg: Cfg, onCopy: () -> Unit, onPaste: () -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Settings") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Sec("Message format")
            CfgChoice(cfg, "end", "Line ending", listOf("" to "None", "\\n" to "\\n", "\\r\\n" to "\\r\\n", "\\r" to "\\r"), "\\n")
            CfgField(cfg, "pre", "Prefix added to every text command")
            CfgField(cfg, "suf", "Suffix added to every text command")
            Text("Escapes \\n \\r \\t \\0 work in commands. hex: commands are sent raw.", style = MaterialTheme.typography.bodySmall)
            Sec("Classic (SPP)")
            CfgField(cfg, "spp", "SPP UUID", "00001101-0000-1000-8000-00805F9B34FB")
            Sec("BLE")
            CfgField(cfg, "svc", "Service UUID filter (blank = any)")
            CfgField(cfg, "wchar", "Write characteristic (blank = auto, e.g. FFE1)")
            CfgField(cfg, "nchar", "Notify characteristic (blank = auto)")
            CfgChoice(cfg, "wmode", "Write type", listOf("auto" to "Auto", "resp" to "With response", "noresp" to "No response"), "auto")
            CfgField(cfg, "chunk", "Bytes per packet (20 default; >20 asks for bigger MTU)", "20")
            CfgField(cfg, "gap", "Delay between packets (ms)", "0")
            Sec("Connection")
            CfgSwitch(cfg, "auto", "Auto-connect / auto-reconnect", false)
            CfgField(cfg, "init", "Send on connect (optional)")
            CfgField(cfg, "hb", "Heartbeat command (optional)")
            CfgField(cfg, "hbms", "Heartbeat interval (ms)", "1000")
            Sec("Controls")
            CfgField(cfg, "rate", "Slider / stick send interval (ms)", "40")
            CfgSwitch(cfg, "haptic", "Vibrate on press", true)
            CfgSwitch(cfg, "keepon", "Keep screen on", true)
            CfgChoice(cfg, "orient", "Orientation", listOf("land" to "Landscape", "port" to "Portrait", "auto" to "Auto"), "land")
            CfgChoice(cfg, "snap", "Edit grid snap (dp)", listOf("1" to "1", "4" to "4", "8" to "8", "16" to "16"), "4")
            Sec("Layout backup")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onCopy) { Text("Copy layout") }
                OutlinedButton(onPaste) { Text("Paste layout") }
            }
        }
    }, confirmButton = { TextButton(onClose) { Text("Done") } })
}

@Composable
fun Monitor(log: List<Line>, onSend: (String) -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    var hex by remember { mutableStateOf(false) }
    var cmd by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Monitor", Modifier.weight(1f)); Text("Hex "); Switch(hex, { hex = it }) }
    }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(cmd, { cmd = it }, Modifier.weight(1f), singleLine = true, label = { Text("Send (text or hex:..)") })
                TextButton({ onSend(cmd) }) { Text("Send") }
            }
            LazyColumn(Modifier.height(200.dp)) {
                items(log.asReversed()) { l ->
                    Text((if (l.tx) "TX " else "RX ") + txt(l.data, hex), fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall, color = if (l.tx) Color(0xFF60A5FA) else Color(0xFF4ADE80))
                }
            }
        }
    }, confirmButton = { TextButton(onClose) { Text("Close") } }, dismissButton = { TextButton(onClear) { Text("Clear") } })
}
