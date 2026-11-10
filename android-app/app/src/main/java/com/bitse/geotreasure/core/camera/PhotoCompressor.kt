package com.bitse.geotreasure.core.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.bitse.geotreasure.core.model.MAX_PHOTO_BYTES
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** 处理结果：[file] 为最终可用文件（处理失败时即原图），[usedOriginal] 为 true 表示回退原图。 */
data class ProcessedPhoto(
    val file: File,
    val compressed: Boolean,
    val usedOriginal: Boolean,
)

/**
 * S1-C-04：EXIF 方向修正与压缩。
 * - 按 EXIF 方向把像素烘焙为正立 JPEG，横竖拍在显示端与服务端均方向正确；
 * - 超过 [MAX_PHOTO_BYTES]（5 MiB，与服务端契约一致）时按质量序列压缩至契约内；
 * - 任何一步失败都回退原图（压缩失败保留原图），不破坏原始照片；
 * - 无需重编码（已正立且未超限）时直接使用原图，避免多余的有损压缩。
 */
object PhotoCompressor {

    /** 解码目标：结果最长边不小于该值（清晰优先，标准 inSampleSize 语义）。 */
    const val DEFAULT_MAX_DIMENSION = 2048

    /**
     * 计算最大 2^n 采样，使解码后最长边仍不小于 [maxDimension]。
     * 纯函数，供 JVM 单测覆盖。
     */
    fun calcInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        require(maxDimension > 0) { "maxDimension must be positive" }
        var sample = 1
        val longest = max(width, height)
        if (longest <= maxDimension) return 1
        while (longest / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        return sample
    }

    /** 压缩质量序列（从高到低），第一个使文件 ≤ maxBytes 的质量生效。 */
    fun qualitySequence(): List<Int> = listOf(90, 80, 70, 60, 50)

    /**
     * 方向烘焙 + 按需压缩。失败时返回原图（不删除原始照片）。
     * 成功时输出同目录 `final_*.jpg` 并删除原始 capture 文件。
     */
    fun process(
        photoFile: File,
        maxBytes: Long = MAX_PHOTO_BYTES,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
    ): ProcessedPhoto {
        if (!photoFile.exists() || photoFile.length() == 0L) {
            return ProcessedPhoto(photoFile, compressed = false, usedOriginal = true)
        }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photoFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching ProcessedPhoto(photoFile, compressed = false, usedOriginal = true)
            }

            val rotationDegrees = readRotationDegrees(photoFile)
            val needsRewrite = rotationDegrees != 0 || photoFile.length() > maxBytes
            if (!needsRewrite) {
                return@runCatching ProcessedPhoto(photoFile, compressed = false, usedOriginal = false)
            }

            val sample = calcInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            val source = BitmapFactory.decodeFile(
                photoFile.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@runCatching ProcessedPhoto(photoFile, compressed = false, usedOriginal = true)

            val upright = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            } else {
                source
            }

            val finalFile = File(photoFile.parentFile, "final_${photoFile.nameWithoutExtension}.jpg")
            val written = qualitySequence().any { quality -> writeJpeg(upright, finalFile, quality, maxBytes) } ||
                writeJpeg(Bitmap.createScaledBitmap(upright, upright.width / 2, upright.height / 2, true), finalFile, 60, maxBytes)
            if (!written) {
                finalFile.delete()
                return@runCatching ProcessedPhoto(photoFile, compressed = false, usedOriginal = true)
            }

            if (finalFile.absolutePath != photoFile.absolutePath) {
                runCatching { photoFile.delete() }
            }
            ProcessedPhoto(finalFile, compressed = true, usedOriginal = false)
        }.getOrElse {
            ProcessedPhoto(photoFile, compressed = false, usedOriginal = true)
        }
    }

    /** 按 EXIF 方向解码为正立 Bitmap（用于预览显示），失败返回 null。 */
    fun decodeUpright(path: String, maxDimension: Int = DEFAULT_MAX_DIMENSION): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val bitmap = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply {
                    inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                },
            ) ?: return@runCatching null
            val rotationDegrees = readRotationDegrees(file)
            if (rotationDegrees == 0) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        }.getOrNull()
    }

    private fun readRotationDegrees(file: File): Int = runCatching {
        when (ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun writeJpeg(bitmap: Bitmap, target: File, quality: Int, maxBytes: Long): Boolean =
        runCatching {
            FileOutputStream(target).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) return@runCatching false
            }
            if (target.length() in 1..maxBytes) true else {
                target.delete()
                false
            }
        }.getOrDefault(false)
}
