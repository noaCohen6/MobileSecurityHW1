# Multi-Condition Android Entry App

This Android application demonstrates a security/puzzle-style entry screen where users must fulfill 6 different conditions using phone sensors and features before they can proceed.

## Features

The app requires users to complete 6 specific conditions to enable the "Enter" button:

1. **Voice Recognition**: Say "open" three times
2. **Phone Tilt**: Tilt the phone at least 45 degrees in both X and Y axes
3. **Compass Direction**: Point the phone toward magnetic north
4. **Hardware Button**: Press the volume up button
5. **WiFi Detection**: Be near a specific WiFi network or a minimum number of networks
6. **Black Color Detection**: Show a black object to the camera

Once all conditions are met, the "Enter" button allows the user to proceed to the success screen.

## Technical Implementation

### Sensors and Permissions Used

- Accelerometer (for tilt detection)
- Magnetometer (for compass direction)
- SpeechRecognizer (for voice commands)
- Volume button monitoring
- WiFi scanning
- Camera (for black color detection)

### Required Permissions

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
```

## How It Works

### Visual Feedback

The app displays 6 light bulbs, initially red. As each condition is met, the corresponding light turns green:

1. First light: Voice recognition condition
2. Second light: Tilt condition
3. Third light: Compass direction condition
4. Fourth light: Volume button condition
5. Fifth light: WiFi condition
6. Sixth light: Black color detection condition

### Condition Details

#### 1. Voice Recognition
The app listens for the word "open" and counts how many times it's detected. When the count reaches 3, the condition is satisfied.

```kotlin
if (result.lowercase().contains("open")) {
    openCount++
    Toast.makeText(applicationContext, "Open detected: $openCount/3", Toast.LENGTH_SHORT).show()
            
    if (openCount >= 3 && !condition1Met) {
        condition1Met = true
        updateLightStatus(1, true)
        checkAllConditions()
    }
}
```

#### 2. Phone Tilt
Uses the device's accelerometer to detect tilting. The user must tilt the phone at least 45 degrees on both X and Y axes.

```kotlin
// Check if tilt exceeds the minimum angle requirement (convert to degrees)
val tiltAngle = Math.toDegrees(Math.atan(value / 9.8)).toFloat()
val meetsRequirement = Math.abs(tiltAngle) >= MIN_X_TILT
```

#### 3. Compass Direction
Uses the device's accelerometer and magnetometer to calculate the compass direction. The condition is met when the phone points north (between 350° and 10°).

```kotlin
// Check if phone is pointing north (between 350 and 10 degrees)
val isPointingNorth = normalizedAzimuth >= 350 || normalizedAzimuth <= 10
```

#### 4. Volume Button
Intercepts the volume up button press event to detect when the user presses it.

```kotlin
override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
    if (event?.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN) {
        // Handle volume up button press
    }
}
```

#### 5. WiFi Networks
Scans available WiFi networks and checks either for a specific network name or for a minimum number of networks.

```kotlin
// Check for specific WiFi network name
val hasSpecificNetwork = scanResults.any { it.SSID == WIFI_NETWORK_NAME }
                
// Check for minimum number of networks
val hasMinNetworks = scanResults.size >= MIN_WIFI_NETWORKS
```

#### 6. Black Color Detection
Uses the device's camera to detect the presence of a black object in the camera view. The condition is met when a substantial area (at least 30%) of the view is filled with a black object.

```kotlin
// Detection parameters
private val BLACK_THRESHOLD = 30 // Max RGB value to be considered black
private val REQUIRED_PIXEL_PERCENTAGE = 30 // At least 30% of the image should be black

// Detection logic
val blackPercentage = (blackPixelCount * 100.0f) / totalSampledPixels
return blackPercentage >= REQUIRED_PIXEL_PERCENTAGE && 
       avgBrightness < 80 &&  // Not too bright overall
       brightPercentage >= 5  // Some bright areas for contrast
```

## Customization

You can customize the application by modifying these parameters:

- `WIFI_NETWORK_NAME`: Specify a particular WiFi network to detect
- `MIN_WIFI_NETWORKS`: Set the minimum number of WiFi networks required
- `MIN_X_TILT` and `MIN_Y_TILT`: Adjust the difficulty of the tilt condition
- `BLACK_THRESHOLD`: Adjust how dark pixels need to be to count as "black"
- `REQUIRED_PIXEL_PERCENTAGE`: Adjust how much of the screen needs to be black

## Setup and Installation

1. Clone this repository
2. Open the project in Android Studio
3. Build and run on an Android device with the required sensors

## Requirements

- Android Studio
- Android SDK level 21 or higher
- A physical Android device with:
  - Accelerometer
  - Magnetometer
  - Microphone
  - WiFi capabilities
  - Camera
