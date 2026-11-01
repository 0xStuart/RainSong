@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.stuartjohnson.rainsong.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.stuartjohnson.rainsong.protocol.DropEvent
import com.stuartjohnson.rainsong.protocol.RainSongUuids
import com.stuartjohnson.rainsong.protocol.StatusFrame
import java.util.ArrayDeque

data class ScannedSensor(
    val address: String,
    val name: String,
    val rssi: Int,
)

/**
 * Scan (service UUID filter) → connect → HIGH priority → enable Drop Event
 * then Status CCCDs. No pairing. Auto-reconnect is owned by the ViewModel.
 */
class BleRainSensor(
    context: Context,
    private val onDrop: (DropEvent) -> Unit,
    private val onStatus: (StatusFrame) -> Unit,
    private val onConnection: (connected: Boolean, message: String?) -> Unit,
    private val onScanResult: (ScannedSensor) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private var gatt: BluetoothGatt? = null
    private val cccdQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private var scanning = false

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            onConnection(false, "Bluetooth off or missing")
            return
        }
        stopScan()
        scanning = true
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(RainSongUuids.SERVICE))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val device = adapter?.getRemoteDevice(address) ?: run {
            onConnection(false, "No adapter")
            return
        }
        stopScan()
        closeGatt()
        onConnection(false, "Connecting…")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
        }
        closeGatt()
        onConnection(false, "Disconnected")
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        cccdQueue.clear()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName
                ?: result.device.name
                ?: RainSongUuids.GAP_NAME
            onScanResult(
                ScannedSensor(
                    address = result.device.address,
                    name = name,
                    rssi = result.rssi,
                ),
            )
        }

        override fun onScanFailed(errorCode: Int) {
            onConnection(false, "Scan failed ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                g.discoverServices()
                main.post { onConnection(false, "Discovering…") }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt()
                main.post { onConnection(false, "Disconnected") }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                main.post { onConnection(false, "Service discovery failed") }
                return
            }
            val service = g.getService(RainSongUuids.SERVICE)
            if (service == null) {
                main.post { onConnection(false, "RainSong service missing") }
                return
            }
            val drop = service.getCharacteristic(RainSongUuids.DROP_EVENT)
            val st = service.getCharacteristic(RainSongUuids.STATUS)
            cccdQueue.clear()
            if (drop != null) cccdQueue.add(drop)
            if (st != null) cccdQueue.add(st)
            if (cccdQueue.isEmpty()) {
                main.post { onConnection(false, "Characteristics missing") }
                return
            }
            enableNextCccd(g)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (cccdQueue.isNotEmpty()) {
                enableNextCccd(g)
                return
            }
            g.getService(RainSongUuids.SERVICE)
                ?.getCharacteristic(RainSongUuids.STATUS)
                ?.let { readCharacteristic(g, it) }
            main.post { onConnection(true, null) }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotify(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            handleNotify(characteristic.uuid, value)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleNotify(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleNotify(characteristic.uuid, characteristic.value ?: return)
            }
        }
    }

    private fun handleNotify(uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            RainSongUuids.DROP_EVENT -> {
                if (value.size >= DropEvent.SIZE) {
                    // Hit audio is triggered by the ViewModel from this callback,
                    // on this thread, before any UI work.
                    onDrop(DropEvent.parse(value))
                }
            }
            RainSongUuids.STATUS -> {
                if (value.size >= StatusFrame.SIZE) {
                    val frame = StatusFrame.parse(value)
                    main.post { onStatus(frame) }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNextCccd(g: BluetoothGatt) {
        val characteristic = cccdQueue.poll() ?: run {
            main.post { onConnection(true, null) }
            return
        }
        g.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(RainSongUuids.CCCD)
        if (cccd == null) {
            enableNextCccd(g)
            return
        }
        val ok = writeCccd(g, cccd)
        if (!ok) enableNextCccd(g)
    }

    @SuppressLint("MissingPermission")
    private fun writeCccd(g: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean {
        val enabled = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, enabled) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = enabled
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readCharacteristic(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.readCharacteristic(characteristic)
        } else {
            @Suppress("DEPRECATION")
            g.readCharacteristic(characteristic)
        }
    }
}
