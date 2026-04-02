package com.example.bletest.ui.theme

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat

class BleUtil(private val context: Context) {
    private val vibUtil by lazy { VibUtil(context) }
    @OptIn(ExperimentalUnsignedTypes::class)
    private val serviceUuidHigh: ULong = 0xe2c56db5dffb48d2u
    private val serviceUuidLow:  ULong = 0xb060d0f5a71096e0u
    private val beaconSize = 29
    private val tag = "BleUtil"
    private val scanPeriod: Long = 10000
    private var scanFilterList: ArrayList<ScanFilter> = ArrayList()
    private val scanSettings = ScanSettings.Builder().build()
    var beaconDetected = false
    private val thresholdRssi = -60
    var nowMajor = 0
    var nowMinor = 0
    var nowRssi = 0

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    fun checkBluetoothAndEnable(launcher: ActivityResultLauncher<Intent>) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            launcher.launch(enableBtIntent)
        } else {
            Toast.makeText(context, "Bluetooth is already enabled", Toast.LENGTH_SHORT).show()
        }
    }

    fun scanLeDevice() {
        val scanner = bluetoothLeScanner
        if (scanner == null) {
            return
        }

        if (!scanning) {
            handler.postDelayed({
                if (scanning) stopScanning(scanner)
            }, scanPeriod)

            startScanning(scanner)
        } else {
            stopScanning(scanner)
        }
    }

    private fun startScanning(scanner: BluetoothLeScanner) {
        beaconDetected = false
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(tag, "BLUETOOTH_SCAN permission not granted")
            return
        }
        scanning = true
        scanner.startScan(scanFilterList, scanSettings, leScanCallback)
        Log.d(tag, "Scanning started")
    }

    private fun stopScanning(scanner: BluetoothLeScanner) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(tag, "BLUETOOTH_SCAN permission not granted")
            return
        }
        scanning = false
        scanner.stopScan(leScanCallback)
        Log.d(tag, "Scanning stopped")
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (beaconDetected) return

            val scanRecord = result.scanRecord ?: return
            val advData = scanRecord.bytes
            if (advData.size >= beaconSize) {
                val uuidHigh = advData.getULongFrom(9)
                val uuidLow = advData.getULongFrom(17)
                val major = advData.getUShortFrom(25)
                val minor = advData.getUShortFrom(27)
                val resultRssi = result.rssi
                Log.d(
                    tag,
                    "Device found: ${result.device.address}, UUID:" +
                            "%016x".format(uuidHigh.toLong()) + "-" +
                            "%016x".format(uuidLow.toLong()) + ", major:" +
                            "%04x".format(major) + " minor:" + "%04x".format(minor) + ", RSSI:" + resultRssi)
                if (uuidHigh == serviceUuidHigh && uuidLow == serviceUuidLow && resultRssi > thresholdRssi) {
                    beaconDetected = true
                    nowMajor = major
                    nowMinor = minor
                    nowRssi = resultRssi

                    bluetoothLeScanner?.let { stopScanning(it) }
                }
            }
        }
        private fun ByteArray.getUShortFrom(start: Int): Int {
            return ((this[start].toInt() and 0xFF) shl 8) or
                    (this[start+1].toInt() and 0xFF)
        }
        private fun ByteArray.getULongFrom(start: Int): ULong {
            return ((this[start].toULong() and 0xFFuL) shl 56) or
                    ((this[start+1].toULong() and 0xFFuL) shl 48) or
                    ((this[start+2].toULong() and 0xFFuL) shl 40) or
                    ((this[start+3].toULong() and 0xFFuL) shl 32) or
                    ((this[start+4].toULong() and 0xFFuL) shl 24) or
                    ((this[start+5].toULong() and 0xFFuL) shl 16) or
                    ((this[start+6].toULong() and 0xFFuL) shl 8) or
                    (this[start+7].toULong() and 0xFFuL)
        }
    }
}
