package com.example.bletest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock.sleep
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bletest.ui.theme.BleUtil
import com.example.bletest.ui.theme.VibUtil

class MainActivity : AppCompatActivity() {

    private val bleUtil by lazy { BleUtil(this) }
    private val vibUtil by lazy { VibUtil(this) }
    var scanThread = Thread()
    var checkThread = Thread()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            bleUtil.scanLeDevice()
        } else {
            Toast.makeText(this, "Permissions are required for BLE scanning", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private val requestEnableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth is required for scanning", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.bluetooth_check_button).setOnClickListener {
            bleUtil.checkBluetoothAndEnable(requestEnableBtLauncher)
        }

        findViewById<Button>(R.id.bluetooth_scan_button).setOnClickListener {
            scanThread = Thread {
                if (hasPermissions()) {
                    bleUtil.scanLeDevice()
                } else {
                    runOnUiThread {
                        requestPermissions()
                    }
                }
            }
            scanThread.start()
            checkThread = Thread {
                while (!bleUtil.beaconDetected) {
                    sleep(100)
                }
                runOnUiThread {
                    vibUtil.combinedVibration?.let { vibUtil.vibratorManager?.vibrate(it) }
                    Toast.makeText(
                        this,
                        "major: %04x, ".format(bleUtil.nowMajor) +
                                "minor: %04x, ".format(bleUtil.nowMinor) +
                                "RSSI: %d".format(bleUtil.nowRssi),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            checkThread.start()
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
}
