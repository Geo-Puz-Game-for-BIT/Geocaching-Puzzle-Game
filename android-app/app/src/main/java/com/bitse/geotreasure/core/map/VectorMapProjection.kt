package com.bitse.geotreasure.core.map

import com.bitse.geotreasure.core.location.distanceMeters
import com.bitse.geotreasure.core.location.localOffsetMeters
import com.bitse.geotreasure.core.location.offsetPoint
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import kotlin.math.abs
import kotlin.math.sqrt

data class SvgMapPoint(val x: Double, val y: Double)

data class SvgMapViewBox(val minX: Double, val minY: Double, val width: Double, val height: Double) {
    init {
        require(minX.isFinite() && minY.isFinite())
        require(width.isFinite() && width > 0.0 && height.isFinite() && height > 0.0)
    }

    fun contains(point: SvgMapPoint): Boolean =
        point.x in minX..(minX + width) && point.y in minY..(minY + height)
}

data class SvgMapControlPoint(val geo: GeoPoint, val svg: SvgMapPoint)

class VectorMapProjection private constructor(
    private val origin: GeoPoint,
    private val xCoefficients: DoubleArray,
    private val yCoefficients: DoubleArray,
    val rmsResidualMeters: Double,
    val maxResidualMeters: Double,
) {
    fun project(point: GeoPoint): SvgMapPoint {
        require(point.latitude.isFinite() && point.longitude.isFinite())
        val offset = localOffsetMeters(origin, point)
        return SvgMapPoint(
            xCoefficients[0] * offset.eastMeters + xCoefficients[1] * offset.northMeters + xCoefficients[2],
            yCoefficients[0] * offset.eastMeters + yCoefficients[1] * offset.northMeters + yCoefficients[2],
        )
    }

    fun unproject(point: SvgMapPoint): GeoPoint {
        require(point.x.isFinite() && point.y.isFinite())
        val x = point.x - xCoefficients[2]
        val y = point.y - yCoefficients[2]
        val determinant = xCoefficients[0] * yCoefficients[1] - xCoefficients[1] * yCoefficients[0]
        val east = (x * yCoefficients[1] - y * xCoefficients[1]) / determinant
        val north = (y * xCoefficients[0] - x * yCoefficients[0]) / determinant
        return offsetPoint(origin, LocalOffsetMeters(east, north))
    }

    companion object {
        fun fit(viewBox: SvgMapViewBox, controls: List<SvgMapControlPoint>): VectorMapProjection {
            require(controls.size >= 3) { "SVG map needs at least three control points" }
            require(controls.all { control ->
                control.geo.latitude.isFinite() && control.geo.longitude.isFinite() &&
                    control.geo.latitude in -90.0..90.0 && control.geo.longitude in -180.0..180.0 &&
                    control.svg.x.isFinite() && control.svg.y.isFinite() && viewBox.contains(control.svg)
            }) { "Control points must have valid GCJ-02 positions inside the SVG viewBox" }

            val origin = controls.first().geo
            val normal = Array(3) { DoubleArray(3) }
            val x = DoubleArray(3)
            val y = DoubleArray(3)
            controls.forEach { control ->
                val offset = localOffsetMeters(origin, control.geo)
                val row = doubleArrayOf(offset.eastMeters, offset.northMeters, 1.0)
                for (column in 0..2) {
                    x[column] += row[column] * control.svg.x
                    y[column] += row[column] * control.svg.y
                    for (other in 0..2) normal[column][other] += row[column] * row[other]
                }
            }
            val xCoefficients = solve(normal, x)
            val yCoefficients = solve(normal, y)
            val determinant = xCoefficients[0] * yCoefficients[1] - xCoefficients[1] * yCoefficients[0]
            require(abs(determinant) > 1e-9) { "SVG control points produce a non-invertible projection" }

            val initial = VectorMapProjection(origin, xCoefficients, yCoefficients, 0.0, 0.0)
            val residuals = controls.map { control -> distanceMeters(control.geo, initial.unproject(control.svg)) }
            require(residuals.all(Double::isFinite))
            return VectorMapProjection(
                origin,
                xCoefficients,
                yCoefficients,
                sqrt(residuals.sumOf { it * it } / residuals.size),
                residuals.max(),
            )
        }

        private fun solve(normal: Array<DoubleArray>, values: DoubleArray): DoubleArray {
            val rows = Array(3) { index -> normal[index].copyOf() + values[index] }
            for (column in 0..2) {
                val pivot = (column..2).maxBy { abs(rows[it][column]) }
                require(abs(rows[pivot][column]) > 1e-9) { "SVG geographic controls are collinear" }
                val swap = rows[column]
                rows[column] = rows[pivot]
                rows[pivot] = swap
                val scale = rows[column][column]
                for (index in column..3) rows[column][index] /= scale
                for (other in 0..2) {
                    if (other == column) continue
                    val factor = rows[other][column]
                    for (index in column..3) rows[other][index] -= factor * rows[column][index]
                }
            }
            return DoubleArray(3) { rows[it][3] }
        }
    }
}
