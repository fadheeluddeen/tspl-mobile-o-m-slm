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

            // Bind once the preview is laid out so the saved photo is cropped to exactly what is on screen (viewport).
            previewView.post {
                try {
                    provider.unbindAll()
                    val viewPort = previewView.viewPort
                    camera = if (viewPort != null) {
                        val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture!!).setViewPort(viewPort).build()
                        provider.bindToLifecycle(lifecycleOwner, cameraSelector, group)
                    } else provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                } catch (e: Exception) {
                    Log.e("CameraCapture", "Camera bind failed", e)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Crops a captured photo to the on-screen alignment box (given as fractions of the preview) so the AI
     * only sees the numbers inside it. Returns the cropped file saved next to the original as *_box.jpg.
     */
    fun cropToBox(file: File, box: android.graphics.RectF): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2400) sample *= 2
        val raw = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        val rotation = when (android.media.ExifInterface(file.absolutePath)
            .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val upright = if (rotation == 0f) raw
        else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true).also { raw.recycle() }

        val left = (box.left * upright.width).toInt().coerceIn(0, upright.width - 1)
        val top = (box.top * upright.height).toInt().coerceIn(0, upright.height - 1)
        val w = (box.width() * upright.width).toInt().coerceIn(1, upright.width - left)
        val h = (box.height() * upright.height).toInt().coerceIn(1, upright.height - top)
        val cropped = Bitmap.createBitmap(upright, left, top, w, h)
        val out = boxFileFor(file)
        out.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (cropped !== upright) cropped.recycle()
        upright.recycle()
        return out
    }

    companion object {
        fun boxFileFor(photo: File) = File(photo.parentFile, photo.nameWithoutExtension + "_box.jpg")
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
