package com.bitse.geotreasure.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "exploration_progress",
)

/**
 * 已完成地标集合合并（幂等）：首次打卡 +1、重复打卡 +0。
 * 抽成纯函数以便 JVM 单测覆盖幂等规则。
 */
internal fun mergeCompletedLandmark(completed: Set<String>, landmarkId: String): Set<String> =
    completed + landmarkId

/**
 * 探索进度存储：已点亮拼图的地标集合。
 *
 * 以 Preferences DataStore 为唯一真源，`completedLandmarkIds` 由 DataStore 流派生，
 * 因此应用重启后会从磁盘读回已完成的集合（重启恢复）。内存 StateFlow 不再作为真源。
 */
class ExplorationProgressStore(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val completedIdsKey = stringSetPreferencesKey("completed_landmark_ids")

    val completedLandmarkIds: StateFlow<Set<String>> = appContext.dataStore.data
        .map { prefs -> prefs[completedIdsKey] ?: emptySet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun markLandmarkCompleted(landmarkId: String) {
        scope.launch {
            appContext.dataStore.edit { prefs ->
                prefs[completedIdsKey] = mergeCompletedLandmark(
                    prefs[completedIdsKey] ?: emptySet(),
                    landmarkId,
                )
            }
        }
    }
}
