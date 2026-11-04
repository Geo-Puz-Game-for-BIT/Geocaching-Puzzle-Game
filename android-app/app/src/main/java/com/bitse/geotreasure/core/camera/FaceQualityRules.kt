package com.bitse.geotreasure.core.camera

/**
 * 人脸初检纯规则（无 Android 依赖，JVM 可单测）。
 *
 * 首版只做「确实有人参与拍摄」的存在性校验，不做身份比对 / 活体 / 建筑识别硬门槛：
 * - 人脸数量：至少 1 张清晰合格人脸（≥1），多人合照仍视为「有人参与」；
 * - 脸部尺寸：bounding box 宽度占图宽比例不低于阈值，避免「远景路人」误判为参与；
 * - 亮度：平均灰度过低判过暗；
 * - 清晰度：拉普拉斯方差过低判过糊。
 *
 * V0.4 参数表（初值，约 20 样本试测目标，非实测准确率）：
 * | 参数 | 初值 | 含义 |
 * |------|------|------|
 * | MIN_FACE_WIDTH_RATIO | 0.10 | 脸宽/图宽 ≥10%，过滤远景小脸 |
 * | MIN_BRIGHTNESS       | 0.15 | 平均灰度 ≥15%（0..1），过滤过暗 |
 * | MIN_LAPLACIAN_VARIANCE | 40.0 | 拉普拉斯方差 ≥40，过滤过糊 |
 */
object FaceQualityRules {

    /** 首版要求至少 1 张清晰人脸。 */
    const val MIN_FACE_COUNT = 1

    /** 脸宽占图宽最小比例（V0.4 初值 0.10）。 */
    const val MIN_FACE_WIDTH_RATIO = 0.10f

    /** 平均亮度下限（V0.4 初值 0.15，0..1）。 */
    const val MIN_BRIGHTNESS = 0.15f

    /** 拉普拉斯方差下限（V0.4 初值 40.0，清晰度阈值，经验值）。 */
    const val MIN_LAPLACIAN_VARIANCE = 40.0

    enum class FaceCountVerdict { OK, NONE }

    fun evaluateFaceCount(count: Int): FaceCountVerdict =
        if (count < MIN_FACE_COUNT) FaceCountVerdict.NONE else FaceCountVerdict.OK

    fun isTooDark(avgBrightness: Float, threshold: Float = MIN_BRIGHTNESS): Boolean =
        avgBrightness < threshold

    fun isTooBlurry(variance: Double, threshold: Double = MIN_LAPLACIAN_VARIANCE): Boolean =
        variance < threshold

    fun isFaceTooSmall(
        faceWidth: Int,
        imageWidth: Int,
        minRatio: Float = MIN_FACE_WIDTH_RATIO,
    ): Boolean = imageWidth <= 0 || faceWidth.toFloat() / imageWidth < minRatio

    /** 是否至少存在一张尺寸合格的脸（「≥1 张合格」：任一 face 达标即通过，不只看第一张）。 */
    fun hasQualifiedFace(
        faceWidths: List<Int>,
        imageWidth: Int,
        minRatio: Float = MIN_FACE_WIDTH_RATIO,
    ): Boolean = faceWidths.any { !isFaceTooSmall(it, imageWidth, minRatio) }

    /** 平均亮度：灰度数组均值归一化到 0..1。 */
    fun averageLuminance(gray: IntArray): Float {
        if (gray.isEmpty()) return 0f
        val sum = gray.fold(0L) { acc, v -> acc + v }
        return (sum.toDouble() / gray.size / 255.0).toFloat()
    }

    /** 拉普拉斯方差：对灰度图做 3×3 拉普拉斯算子，返回方差（清晰度度量）。 */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || gray.size < width * height) return 0.0
        val count = (width - 2) * (height - 2)
        val values = DoubleArray(count)
        var index = 0
        var sum = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = gray[y * width + x].toDouble() * 4.0
                val neighbors = gray[(y - 1) * width + x].toDouble() +
                    gray[(y + 1) * width + x].toDouble() +
                    gray[y * width + x - 1].toDouble() +
                    gray[y * width + x + 1].toDouble()
                val v = center - neighbors
                values[index] = v
                sum += v
                index++
            }
        }
        val mean = sum / count
        var variance = 0.0
        for (v in values) {
            val d = v - mean
            variance += d * d
        }
        return variance / count
    }
}
