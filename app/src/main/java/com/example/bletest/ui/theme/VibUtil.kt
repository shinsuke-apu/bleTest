package com.example.bletest.ui.theme

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresApi

class VibUtil(private val context: Context) {
    val vibratorManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        } else {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val vibrationEffect = VibrationEffect.createWaveform(
        longArrayOf(500L, 500L),
        intArrayOf(VibrationEffect.DEFAULT_AMPLITUDE, 0),
        -1
    )

    val combinedVibration by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CombinedVibration.createParallel(vibrationEffect)
        } else {
            null
        }
    }
}