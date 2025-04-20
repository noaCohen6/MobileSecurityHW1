package com.example.mobilesecurityhw1.utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.mobilesecurityhw1.interfaces.ColorCallback
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class ColorDetector(
    private val context: Context,
    private val colorCallback: ColorCallback?
) {
    private val TAG = "ColorDetector"

    private var cameraDevice: CameraDevice? = null
    private var cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private val cameraOpenCloseLock = Semaphore(1)
    private var isRunning = false

    // For color detection
    private val SCAN_INTERVAL_MS = 1000L // Check every second

    // The parameters for black color detection - much stricter now
    private val BLACK_THRESHOLD = 30 // Max RGB value to be considered black (reduced from 50)
    private val REQUIRED_PIXEL_PERCENTAGE = 30 // At least 30% of the image should be black (increased from 10%)

    // Detection state
    private var positiveDetectionCount = 0
    private val REQUIRED_POSITIVE_DETECTIONS = 4 // Need more consecutive positive detections (increased from 2)
    private var consecutiveNegativeCount = 0
    private val MAX_NEGATIVE_COUNT = 2

    // Track when we last sent a positive detection to avoid spamming
    private var lastPositiveDetectionTime = 0L
    private val DETECTION_COOLDOWN_MS = 5000L // 5 seconds cooldown

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
            Log.e(TAG, "Camera device error: $error")
        }
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener

        try {
            // Process the image to detect black color
            processImageForColorDetection(image)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
        } finally {
            // Close the image when done
            image.close()
        }
    }

    private fun processImageForColorDetection(image: Image) {
        // Convert image to bitmap for analysis
        val bitmap = imageToRgbBitmap(image)

        // Analyze the bitmap to detect black color
        val isBlackDetected = detectBlackColor(bitmap)

        if (isBlackDetected) {
            positiveDetectionCount++
            consecutiveNegativeCount = 0

            Log.d(TAG, "Black color detected! Count: $positiveDetectionCount")

            if (positiveDetectionCount >= REQUIRED_POSITIVE_DETECTIONS) {
                val currentTime = System.currentTimeMillis()

                // Only notify if we haven't recently sent a positive detection
                if (currentTime - lastPositiveDetectionTime > DETECTION_COOLDOWN_MS) {
                    // We have enough consecutive detections to confirm black color
                    colorCallback?.onColorDetected(true)
                    lastPositiveDetectionTime = currentTime
                    Log.d(TAG, "Black color detection confirmed! Callback called.")
                }
            }
        } else {
            consecutiveNegativeCount++

            if (consecutiveNegativeCount >= MAX_NEGATIVE_COUNT) {
                positiveDetectionCount = 0 // Reset after enough negative detections
            }
        }
    }

    // converts an Image object (of type YUV420_888) received from the camera
    // into a Bitmap object that can be processed and have its pixel values examined.
    private fun imageToRgbBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val planes = image.planes

        // YUV format
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()

        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun detectBlackColor(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        var blackPixelCount = 0
        var brightPixelCount = 0
        val totalSampledPixels = (width * height) / 16 // Sample every 4th pixel in both directions

        // Calculate average brightness of the entire image
        var totalBrightness = 0

        // Sample the bitmap for performance (check every 4th pixel)
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = bitmap.getPixel(x, y)

                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                // Calculate brightness
                val brightness = (red + green + blue) / 3
                totalBrightness += brightness

                if (isBlackColor(red, green, blue)) {
                    blackPixelCount++
                }

                // Count bright pixels for contrast check
                if (brightness > 100) {
                    brightPixelCount++
                }
            }
        }

        // Calculate percentage of black pixels
        val blackPercentage = (blackPixelCount * 100.0f) / totalSampledPixels

        val avgBrightness = totalBrightness / totalSampledPixels

        // Calculate bright pixel percentage
        val brightPercentage = (brightPixelCount * 100.0f) / totalSampledPixels

        Log.d(TAG, "Black pixel percentage: $blackPercentage%, Avg brightness: $avgBrightness, Bright pixel %: $brightPercentage%")


        return blackPercentage >= REQUIRED_PIXEL_PERCENTAGE &&
                avgBrightness < 80 &&
                brightPercentage >= 5 // Some bright areas for contrast
    }

    private fun isBlackColor(red: Int, green: Int, blue: Int): Boolean {
        // For black, all RGB values should be very low
        return red < BLACK_THRESHOLD &&
                green < BLACK_THRESHOLD &&
                blue < BLACK_THRESHOLD &&
                (red + green + blue) / 3 < BLACK_THRESHOLD
    }

    private fun createCameraPreviewSession() {
        try {
            // Set up ImageReader for image analysis
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
            }

            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(imageReader!!.surface)

            cameraDevice?.createCaptureSession(
                listOf(imageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        try {
                            captureRequestBuilder?.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )

                            captureRequestBuilder?.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON
                            )

                            // Start the capture
                            val captureRequest = captureRequestBuilder?.build()
                            cameraCaptureSession?.setRepeatingRequest(
                                captureRequest!!, null, backgroundHandler
                            )

                            backgroundHandler?.postDelayed(scanRunnable, SCAN_INTERVAL_MS)
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Camera access exception: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure camera session")
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception: ${e.message}")
        }
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                backgroundHandler?.postDelayed(this, SCAN_INTERVAL_MS)
            }
        }
    }

    fun start() {
        if (isRunning) return

        // Reset detection state
        positiveDetectionCount = 0
        consecutiveNegativeCount = 0
        lastPositiveDetectionTime = 0L

        // Check for required permissions before proceeding
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted!")
            return
        }

        // Start background thread
        startBackgroundThread()

        try {
            // Find the back camera
            val cameraId = findBackCamera()
            if (cameraId == null) {
                Log.e(TAG, "Cannot find suitable camera")
                return
            }

            // Open the camera
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }

            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
            isRunning = true

            Log.d(TAG, "Color detector started successfully")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception: ${e.message}")
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while trying to lock camera: ${e.message}")
        }
    }

    fun stop() {
        if (!isRunning) return

        try {
            isRunning = false
            cameraOpenCloseLock.acquire()

            cameraCaptureSession?.close()
            cameraCaptureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null

            stopBackgroundThread()

            cameraOpenCloseLock.release()

            Log.d(TAG, "Color detector stopped")
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while trying to lock camera closing: ${e.message}")
        } finally {
            // Reset detection state
            positiveDetectionCount = 0
            consecutiveNegativeCount = 0
        }
    }

    private fun findBackCamera(): String? {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return cameraId
            }
        }
        return null
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Exception stopping background thread: ${e.message}")
        }
    }
}