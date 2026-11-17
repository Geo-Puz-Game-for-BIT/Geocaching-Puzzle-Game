package com.bitse.geotreasure.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * P01 底图嵌入的配置契约：bounds 缺失/顺序错误/非有限值必须失败（禁止回退成 0 坐标），
 * 规格字段（路径、哈希、像素尺寸）不合法同样失败。
 */
class CampusArtworkSpecTest {
    private val validBounds = CampusArtworkBounds(
        south = 39.730987159,
        west = 116.166770062,
        north = 39.739268675,
        east = 116.172811045,
    )

    private fun spec(
        bounds: CampusArtworkBounds = validBounds,
        assetPath: String = "campus_maps/north.png",
        sha256: String = "c".repeat(64),
        widthPx: Int = 779,
        heightPx: Int = 1381,
    ) = CampusArtworkSpec(
        campusId = "north",
        assetPath = assetPath,
        version = "campus-artwork-v1",
        sha256 = sha256,
        widthPx = widthPx,
        heightPx = heightPx,
        bounds = bounds,
    )

    @Test
    fun `valid bounds and spec pass`() {
        validBounds.validate()
        spec().validate()
        assertEquals(779L * 1381L * 4L, spec().decodeByteEstimate())
    }

    @Test
    fun `zero or inverted bounds are rejected instead of falling back`() {
        assertThrows(IllegalArgumentException::class.java) {
            CampusArtworkBounds(0.0, 0.0, 0.0, 0.0).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CampusArtworkBounds(39.74, 116.16, 39.73, 116.17).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CampusArtworkBounds(39.73, 116.17, 39.74, 116.16).validate()
        }
    }

    @Test
    fun `non finite and out of range values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CampusArtworkBounds(Double.NaN, 116.16, 39.74, 116.17).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CampusArtworkBounds(39.73, 116.16, 39.74, Double.POSITIVE_INFINITY).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CampusArtworkBounds(-91.0, 116.16, 39.74, 116.17).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CampusArtworkBounds(39.73, 116.16, 39.74, 181.0).validate()
        }
    }

    @Test
    fun `spec rejects malformed asset path hash and size`() {
        assertThrows(IllegalArgumentException::class.java) {
            spec(assetPath = "drawable/north.png").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            spec(sha256 = "abc").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            spec(widthPx = 0).validate()
        }
    }
}
