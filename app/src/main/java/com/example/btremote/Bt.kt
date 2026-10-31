package com.example.btremote

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class Bt(private val ctx: Context, private val cfg: Cfg,
         private val onState: (String) -> Unit, private val onRx: (ByteArray) -> Unit) {
    private val adapter: BluetoothAdapter =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var sock: BluetoothSocket? = null
    private var out: OutputStream? = null
    private var gatt: BluetoothGatt? = null
    private var chr: BluetoothGattCharacteristic? = null
    private val sem = Semaphore(0)
    private val q = Executors.newSingleThreadExecutor()
    private val h = Handler(Looper.getMainLooper())
    private var retry: Runnable? = null
    private var want = false
    private var lastDev: BluetoothDevice? = null
    private var lastBle = false
    private var gen = 0
    private val SPP = "00001101-0000-1000-8000-00805F9B34FB"

    fun bonded(): List<BluetoothDevice> = try { adapter.bondedDevices.toList() } catch (e: Exception) { emptyList() }

    fun scan(onFound: (BluetoothDevice) -> Unit): () -> Unit {
        val s = adapter.bluetoothLeScanner ?: return {}
        val cb = object : ScanCallback() { override fun onScanResult(t: Int, r: ScanResult) = onFound(r.device) }
        s.startScan(cb)
        return { try { s.stopScan(cb) } catch (_: Exception) {} }
    }

    fun connect(d: BluetoothDevice, ble: Boolean) {
        close(); want = true; lastDev = d; lastBle = ble
        cfg.set("lastAddr", d.address); cfg.set("lastType", if (ble) "ble" else "spp")
        val g = ++gen; onState("Connecting…")
        if (ble) connectBle(d, g) else connectSpp(d, g)
    }

    fun connectLast() {
        val a = cfg.s("lastAddr"); if (a.isBlank()) return
        try { connect(adapter.getRemoteDevice(a), cfg.s("lastType") == "ble") } catch (_: Exception) {}
    }

    fun disconnect() { want = false; close(); onState("Not connected") }

    private fun close() {
        gen++; retry?.let { h.removeCallbacks(it) }; retry = null
        try { sock?.close() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        sock = null; out = null; gatt = null; chr = null
    }

    private fun lost(g: Int, msg: String) {
        if (g != gen) return
        val d = lastDev; val ble = lastBle
        close(); onState(msg)
        if (want && cfg.b("auto", false) && d != null) {
            val r = Runnable { if (want) connect(d, ble) }
            retry = r; h.postDelayed(r, 2000)
        }
    }

    private fun connectSpp(d: BluetoothDevice, g: Int) {
        Thread {
            var up = false
            try {
                val u = try { UUID.fromString(cfg.s("spp", SPP)) } catch (e: Exception) { UUID.fromString(SPP) }
                val s = d.createRfcommSocketToServiceRecord(u)
                adapter.cancelDiscovery(); s.connect()
                if (g != gen) { s.close(); return@Thread }
                sock = s; out = s.outputStream; up = true
                onState("Connected (SPP): ${d.name}")
                val buf = ByteArray(512); val ins = s.inputStream
                while (true) { val n = ins.read(buf); if (n < 0) break; onRx(buf.copyOf(n)) }
                lost(g, "Disconnected")
            } catch (e: Exception) { lost(g, if (up) "Disconnected" else "Failed: ${e.message}") }
        }.start()
    }

    private fun connectBle(d: BluetoothDevice, g: Int) {
        val prop = BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        val nprop = BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE
        gatt = d.connectGatt(ctx, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gt: BluetoothGatt, st: Int, ns: Int) {
                if (g != gen) return
                if (ns == BluetoothProfile.STATE_CONNECTED) {
                    val c = cfg.i("chunk", 20)
                    if (c > 20) gt.requestMtu(c + 3) else gt.discoverServices()
                } else lost(g, "Disconnected")
            }
            override fun onMtuChanged(gt: BluetoothGatt, mtu: Int, st: Int) { if (g == gen) gt.discoverServices() }
            override fun onServicesDiscovered(gt: BluetoothGatt, st: Int) {
                if (g != gen) return
                val svc = cfg.s("svc").trim(); val wc = cfg.s("wchar").trim(); val nc = cfg.s("nchar").trim()
                val cs = gt.services.filter { svc.isBlank() || it.uuid.toString().contains(svc, true) }
                    .flatMap { it.characteristics }
                chr = cs.firstOrNull { if (wc.isNotBlank()) it.uuid.toString().contains(wc, true) else it.properties and prop != 0 }
                cs.firstOrNull { if (nc.isNotBlank()) it.uuid.toString().contains(nc, true) else it.properties and nprop != 0 }?.let { n ->
                    gt.setCharacteristicNotification(n, true)
                    n.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; gt.writeDescriptor(it)
                    }
                }
                onState(if (chr != null) "Connected (BLE): ${d.name} [${chr!!.uuid.toString().take(8)}]"
                        else "No writable characteristic found")
            }
            override fun onCharacteristicChanged(gt: BluetoothGatt, c: BluetoothGattCharacteristic) {
                if (g == gen) onRx(c.value ?: ByteArray(0))
            }
            override fun onCharacteristicWrite(gt: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) { sem.release() }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    fun send(data: ByteArray) {
        if (data.isEmpty()) return
        q.execute {
            try {
                out?.let { it.write(data); it.flush() }
                val c = chr; val gt = gatt
                if (c != null && gt != null) {
                    val size = cfg.i("chunk", 20).coerceAtLeast(1); val gap = cfg.i("gap", 0).toLong()
                    val noResp = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                    val type = when (cfg.s("wmode", "auto")) {
                        "resp" -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        "noresp" -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        else -> if (noResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    data.toList().chunked(size).forEach { part ->
                        c.writeType = type; c.value = part.toByteArray()
                        sem.drainPermits(); gt.writeCharacteristic(c)
                        if (type == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) sem.tryAcquire(300, TimeUnit.MILLISECONDS)
                        if (gap > 0) Thread.sleep(gap)
                    }
                }
            } catch (e: Exception) { onState("Send error: ${e.message}") }
        }
    }
}
