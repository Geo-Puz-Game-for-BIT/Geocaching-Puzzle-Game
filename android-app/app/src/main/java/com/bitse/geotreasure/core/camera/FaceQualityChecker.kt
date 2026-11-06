package com.bitse.geotreasure.core.camera

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 人脸初检结果。 */
sealed interface FaceCheckResult {
    data class Success(
        val faceCount: Int,
        val faceWidthRatio: Float,
        val brightness: Float,
        val sharpness: Double,
    ) : FaceCheckResult

    data class Failure(val reason: FaceCheckFailureReason) : FaceCheckResult
}

enum class FaceCheckFailureReason(val message: String) {
    NO_FACE("未检测到人脸，请正对镜头重拍"),
    FACE_TOO_SMALL("人脸过小，请靠近镜头重拍"),
    TOO_DARK("照片过暗，请到光线充足处重拍"),
    TOO_BLURRY("照片模糊，请持稳手机重拍"),
    DETECTION_FAILED("人脸检测失败，请重试"),
}

/**
 * 人脸存在性 + 照片质量初检（ML Kit bundled，离线，不依赖 GMS）。
 * 只证「有人参与拍摄」（至少一张清晰合格人脸），不做身份识别；建筑识别不在本类职责内。
 */
class FaceQualityChecker(
    private val detector: FaceDetector = defaultDetector(),
) {

    suspend fun check(bitmap: Bitmap): FaceCheckResult {
        val gray = toGrayscale(bitmap)
        val brightness = FaceQualityRules.averageLuminance(gray.pixels)
        val sharpness = FaceQualityRules.laplacianVariance(gray.pixels, gray.width, gray.height)

        val faces = runCatching { detectFaces(bitmap) }.getOrElse {
            return FaceCheckResult.Failure(FaceCheckFailureReason.DETECTION_FAILED)
        }

        val countVerdict = FaceQualityRules.evaluateFaceCount(faces.size)
        val faceWidths = faces.map { it.boundingBox.width() }
        val faceWidthRatio = faceWidths.maxOrNull()?.let { faceWidthRatio(it, bitmap.width) } ?: 0f

        // 「至少一张合格脸」：任一 face 尺寸达标即通过，不只看返回列表第一张
        val hasQualifiedFace = FaceQualityRules.hasQualifiedFace(faceWidths, bitmap.width)

        val reason = when {
            countVerdict == FaceQualityRules.FaceCountVerdict.NONE -> FaceCheckFailureReason.NO_FACE
            !hasQualifiedFace -> FaceCheckFailureReason.FACE_TOO_SMALL
            FaceQualityRules.isTooDark(brightness) -> FaceCheckFailureReason.TOO_DARK
            FaceQualityRules.isTooBlurry(sharpness) -> FaceCheckFailureReason.TOO_BLURRY
            else -> null
        }

        return if (reason == null) {
            FaceCheckResult.Success(faces.size, faceWidthRatio, brightness, sharpness)
        } else {
            FaceCheckResult.Failure(reason)
        }
    }

    fun close() {
        runCatching { detector.close() }
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (cont.isActive) cont.resume(faces)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWith(Result.failure(e))
                }
        }

    private fun faceWidthRatio(faceWidth: Int, imageWidth: Int): Float =
        if (imageWidth <= 0) 0f else faceWidth.toFloat() / imageWidth

    private data class GrayImage(val pixels: IntArray, val width: Int, val height: Int)

    private fun toGrayscale(bitmap: Bitmap, maxWidth: Int = 256): GrayImage {
        val scale = if (bitmap.width > maxWidth) maxWidth.toFloat() / bitmap.width else 1f
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
        val argb = IntArray(w * h)
        small.getPixels(argb, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return GrayImage(gray, w, h)
    }

    companion object {
        private fun defaultDetector(): FaceDetector {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()
            return FaceDetection.getClient(options)
        }
    }
}
