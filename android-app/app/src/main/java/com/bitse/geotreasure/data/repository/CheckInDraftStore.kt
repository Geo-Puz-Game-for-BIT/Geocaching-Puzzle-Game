package com.bitse.geotreasure.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bitse.geotreasure.core.model.CheckInDraft
import com.bitse.geotreasure.core.model.LocalPhotoRef
import com.bitse.geotreasure.core.model.UploadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

private val Context.checkInDraftDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "checkin_draft",
)

/**
 * 打卡草稿持久化：以 Preferences DataStore 存当前草稿。
 * 拍照页重入、进程重启后可经 [load] 读回未提交草稿（重启恢复）。
 * 复用 DomainModels 的 [CheckInDraft] / [LocalPhotoRef] / [UploadState] 契约。
 */
class CheckInDraftStore(private val context: Context) {
    private val appContext = context.applicationContext

    private val draftIdKey = stringPreferencesKey("draft_id")
    private val landmarkIdKey = stringPreferencesKey("landmark_id")
    private val photoPathKey = stringPreferencesKey("photo_path")
    private val photoMimeKey = stringPreferencesKey("photo_mime")
    private val photoSizeKey = longPreferencesKey("photo_size")
    private val uploadStateKey = stringPreferencesKey("upload_state")
    private val createdAtKey = longPreferencesKey("created_at")
    private val updatedAtKey = longPreferencesKey("updated_at")

    val draft: Flow<CheckInDraft?> = appContext.checkInDraftDataStore.data.map { prefs ->
        val id = prefs[draftIdKey] ?: return@map null
        val photoPath = prefs[photoPathKey]
        CheckInDraft(
            id = id,
            landmarkId = prefs[landmarkIdKey] ?: "",
            localPhoto = photoPath?.let { path ->
                LocalPhotoRef(
                    absolutePath = path,
                    mimeType = prefs[photoMimeKey] ?: "image/jpeg",
                    sizeBytes = prefs[photoSizeKey] ?: 0L,
                )
            },
            uploadState = prefs[uploadStateKey]
                ?.let { runCatching { UploadState.valueOf(it) }.getOrNull() }
                ?: UploadState.EMPTY,
            createdAt = Instant.fromEpochMilliseconds(prefs[createdAtKey] ?: 0L),
            updatedAt = Instant.fromEpochMilliseconds(prefs[updatedAtKey] ?: 0L),
        )
    }

    suspend fun load(): CheckInDraft? = draft.first()

    suspend fun save(draft: CheckInDraft) {
        appContext.checkInDraftDataStore.edit { prefs ->
            prefs[draftIdKey] = draft.id
            prefs[landmarkIdKey] = draft.landmarkId
            val photo = draft.localPhoto
            if (photo != null) {
                prefs[photoPathKey] = photo.absolutePath
                prefs[photoMimeKey] = photo.mimeType
                prefs[photoSizeKey] = photo.sizeBytes
            } else {
                prefs.remove(photoPathKey)
                prefs.remove(photoMimeKey)
                prefs.remove(photoSizeKey)
            }
            prefs[uploadStateKey] = draft.uploadState.name
            prefs[createdAtKey] = draft.createdAt.toEpochMilliseconds()
            prefs[updatedAtKey] = draft.updatedAt.toEpochMilliseconds()
        }
    }

    suspend fun clear() {
        appContext.checkInDraftDataStore.edit { it.clear() }
    }
}
