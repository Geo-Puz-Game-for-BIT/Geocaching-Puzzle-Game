package com.bitse.geotreasure.data.repository

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
import com.bitse.geotreasure.core.account.AccountSession
import com.bitse.geotreasure.core.model.CheckInDraft
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocalPhotoRef
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
import com.bitse.geotreasure.core.model.UploadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

private val Context.checkInDraftDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "checkin_draft",
)

/**
 * 打卡草稿持久化：以 Preferences DataStore 存当前草稿，按账号命名空间分区。
 *
 * - 账号命名空间：`acc.<accountId>.`；无账号（legacy 演示数据）：无前缀。
 * - 旧无归属演示数据归 legacy，不自动分给首个登录人（首次登录读的是新命名空间，为空）。
 * - 命名空间由 [AccountSession] 驱动：C 不另维护全局登录态。
 *
 * 拍照页重入、进程重启后可经 [load] 读回未提交草稿（重启恢复）。
 */
class CheckInDraftStore(
    private val context: Context,
    private val accountSession: AccountSession,
) {
    private val appContext = context.applicationContext

    private fun scope(accountId: String?): String = accountId?.let { "acc.$it." } ?: ""

    private val activeAccountId: Flow<String?> =
        accountSession.context.map { it.identity?.accountId }

    private fun draftIdKey(scope: String) = stringPreferencesKey("${scope}draft_id")
    private fun landmarkIdKey(scope: String) = stringPreferencesKey("${scope}landmark_id")
    private fun photoPathKey(scope: String) = stringPreferencesKey("${scope}photo_path")
    private fun photoMimeKey(scope: String) = stringPreferencesKey("${scope}photo_mime")
    private fun photoSizeKey(scope: String) = longPreferencesKey("${scope}photo_size")
    private fun uploadedPhotoIdKey(scope: String) = stringPreferencesKey("${scope}uploaded_photo_id")
    private fun challengeIdKey(scope: String) = stringPreferencesKey("${scope}challenge_id")
    private fun challengeExpiresAtKey(scope: String) = longPreferencesKey("${scope}challenge_expires_at")
    private fun uploadStateKey(scope: String) = stringPreferencesKey("${scope}upload_state")
    private fun createdAtKey(scope: String) = longPreferencesKey("${scope}created_at")
    private fun updatedAtKey(scope: String) = longPreferencesKey("${scope}updated_at")
    private fun frozenLatKey(scope: String) = doublePreferencesKey("${scope}frozen_lat")
    private fun frozenLonKey(scope: String) = doublePreferencesKey("${scope}frozen_lon")
    private fun frozenAccuracyKey(scope: String) = floatPreferencesKey("${scope}frozen_accuracy")
    private fun frozenCapturedAtKey(scope: String) = longPreferencesKey("${scope}frozen_captured_at")
    private fun frozenSourceKey(scope: String) = stringPreferencesKey("${scope}frozen_source")
    private fun requestSentKey(scope: String) = booleanPreferencesKey("${scope}request_sent")

    @OptIn(ExperimentalCoroutinesApi::class)
    val draft: Flow<CheckInDraft?> = activeAccountId.flatMapLatest { accountId ->
        appContext.checkInDraftDataStore.data.map { prefs -> readDraft(prefs, scope(accountId)) }
    }

    private fun readDraft(prefs: Preferences, scope: String): CheckInDraft? {
        val id = prefs[draftIdKey(scope)] ?: return null
        val photoPath = prefs[photoPathKey(scope)]
        val frozenLat = prefs[frozenLatKey(scope)]
        val frozenLon = prefs[frozenLonKey(scope)]
        val frozenLocation = if (frozenLat != null && frozenLon != null) {
            LocationSample(
                point = GeoPoint(frozenLat, frozenLon),
                accuracyMeters = prefs[frozenAccuracyKey(scope)] ?: 0f,
                capturedAt = Instant.fromEpochMilliseconds(prefs[frozenCapturedAtKey(scope)] ?: 0L),
                source = prefs[frozenSourceKey(scope)]
                    ?.let { runCatching { LocationSourceKind.valueOf(it) }.getOrNull() }
                    ?: LocationSourceKind.UNKNOWN,
            )
        } else {
            null
        }
        return CheckInDraft(
            id = id,
            landmarkId = prefs[landmarkIdKey(scope)] ?: "",
            localPhoto = photoPath?.let { path ->
                LocalPhotoRef(
                    absolutePath = path,
                    mimeType = prefs[photoMimeKey(scope)] ?: "image/jpeg",
                    sizeBytes = prefs[photoSizeKey(scope)] ?: 0L,
                )
            },
            uploadedPhotoId = prefs[uploadedPhotoIdKey(scope)],
            challengeId = prefs[challengeIdKey(scope)],
            challengeExpiresAt = prefs[challengeExpiresAtKey(scope)]
                ?.let { Instant.fromEpochMilliseconds(it) },
            frozenLocation = frozenLocation,
            uploadState = prefs[uploadStateKey(scope)]
                ?.let { runCatching { UploadState.valueOf(it) }.getOrNull() }
                ?: UploadState.EMPTY,
            createdAt = Instant.fromEpochMilliseconds(prefs[createdAtKey(scope)] ?: 0L),
            updatedAt = Instant.fromEpochMilliseconds(prefs[updatedAtKey(scope)] ?: 0L),
            requestSent = prefs[requestSentKey(scope)] ?: false,
        )
    }

    suspend fun load(): CheckInDraft? = draft.first()

    suspend fun save(draft: CheckInDraft) {
        val scope = scope(accountSession.currentIdentity()?.accountId)
        appContext.checkInDraftDataStore.edit { prefs ->
            prefs[draftIdKey(scope)] = draft.id
            prefs[landmarkIdKey(scope)] = draft.landmarkId
            val photo = draft.localPhoto
            if (photo != null) {
                prefs[photoPathKey(scope)] = photo.absolutePath
                prefs[photoMimeKey(scope)] = photo.mimeType
                prefs[photoSizeKey(scope)] = photo.sizeBytes
            } else {
                prefs.remove(photoPathKey(scope))
                prefs.remove(photoMimeKey(scope))
                prefs.remove(photoSizeKey(scope))
            }
            draft.uploadedPhotoId?.let { prefs[uploadedPhotoIdKey(scope)] = it }
                ?: prefs.remove(uploadedPhotoIdKey(scope))
            draft.challengeId?.let { prefs[challengeIdKey(scope)] = it }
                ?: prefs.remove(challengeIdKey(scope))
            draft.challengeExpiresAt?.let { prefs[challengeExpiresAtKey(scope)] = it.toEpochMilliseconds() }
                ?: prefs.remove(challengeExpiresAtKey(scope))
            val frozen = draft.frozenLocation
            if (frozen != null) {
                prefs[frozenLatKey(scope)] = frozen.point.latitude
                prefs[frozenLonKey(scope)] = frozen.point.longitude
                prefs[frozenAccuracyKey(scope)] = frozen.accuracyMeters
                prefs[frozenCapturedAtKey(scope)] = frozen.capturedAt.toEpochMilliseconds()
                prefs[frozenSourceKey(scope)] = frozen.source.name
            } else {
                prefs.remove(frozenLatKey(scope))
                prefs.remove(frozenLonKey(scope))
                prefs.remove(frozenAccuracyKey(scope))
                prefs.remove(frozenCapturedAtKey(scope))
                prefs.remove(frozenSourceKey(scope))
            }
            prefs[uploadStateKey(scope)] = draft.uploadState.name
            prefs[createdAtKey(scope)] = draft.createdAt.toEpochMilliseconds()
            prefs[updatedAtKey(scope)] = draft.updatedAt.toEpochMilliseconds()
            prefs[requestSentKey(scope)] = draft.requestSent
        }
    }

    suspend fun clear() {
        val scope = scope(accountSession.currentIdentity()?.accountId)
        appContext.checkInDraftDataStore.edit { prefs ->
            val keys = prefs.asMap().keys.filter { key ->
                if (scope.isEmpty()) {
                    // legacy：清无前缀 key，保留所有账号命名空间
                    !key.name.startsWith("acc.")
                } else {
                    key.name.startsWith(scope)
                }
            }
            keys.forEach { prefs.remove(it) }
        }
    }
}
