package com.example.android.tensorflowdigitrecognizer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.android.tensorflowdigitrecognizer.classifier.Classifier
import com.example.android.tensorflowdigitrecognizer.executor.ScopedExecutor
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scopedExecutor: ScopedExecutor
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var displayId: Int = -1
    private lateinit var classifier : Classifier

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        classifier = Classifier(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        scopedExecutor = ScopedExecutor(cameraExecutor)
        if (allPermissionsGranted()) {
            viewfinder.post {
                displayId = viewfinder.display.displayId
                setUpCamera()
            }
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewfinder.post {
                    displayId = viewfinder.display.displayId
                    setUpCamera()
                }
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val metrics = DisplayMetrics().also { viewfinder.display.getRealMetrics(it) }

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)

        val rotation = viewfinder.display.rotation

        val preview = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
            image.image?.let {
                runOnUiThread {
                    tvPredictValue.text = classifier.classify(it.toBitmap()).toString()
                    image.close()
                }
            }
        })

        try {
            cameraProvider.unbindAll()
            preview.setSurfaceProvider(viewfinder.createSurfaceProvider())
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, imageAnalysis, preview
            )
        } catch (exc: IllegalStateException) {
            Log.i("WILL_TESTE", "Erro ao abrir a camera")
        }
    }

    fun Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = ln(max(width, height).toDouble() / min(width, height))
        if (abs(previewRatio - ln(RATIO_4_3_VALUE))
            <= abs(previewRatio - ln(RATIO_16_9_VALUE))
        ) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            this, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scopedExecutor.shutdown()
    }
}