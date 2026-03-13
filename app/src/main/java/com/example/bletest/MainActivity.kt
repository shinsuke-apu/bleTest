package com.example.bletest

import android.Manifest
import android.app.Activity
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    //private val serviceUuid: String = "e2c56db5-dffb-48d2-b060-d0f5a71096e0"
    @OptIn(ExperimentalUnsignedTypes::class)
    private val serviceUuidHigh: ULong = 0xe2c56db5dffb48d2u
    private val serviceUuidLow:  ULong = 0xb060d0f5a71096e0u
    private val ibeaconSize = 29
    private val TAG = "MainActivity"
    private val SCANPERIOD: Long = 10000
    private var scanFilterList: ArrayList<ScanFilter> = ArrayList()
    private val scanSettings = ScanSettings.Builder().build()
    private var beaconDetected = false
    private val thresholdRssi = -50

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    private val requestEnableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth is required for scanning", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            scanLeDevice()
        } else {
            Toast.makeText(this, "Permissions are required for BLE scanning", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.bluetooth_check_button).setOnClickListener {
            checkBluetoothAndEnable()
        }

        if (hasPermissions()) {
            scanLeDevice()
        } else {
            requestPermissions()
        }
    }

    private fun checkBluetoothAndEnable() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBtLauncher.launch(enableBtIntent)
        } else {
            Toast.makeText(this, "Bluetooth is already enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(getRequiredPermissions())
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    private fun scanLeDevice() {
        val scanner = bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "BLE Scanner not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (!scanning) {
            handler.postDelayed({
                if (scanning) stopScanning(scanner)
            }, SCANPERIOD)

            startScanning(scanner)
        } else {
            stopScanning(scanner)
        }
    }

    private fun startScanning(scanner: BluetoothLeScanner) {
        beaconDetected = false
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
            return
        }
        scanning = true
        scanner.startScan(scanFilterList, scanSettings, leScanCallback)
        //scanner.startScan(leScanCallback)
        Log.d(TAG, "Scanning started")
    }

    private fun stopScanning(scanner: BluetoothLeScanner) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
            return
        }
        scanning = false
        scanner.stopScan(leScanCallback)
        Log.d(TAG, "Scanning stopped")
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (beaconDetected) return
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val scanRecord = result.scanRecord ?: return
            val adbyte = scanRecord.bytes
            if (adbyte.size >= ibeaconSize) {
                var uuidHigh = adbyte.getULongFrom(9)
                var uuidLow = adbyte.getULongFrom(17)
                var major = "%02x".format(adbyte[25]) + "%02x".format(adbyte[26])
                var minor = "%02x".format(adbyte[27]) + "%02x".format(adbyte[28])
                var resultRssi = result.rssi
                Log.d(
                    TAG,
                    "Device found: ${result.device.address}, UUID:" +
                            "%016x".format(uuidHigh.toLong()) + "-" +
                            "%016x".format(uuidLow.toLong()) + ", major:" +
                            major + ", minor:" + minor + ", RSSI:" + resultRssi
                )
                if (uuidHigh == serviceUuidHigh && uuidLow == serviceUuidLow && resultRssi > thresholdRssi) {
                    bluetoothLeScanner?.let { stopScanning(it) }

                    Toast.makeText(
                        this@MainActivity,
                        "Beacon detected:$major:$minor",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
