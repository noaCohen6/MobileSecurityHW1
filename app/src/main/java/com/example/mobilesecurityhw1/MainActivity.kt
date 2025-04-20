package com.example.mobilesecurityhw1

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mobilesecurityhw1.interfaces.ColorCallback
import com.example.mobilesecurityhw1.interfaces.CompassCallback
import com.example.mobilesecurityhw1.interfaces.SpeechCallback
import com.example.mobilesecurityhw1.interfaces.TiltCallback
import com.example.mobilesecurityhw1.interfaces.WifiCallback
import com.example.mobilesecurityhw1.utilities.ColorDetector
import com.example.mobilesecurityhw1.utilities.CompassDetector
import com.example.mobilesecurityhw1.utilities.SpeechDetector
import com.example.mobilesecurityhw1.utilities.TiltDetector
import com.example.mobilesecurityhw1.utilities.WifiDetector

class MainActivity : AppCompatActivity(), TiltCallback, SpeechCallback, CompassCallback, WifiCallback, ColorCallback {

    private val TAG = "MainActivity"
    private val WIFI_NETWORK_NAME = "robco" // Replace with your specific WiFi network name
    private val MIN_WIFI_NETWORKS = 3 // Minimum number of WiFi networks required

    // UI Components
    private lateinit var lightImg1: AppCompatImageView
    private lateinit var lightImg2: AppCompatImageView
    private lateinit var lightImg3: AppCompatImageView
    private lateinit var lightImg4: AppCompatImageView
    private lateinit var lightImg5: AppCompatImageView
    private lateinit var lightImg6: AppCompatImageView // New light for color detection
    private lateinit var enterButton: AppCompatButton

    // Conditions state
    private var condition1Met = false // Speech recognition - say "open" 3 times
    private var condition2Met = false // Phone tilt
    private var condition3Met = false // Phone orientation (compass) pointing north
    private var condition4Met = false // Volume up button press
    private var condition5Met = false // WiFi network check
    private var condition6Met = false // Black color detection

    // Utilities
    private lateinit var tiltDetector: TiltDetector
    private lateinit var speechDetector: SpeechDetector
    private lateinit var compassDetector: CompassDetector
    private lateinit var wifiDetector: WifiDetector
    private lateinit var colorDetector: ColorDetector
    private lateinit var audioManager: AudioManager

    // Speech recognition counter
    private var openCount = 0

    // Tilt detection variables
    private var tiltXDetected = false
    private var tiltYDetected = false
    private var lastXValue = 0f
    private var lastYValue = 0f

    // Tilt angle requirements
    private val MIN_X_TILT = 45f   // Minimum tilt angle in X axis (degrees)
    private val MIN_Y_TILT = 45f   // Minimum tilt angle in Y axis (degrees)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        requestPermissions()
        initSensors()
        initListeners()

