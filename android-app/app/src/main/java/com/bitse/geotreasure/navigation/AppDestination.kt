package com.bitse.geotreasure.navigation

sealed interface AppDestination {
    data object Map : AppDestination
    data class LandmarkDetail(val landmarkId: String) : AppDestination
    data class Camera(val landmarkId: String, val attemptId: String) : AppDestination
    data class CheckInResult(val checkInId: String, val landmarkId: String) : AppDestination
    data class Puzzle(val puzzleId: String) : AppDestination
    data object Records : AppDestination
    data object Overview3D : AppDestination
}

object AppRoutes {
    const val MAP = "map"
    const val LANDMARK_DETAIL = "landmarks/{landmarkId}"
    const val CAMERA = "camera/{landmarkId}/{attemptId}"
    const val CHECK_IN_RESULT = "checkins/{checkInId}/{landmarkId}/result"
    const val PUZZLE = "puzzles/{puzzleId}"
    const val RECORDS = "records"
    const val OVERVIEW_3D = "overview3d"

    fun landmarkDetail(landmarkId: String) = "landmarks/$landmarkId"
    fun camera(landmarkId: String, attemptId: String) = "camera/$landmarkId/$attemptId"
    fun checkInResult(checkInId: String, landmarkId: String) = "checkins/$checkInId/$landmarkId/result"
    fun puzzle(puzzleId: String) = "puzzles/$puzzleId"
}
