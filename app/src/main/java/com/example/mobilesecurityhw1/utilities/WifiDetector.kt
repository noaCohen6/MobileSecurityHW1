package com.example.mobilesecurityhw1.utilities

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.example.mobilesecurityhw1.interfaces.WifiCallback

class WifiDetector(
    private val context: Context,
    private val wifiCallback: WifiCallback?,
    private val targetNetworkName: String,
    private val minNetworkCount: Int
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var scanReceiver: BroadcastReceiver? = null
    private var isScanning = false

    fun startScanning() {
        if (isScanning) return

        // Check for required permissions before proceeding
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return  // Don't proceed if permissions are not granted
        }

        // Register for scan results
        scanReceiver = object : BroadcastReceiver() {
            @androidx.annotation.RequiresPermission(allOf = ["android.permission.ACCESS_WIFI_STATE", "android.permission.ACCESS_FINE_LOCATION"])
            override fun onReceive(context: Context, intent: Intent) {
                // Check permissions before proceeding
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return
                }

                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    scanSuccess()
                }
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, intentFilter)

        isScanning = true
        startScan()
    }

    private fun startScan() {
        // Check permissions again before each scan
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            wifiManager.startScan()
        } catch (e: SecurityException) {
            // Handle security exception gracefully
            e.printStackTrace()
        }

        // Schedule next scan in 10 seconds
        android.os.Handler().postDelayed({
            if (isScanning) {
                startScan()
            }
        }, 10000)
    }

    @androidx.annotation.RequiresPermission(allOf = ["android.permission.ACCESS_WIFI_STATE", "android.permission.ACCESS_FINE_LOCATION"])
    private fun scanSuccess() {
        // Double-check permissions before accessing scan results
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val scanResults = wifiManager.scanResults

            if (scanResults != null) {
                // Check for specific WiFi network name
                val hasSpecificNetwork = scanResults.any { it.SSID == targetNetworkName }

                // Check for minimum number of networks
                val hasMinNetworks = scanResults.size >= minNetworkCount

                if (hasSpecificNetwork) {
                    wifiCallback?.onSpecificNetworkFound(targetNetworkName)
                }

                if (hasMinNetworks) {
                    wifiCallback?.onMinimumNetworksFound(scanResults.size)
                }
            }
        } catch (e: SecurityException) {
            // Handle security exception gracefully
            e.printStackTrace()
        }
    }

    fun stopScanning() {
        if (!isScanning) return

        try {
            scanReceiver?.let {
                context.unregisterReceiver(it)
                scanReceiver = null
            }
        } catch (e: IllegalArgumentException) {
            // Handle case where receiver wasn't registered
            e.printStackTrace()
        }

        isScanning = false
    }
}