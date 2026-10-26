package com.bitse.geotreasure.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExplorationProgressStore {
    private val mutableCompletedLandmarkIds = MutableStateFlow<Set<String>>(emptySet())
    val completedLandmarkIds: StateFlow<Set<String>> = mutableCompletedLandmarkIds.asStateFlow()

    fun markLandmarkCompleted(landmarkId: String) {
        mutableCompletedLandmarkIds.value = mutableCompletedLandmarkIds.value + landmarkId
    }
}
