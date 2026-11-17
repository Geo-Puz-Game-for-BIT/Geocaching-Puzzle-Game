package com.bitse.geotreasure.core.map

import com.bitse.geotreasure.core.location.distanceMeters
import com.bitse.geotreasure.core.location.offsetPoint
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMapProjectionTest {
    private val origin = GeoPoint(39.73, 116.17)
    private val viewBox = SvgMapViewBox(0.0, 0.0, 400.0, 400.0)

    @Test
    fun `three noncollinear controls map GCJ02 positions to SVG and back`() {
        val projection = VectorMapProjection.fit(viewBox, listOf(
            control(0.0, 0.0), control(100.0, 0.0), control(0.0, 100.0),
        ))
        val geo = point(50.0, 50.0)
        val svg = projection.project(geo)

        assertEquals(135.0, svg.x, 0.01)
        assertEquals(107.5, svg.y, 0.01)
        assertTrue(distanceMeters(geo, projection.unproject(svg)) < 0.01)
        assertTrue(projection.rmsResidualMeters < 0.01)
    }

    @Test
    fun `extra control exposes artwork alignment error in meters`() {
        val controls = listOf(
            control(0.0, 0.0), control(100.0, 0.0), control(0.0, 100.0),
            SvgMapControlPoint(point(100.0, 100.0), SvgMapPoint(262.0, 197.0)),
        )

        val projection = VectorMapProjection.fit(viewBox, controls)

        assertTrue(projection.rmsResidualMeters > 0.0)
        assertTrue(projection.maxResidualMeters >= projection.rmsResidualMeters)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `collinear geographic controls are rejected`() {
        VectorMapProjection.fit(viewBox, listOf(
            control(0.0, 0.0), control(50.0, 0.0), control(100.0, 0.0),
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `controls outside the SVG viewBox are rejected`() {
        VectorMapProjection.fit(viewBox, listOf(
            control(0.0, 0.0), control(100.0, 0.0),
            SvgMapControlPoint(point(0.0, 100.0), SvgMapPoint(450.0, 20.0)),
        ))
    }

    private fun point(east: Double, north: Double): GeoPoint =
        offsetPoint(origin, LocalOffsetMeters(east, north))

    private fun control(east: Double, north: Double) = SvgMapControlPoint(
        geo = point(east, north),
        svg = SvgMapPoint(2.0 * east + 0.5 * north + 10.0, 0.25 * east + 1.5 * north + 20.0),
    )
}
