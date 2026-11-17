package com.bitse.geotreasure.core.map

import android.content.Context
import org.json.JSONObject

/**
 * P01 底图嵌入 · 校正底图的数据契约（不含高德类型，便于纯 JVM 单测）。
 *
 * 数据来自 B 交付的 campus-unified-v5（2026-11-03）：三条 AI 风格校区插画按
 * 公共 2.5D 地面投影（x = 东向米 × 1.5、y = -北向米 × 1.125，WGS84 局部米制原点）配准。
 * 运行时用的 PNG 已被重采样为「正北向上、东西南北同尺 1.5px/m」，
 * 因此每张图可精确对应一个经纬度矩形，直接交给高德 GroundOverlay。
 *
 * v2 追加：以东/南区实机反馈「比例尺与真图不符、覆盖不全」为线索，改用卫星影像
 * （ArcGIS World Imagery）做真值校验，实测东/南两区插画被竖向压缩至 0.625、横向 0.862，
 * 已按实测仿射重采样并重算 bounds（北区实测与真图一致，保持原样）。
 *
 * 坐标系：config.json 的 south/west/north/east 已由 WGS-84 换算为 GCJ-02，
 * 与高德地图、地标目录 (LiangxiangTreasureCatalog) 保持一致。
 */
data class CampusArtworkBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    /** 字段缺失/越界/顺序错误一律抛出，禁止回退成 0 坐标。 */
    fun validate() {
        require(listOf(south, west, north, east).all { it.isFinite() }) {
            "campus artwork bounds contain non-finite values"
        }
        require(south in -90.0..90.0 && north in -90.0..90.0) { "campus artwork latitude out of range" }
        require(west in -180.0..180.0 && east in -180.0..180.0) { "campus artwork longitude out of range" }
        require(south < north) { "campus artwork south >= north" }
        require(west < east) { "campus artwork west >= east" }
    }
}

/** 一张校正底图的安装参数（对齐工作指南 §3.3 字段）。 */
data class CampusArtworkSpec(
    val campusId: String,
    val assetPath: String,
    val version: String,
    val sha256: String,
    val widthPx: Int,
    val heightPx: Int,
    val bounds: CampusArtworkBounds,
) {
    fun validate() {
        require(campusId.isNotBlank()) { "campus artwork id blank" }
        require(assetPath.startsWith("campus_maps/") && assetPath.endsWith(".png")) {
            "campus artwork path must live under campus_maps/: $assetPath"
        }
        require(version.isNotBlank()) { "campus artwork version blank" }
        require(sha256.length == 64) { "campus artwork sha256 malformed: $campusId" }
        require(widthPx > 0 && heightPx > 0) { "campus artwork size invalid: $campusId" }
        bounds.validate()
    }

    /** ARGB_8888 解码内存估算（宽 × 高 × 4），SDK 纹理/缓存另计。 */
    fun decodeByteEstimate(): Long = widthPx.toLong() * heightPx.toLong() * 4L
}

/**
 * 读取 assets/campus_maps/config.json。
 * 解析失败或字段不合法直接抛错，由调用方显示「资源不可用」而不是静默回退高德底图。
 */
object CampusArtworkCatalog {
    const val CONFIG_ASSET = "campus_maps/config.json"

    fun load(context: Context): List<CampusArtworkSpec> {
        val raw = context.assets.open(CONFIG_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        val root = JSONObject(raw)
        val maps = root.optJSONArray("maps")
            ?: throw IllegalStateException("config.json 缺少 maps 数组")
        require(maps.length() > 0) { "config.json maps 为空" }
        return (0 until maps.length()).map { index ->
            val item = maps.getJSONObject(index)
            CampusArtworkSpec(
                campusId = item.getString("campusId"),
                assetPath = item.getString("assetPath"),
                version = item.getString("version"),
                sha256 = item.getString("sha256"),
                widthPx = item.getInt("widthPx"),
                heightPx = item.getInt("heightPx"),
                bounds = CampusArtworkBounds(
                    south = item.getDouble("south"),
                    west = item.getDouble("west"),
                    north = item.getDouble("north"),
                    east = item.getDouble("east"),
                ),
            ).also(CampusArtworkSpec::validate)
        }
    }
}
