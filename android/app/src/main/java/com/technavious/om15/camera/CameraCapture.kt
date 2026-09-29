package com.technavious.om15.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraCapture(private val context: Context) {
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("CameraCapture", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    suspend fun capturePhoto(): File = suspendCoroutine { cont ->
        val capture = imageCapture ?: run {
            cont.resumeWithException(IllegalStateException("Camera not initialized"))
            return@suspendCoroutine
        }

        val photoDir = File(context.filesDir, "photos").also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(photoDir, "IMG_${timestamp}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    cont.resume(photoFile)
                }
                override fun onError(exc: ImageCaptureException) {
                    cont.resumeWithException(exc)
                }
            }
        )
    }

    fun loadBitmap(file: File, maxDimension: Int = 1024): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val scale = maxOf(1, maxOf(
            options.outWidth / maxDimension,
            options.outHeight / maxDimension
        ))

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
