package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

var status_face_dfetector:Boolean = false
var status_udp_send:Boolean = false
var bounds = Rect(0, 0, 0, 0);
var rotX: Float = 0.0F
var rotY: Float = 0.0F
var rotZ: Float = 0.0F
var left: Int = 0
var top: Int = 0
var right: Int = 0
var bottom: Int = 0

private class FaceAnalyzer(private var listener: (Int) -> Unit) : ImageAnalysis.Analyzer {
    private val detector = FaceDetection.getClient()

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {

        val mediaImage = imageProxy.image ?: return
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
                .addOnSuccessListener { faces ->
                    listener(faces.size)
                    status_face_dfetector = (faces.size == 1)
                    for (face in faces) {
                        left = face.boundingBox.left
                        right = face.boundingBox.right
                        top = face.boundingBox.top
                        bottom = face.boundingBox.bottom
                        rotX = face.headEulerAngleX
                        rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
                        rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees
                    }

                }
                .addOnFailureListener { e ->
                    Log.e("FaceAnalyzer", "Face detection failure.", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }

    }
}

class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private fun takePhoto() {}

    private fun changeMode() {
        if (status_udp_send)
            status_udp_send = false
        else
            status_udp_send = true
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }

            imageCapture = ImageCapture.Builder()
                    .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FaceAnalyzer { faces ->
                            Log.d(TAG, "Face detected: $faces")
                            camera_capture_button.setEnabled(faces > 0)
                            udp_start_button.setEnabled(true)
                        })
                    }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }

        // 各ボタンの設定
        udp_start_button.setOnClickListener { changeMode() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

        val editText = findViewById<EditText>(R.id.ip_edit_text)
        ip_set_button.setOnClickListener {
            // エディットテキストのテキストを取得
            if (editText.text != null) {
                UDPController.setip(ip_edit_text.text.toString())
            }
        }


        // UDP
        thread {
            var count_send: Int = 0
            while (true) {
                if (status_face_dfetector && status_udp_send) {
                    // UDP送信
                    UDPController.send("" + count_send + ", " + rotX + ", " + rotY + ", " + rotZ + ", " + left + ", " + right + ", " + top + ", " + bottom)
//                    Log.i("MyApplication",  "Hei " + rotX + ", " + rotY + ", " + rotZ + ", " + left + ", " + right + ", " + top + ", " + bottom)
                    count_send++
                }
            }
        }
    }
}