        // Initialize the enter button as disabled
        enterButton.isEnabled = false
    }

    private fun initViews() {
        lightImg1 = findViewById(R.id.light_IMG_1)
        lightImg2 = findViewById(R.id.light_IMG_2)
        lightImg3 = findViewById(R.id.light_IMG_3)
        lightImg4 = findViewById(R.id.light_IMG_4)
        lightImg5 = findViewById(R.id.light_IMG_5)
        lightImg6 = findViewById(R.id.light_IMG_6) // New light for black color detection
        enterButton = findViewById(R.id.EnterButton)
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CAMERA // Add camera permission
        )

        ActivityCompat.requestPermissions(this, permissions, 101)
    }

    private fun initSensors() {
        // Initialize tilt detector
        tiltDetector = TiltDetector(this, this)

        // Initialize compass detector
        compassDetector = CompassDetector(this, this)

        // Initialize speech detector
        speechDetector = SpeechDetector(this, this)

        // Initialize wifi detector
        wifiDetector = WifiDetector(this, this, WIFI_NETWORK_NAME, MIN_WIFI_NETWORKS)

        // Initialize color detector
        colorDetector = ColorDetector(this, this)

        // Initialize audio manager for volume button detection
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private fun initListeners() {
        // Set up enter button click listener
        enterButton.setOnClickListener {
            if (allConditionsMet()) {
                val intent = Intent(this, EnteredSuccessfullyActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    // SpeechCallback implementation
    override fun onSpeechResult(text: String) {
        if (text.lowercase().contains("open")) {
            openCount++
            Toast.makeText(applicationContext, "Open detected: $openCount/3", Toast.LENGTH_SHORT).show()

            if (openCount >= 3 && !condition1Met) {
                condition1Met = true
                updateLightStatus(1, true)
                checkAllConditions()
            }
        }
    }

    // CompassCallback implementation
    override fun onAzimuthChanged(azimuth: Float) {
        // Check if phone is pointing north (between 350 and 10 degrees)
        val isPointingNorth = azimuth >= 350 || azimuth <= 10

        if (isPointingNorth && !condition3Met) {
            condition3Met = true
            updateLightStatus(3, true)
            checkAllConditions()

            // Show feedback to user
            Toast.makeText(applicationContext, "Phone is pointing North!", Toast.LENGTH_SHORT).show()
        }
    }

    // WifiCallback implementation
    override fun onSpecificNetworkFound(networkName: String) {
        if (!condition5Met) {
            condition5Met = true
            updateLightStatus(5, true)
            checkAllConditions()

            Toast.makeText(this, "Specific WiFi network found!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMinimumNetworksFound(networkCount: Int) {
        if (!condition5Met) {
            condition5Met = true
            updateLightStatus(5, true)
            checkAllConditions()

            Toast.makeText(this, "Found at least 3 WiFi networks!", Toast.LENGTH_SHORT).show()
        }
    }

    // ColorCallback implementation
    override fun onColorDetected(detected: Boolean) {
        Log.d(TAG, "onColorDetected called with: $detected")
        if (detected && !condition6Met) {
            condition6Met = true
            updateLightStatus(6, true)
            checkAllConditions()

            Toast.makeText(this, "Black color detected!", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle volume button press
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN) {
            if (!condition4Met) {
                condition4Met = true
                updateLightStatus(4, true)
                checkAllConditions()

                Toast.makeText(this, "Volume up button pressed!", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // TiltCallback implementation
    override fun tiltX(value: Float) {
        lastXValue = value

        // Check if tilt exceeds the minimum angle requirement (convert to degrees)
        val tiltAngle = Math.toDegrees(Math.atan(value / 9.8)).toFloat()
        val meetsRequirement = Math.abs(tiltAngle) >= MIN_X_TILT

        if (meetsRequirement && !tiltXDetected) {
            tiltXDetected = true
            Toast.makeText(this, "X-axis tilt detected at ${Math.abs(tiltAngle).toInt()}°!", Toast.LENGTH_SHORT).show()
            checkTiltCondition()
        }
    }

    override fun tiltY(value: Float) {
        lastYValue = value

        // Check if tilt exceeds the minimum angle requirement (convert to degrees)
        val tiltAngle = Math.toDegrees(Math.atan(value / 9.8)).toFloat()
        val meetsRequirement = Math.abs(tiltAngle) >= MIN_Y_TILT

        if (meetsRequirement && !tiltYDetected) {
            tiltYDetected = true
            Toast.makeText(this, "Y-axis tilt detected at ${Math.abs(tiltAngle).toInt()}°!", Toast.LENGTH_SHORT).show()
            checkTiltCondition()
        }
    }

    private fun checkTiltCondition() {
        if (tiltXDetected && tiltYDetected && !condition2Met) {
            condition2Met = true
            updateLightStatus(2, true)
            checkAllConditions()
        }
    }

    private fun updateLightStatus(lightNumber: Int, isGreen: Boolean) {
        Log.d(TAG, "Updating light $lightNumber to ${if (isGreen) "green" else "red"}")
        val lightView = when (lightNumber) {
            1 -> lightImg1
            2 -> lightImg2
            3 -> lightImg3
            4 -> lightImg4
            5 -> lightImg5
            6 -> lightImg6
            else -> return
        }

        lightView.setImageResource(
            if (isGreen) R.drawable.light_bulb_green else R.drawable.light_bulb_red
        )
    }

    private fun allConditionsMet(): Boolean {
        val result = condition1Met && condition2Met && condition3Met &&
                condition4Met && condition5Met && condition6Met
        Log.d(TAG, "All conditions met: $result")
        return result
    }

    private fun checkAllConditions() {
        val allMet = allConditionsMet()
        enterButton.isEnabled = allMet

        // Change button color to green when all conditions are met
        if (allMet) {
            enterButton.setBackgroundResource(R.color.green_button)
            Toast.makeText(this, "All conditions met! Press Enter to continue.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        tiltDetector.stop()
        compassDetector.stop()
        speechDetector.stopListening()
        wifiDetector.stopScanning()
        colorDetector.stop()
    }

    override fun onResume() {
        super.onResume()
        tiltDetector.start()
        compassDetector.start()
        speechDetector.startListening()

        // Only start WiFi scanning if we have permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) ==
            PackageManager.PERMISSION_GRANTED) {
            wifiDetector.startScanning()
        }

        // Only start color detection if we have camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            colorDetector.start()
            Log.d(TAG, "Color detector started")
        } else {
            Log.e(TAG, "Camera permission not granted, cannot start color detector")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechDetector.destroy()
    }
}