package com.bitse.geotreasure.data.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bitse.geotreasure.core.account.CheckInSeal
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

private val Context.checkInSealDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "checkin_seal",
)

/**
 * 退出前封存记录持久化。按 accountId 命名空间分区：封存只属于原账号，
 * 旧账号重新有效登录后经 [load] 读回其封存，用新会话查询原 attempt 恢复。
 */
class CheckInSealStore(context: Context) {
    private val appContext = context.applicationContext

    private fun prefix(accountId: String): String = "seal.$accountId."

    suspend fun save(seal: CheckInSeal) {
        val p = prefix(seal.accountId)
        appContext.checkInSealDataStore.edit { prefs ->
            prefs[stringPreferencesKey("${p}attempt_id")] = seal.attemptId
            prefs[stringPreferencesKey("${p}landmark_id")] = seal.landmarkId
            prefs[booleanPreferencesKey("${p}request_sent")] = seal.requestSent
            prefs[stringPreferencesKey("${p}upload_state")] = seal.uploadStateName
            seal.photoId?.let { prefs[stringPreferencesKey("${p}photo_id")] = it }
                ?: prefs.remove(stringPreferencesKey("${p}photo_id"))
            seal.photoPath?.let { prefs[stringPreferencesKey("${p}photo_path")] = it }
                ?: prefs.remove(stringPreferencesKey("${p}photo_path"))
            seal.challengeId?.let { prefs[stringPreferencesKey("${p}challenge_id")] = it }
                ?: prefs.remove(stringPreferencesKey("${p}challenge_id"))
            seal.knownCheckInId?.let { prefs[stringPreferencesKey("${p}known_checkin_id")] = it }
                ?: prefs.remove(stringPreferencesKey("${p}known_checkin_id"))
            prefs[longPreferencesKey("${p}sealed_at")] = seal.sealedAt.toEpochMilliseconds()
            val frozen = seal.frozenLocation
            if (frozen != null) {
                prefs[doublePreferencesKey("${p}frozen_lat")] = frozen.point.latitude
                prefs[doublePreferencesKey("${p}frozen_lon")] = frozen.point.longitude
                prefs[floatPreferencesKey("${p}frozen_accuracy")] = frozen.accuracyMeters
                prefs[longPreferencesKey("${p}frozen_captured_at")] = frozen.capturedAt.toEpochMilliseconds()
                prefs[stringPreferencesKey("${p}frozen_source")] = frozen.source.name
            } else {
                prefs.remove(doublePreferencesKey("${p}frozen_lat"))
                prefs.remove(doublePreferencesKey("${p}frozen_lon"))
                prefs.remove(floatPreferencesKey("${p}frozen_accuracy"))
                prefs.remove(longPreferencesKey("${p}frozen_captured_at"))
                prefs.remove(stringPreferencesKey("${p}frozen_source"))
            }
        }
    }

    suspend fun load(accountId: String): CheckInSeal? {
        val p = prefix(accountId)
        val prefs = appContext.checkInSealDataStore.data.first()
        val attemptId = prefs[stringPreferencesKey("${p}attempt_id")] ?: return null
        val frozenLat = prefs[doublePreferencesKey("${p}frozen_lat")]
        val frozenLon = prefs[doublePreferencesKey("${p}frozen_lon")]
        val frozenLocation = if (frozenLat != null && frozenLon != null) {
            LocationSample(
                point = GeoPoint(frozenLat, frozenLon),
                accuracyMeters = prefs[floatPreferencesKey("${p}frozen_accuracy")] ?: 0f,
                capturedAt = Instant.fromEpochMilliseconds(prefs[longPreferencesKey("${p}frozen_captured_at")] ?: 0L),
                source = prefs[stringPreferencesKey("${p}frozen_source")]
                    ?.let { runCatching { LocationSourceKind.valueOf(it) }.getOrNull() }
                    ?: LocationSourceKind.UNKNOWN,
            )
        } else {
            null
        }
        return CheckInSeal(
            accountId = accountId,
            attemptId = attemptId,
            landmarkId = prefs[stringPreferencesKey("${p}landmark_id")] ?: "",
            requestSent = prefs[booleanPreferencesKey("${p}request_sent")] ?: false,
            uploadStateName = prefs[stringPreferencesKey("${p}upload_state")] ?: "",
            photoId = prefs[stringPreferencesKey("${p}photo_id")],
            photoPath = prefs[stringPreferencesKey("${p}photo_path")],
            challengeId = prefs[stringPreferencesKey("${p}challenge_id")],
            frozenLocation = frozenLocation,
            knownCheckInId = prefs[stringPreferencesKey("${p}known_checkin_id")],
            sealedAt = Instant.fromEpochMilliseconds(prefs[longPreferencesKey("${p}sealed_at")] ?: 0L),
        )
    }

    suspend fun clear(accountId: String) {
        val p = prefix(accountId)
        appContext.checkInSealDataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(p) }.forEach { prefs.remove(it) }
        }
    }
}
