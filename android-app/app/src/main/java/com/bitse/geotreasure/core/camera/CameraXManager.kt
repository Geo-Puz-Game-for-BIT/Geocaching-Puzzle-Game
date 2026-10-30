package com.bitse.geotreasure.core.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * CameraX 封装：Preview + ImageCapture。
 * Screen 通过 [PreviewView] 提供预览 Surface；拍照结果写入应用私有目录
 * `filesDir/checkin_photos/`（不进入共享相册，符合「保存到应用私有目录」要求）。
 */
class CameraXManager(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null

    val isBound: Boolean get() = cameraProvider != null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onError: (Throwable) -> Unit = {},
    ) {
        if (cameraProvider != null) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    val provider = future.get()
                    cameraProvider = provider
                    val p = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val ic = ImageCapture.Builder().build()
                    preview = p
                    imageCapture = ic
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        p,
                        ic,
                    )
                } catch (e: Exception) {
                    onError(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun takePhoto(
        rotation: Int,
        onCaptured: (File) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("相机尚未就绪"))
            return
        }
        // S1-C-04：按当前屏幕方向写入 EXIF，横竖拍方向正确
        capture.targetRotation = rotation
        val file = createPhotoFile()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onCaptured(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            },
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
        imageCapture = null
    }

    private fun createPhotoFile(): File {
        val dir = File(context.filesDir, "checkin_photos").apply { mkdirs() }
        return File(dir, "draft_${System.currentTimeMillis()}.jpg")
    }

    companion object {
        fun deletePhoto(path: String?) {
            if (path.isNullOrBlank()) return
            runCatching { File(path).delete() }
        }
    }
}
