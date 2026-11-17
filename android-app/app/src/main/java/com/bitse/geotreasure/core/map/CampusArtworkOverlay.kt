package com.bitse.geotreasure.core.map

import android.os.Looper
import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.GroundOverlay
import com.amap.api.maps.model.GroundOverlayOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds

/**
 * P01 底图嵌入 · 图片地理覆盖层（GroundOverlay）。
 *
 * 每张校正底图按自身经纬度矩形铺在地图上：拖动/缩放与真实地图 1:1 同步，
 * 地标水滴、打卡区域、迷雾都在其上，因此底图必须是最底层的风格层。
 *
 * 生命周期约定（对齐工作指南 §4）：
 * - 一个实例只服务当前 MapView；descriptor 由本组件独占，安装成功后所有权转移；
 * - install 抛错时 descriptor 仍由调用方释放；
 * - clear() 必须在 MapView.onDestroy 之前调用（AmapMapContent 的 onDispose 顺序已保证），
 *   清理时先 remove 覆盖物再 recycle 图片，避免 SDK 仍在使用位图。
 */
internal class CampusArtworkOverlay(private val map: AMap) {
    private data class Entry(
        val ground: GroundOverlay,
        val image: BitmapDescriptor,
    )

    private val entries = mutableMapOf<String, Entry>()

    fun install(
        spec: CampusArtworkSpec,
        image: BitmapDescriptor,
        zIndex: Float,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "GroundOverlay 只能在主线程安装" }
        require(spec.campusId.isNotBlank() && spec.campusId !in entries) {
            "campus artwork already installed: ${spec.campusId}"
        }
        val bounds = spec.bounds
        bounds.validate()
        val ground = checkNotNull(
            map.addGroundOverlay(
                GroundOverlayOptions()
                    .image(image)
                    .positionFromBounds(
                        LatLngBounds(
                            LatLng(bounds.south, bounds.west),
                            LatLng(bounds.north, bounds.east),
                        ),
                    )
                    .transparency(0f)
                    .zIndex(zIndex)
                    .visible(true),
            ),
        ) { "GroundOverlay 创建失败: ${spec.campusId}" }
        entries[spec.campusId] = Entry(ground, image)
    }

    fun clear() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "GroundOverlay 只能在主线程清理" }
        entries.values.forEach { entry ->
            entry.ground.remove()
            entry.image.recycle()
        }
        entries.clear()
    }
}
