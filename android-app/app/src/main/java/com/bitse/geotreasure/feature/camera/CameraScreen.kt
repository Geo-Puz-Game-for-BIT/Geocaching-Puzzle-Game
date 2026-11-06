package com.bitse.geotreasure.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.app.ExplorationProgressStore
import com.bitse.geotreasure.core.camera.CameraXManager
import com.bitse.geotreasure.core.camera.FaceCheckResult
import com.bitse.geotreasure.core.camera.FaceQualityChecker
import com.bitse.geotreasure.core.camera.PhotoCompressor
import com.bitse.geotreasure.core.checkin.BlockReason
import com.bitse.geotreasure.core.checkin.CheckInAttemptRules
import com.bitse.geotreasure.core.checkin.CheckInGate
import com.bitse.geotreasure.core.checkin.CheckInGateResult
import com.bitse.geotreasure.core.checkin.CheckInSubmitCoordinator
import com.bitse.geotreasure.core.checkin.SubmitOutcome
import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.location.LocationProvider
import com.bitse.geotreasure.core.model.CheckInDraft
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LocalPhotoRef
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.UploadState
import com.bitse.geotreasure.data.remote.CheckInApiException
import com.bitse.geotreasure.data.repository.CheckInDraftStore
import com.bitse.geotreasure.data.repository.CheckInRepository
import com.bitse.geotreasure.data.repository.LandmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File
import java.util.UUID

data class CameraUiState(
    val landmarkId: String,
    val attemptId: String,
    val hasCameraPermission: Boolean = false,
    val cameraBound: Boolean = false,
    val photoPath: String? = null,
    val retaking: Boolean = false,
    val isCapturing: Boolean = false,
    val isProcessing: Boolean = false,
    val uploadState: UploadState = UploadState.EMPTY,
    val gate: CheckInGateResult? = null,
    val restoredDraft: Boolean = false,
    val pendingUnknownDraft: Boolean = false,
    val pendingDraftUploadState: UploadState? = null,
    val errorMessage: String? = null,
    val faceCheckMessage: String? = null,
) {
    val canShoot: Boolean get() = gate is CheckInGateResult.Ready
    val gateMessage: String? get() = (gate as? CheckInGateResult.Blocked)?.reason?.message

    /** 结果未知（含查询故障）时禁止重拍/换照/清草稿/切任务覆盖。 */
    val isOutcomeUnknown: Boolean get() = CheckInAttemptRules.isOutcomeUnknown(uploadState)
}

sealed interface CameraAction {
    data object Capture : CameraAction
    data object Retake : CameraAction
    data object CancelRetake : CameraAction
    data object Confirm : CameraAction // 兼作「重试」入口（幂等提交）
    data object RefreshLocation : CameraAction
    data object DiscardPendingDraft : CameraAction // 放弃上一个未完成草稿
    data object KeepPendingDraft : CameraAction // 保留上一个未完成草稿并返回
}

sealed interface CameraEffect {
    data class NavigateToResult(val checkInId: String, val landmarkId: String) : CameraEffect
    data object NavigateBack : CameraEffect
}

class CameraViewModel(
    private val landmarkId: String,
    attemptId: String,
    private val draftStore: CheckInDraftStore,
    private val cameraManager: CameraXManager,
    private val checkInRepository: CheckInRepository,
    private val submitCoordinator: CheckInSubmitCoordinator,
    private val progressStore: ExplorationProgressStore,
    private val locationProvider: LocationProvider,
    private val faceQualityChecker: FaceQualityChecker,
    private val landmarkRepository: LandmarkRepository,
) : ViewModel() {
    /** 当前尝试的幂等键。可靠确认旧尝试结束后可原子生成新 ID（R2）。 */
    private var currentAttemptId: String = attemptId

    private val mutableUiState = MutableStateFlow(CameraUiState(landmarkId, currentAttemptId))
    val uiState: StateFlow<CameraUiState> = mutableUiState.asStateFlow()
    private val effectChannel = Channel<CameraEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var landmark: Landmark? = null
    private var challengeId: String? = null
    private var challengeExpiresAt: Instant? = null
    private var frozenLocation: LocationSample? = null
    private var uploadedPhotoId: String? = null
    private var draftCreatedAt: Instant? = null
    private var attachedPreviewView: PreviewView? = null
    /** 本 attempt 是否已发出过至少一次提交请求（true 后冻结内容不可变，重试即查询恢复）。 */
    private var requestSent: Boolean = false

    init {
        loadLandmarkAndDraft()
    }

    private fun loadLandmarkAndDraft() {
        viewModelScope.launch {
            landmark = when (val result = landmarkRepository.getLandmark(landmarkId)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> null
            }
            val saved = draftStore.load()
            when {
                saved == null -> Unit
                saved.id == currentAttemptId && saved.landmarkId == landmarkId -> recoverDraft(saved)
                else -> {
                    val terminal = saved.uploadState == UploadState.EMPTY ||
                        saved.uploadState == UploadState.CONFIRMED
                    if (!terminal) {
                        mutableUiState.update {
                            it.copy(
                                pendingUnknownDraft = true,
                                pendingDraftUploadState = saved.uploadState,
                            )
                        }
                    }
                }
            }
            refreshGate()
        }
    }

    private fun recoverDraft(saved: CheckInDraft) {
        challengeId = saved.challengeId
        challengeExpiresAt = saved.challengeExpiresAt
        uploadedPhotoId = saved.uploadedPhotoId
        frozenLocation = saved.frozenLocation
        draftCreatedAt = saved.createdAt
        // 恢复为「结果未知」的草稿意味着此前已发出过请求：标记已发送，重试即查询恢复（不重放）
        if (saved.uploadState == UploadState.UNKNOWN) {
            requestSent = true
        }
        val path = saved.localPhoto?.absolutePath
        if (path != null && File(path).exists()) {
            mutableUiState.update {
                it.copy(
                    photoPath = path,
                    uploadState = saved.uploadState,
                    restoredDraft = true,
                    errorMessage = when (saved.uploadState) {
                        UploadState.UNKNOWN -> "上次提交结果未知，请重试以恢复（不要重拍）"
                        UploadState.FAILED -> "上次提交失败，请重试"
                        else -> null
                    },
                )
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        mutableUiState.update { it.copy(hasCameraPermission = granted) }
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        attachedPreviewView = previewView
        // 目标变化由 CameraXManager 内部比较（owner/previewView），不在此按 boolean 早退
        cameraManager.bind(
            lifecycleOwner,
            previewView,
            onBound = { mutableUiState.update { it.copy(cameraBound = true, errorMessage = null) } },
            onError = { e ->
                mutableUiState.update { it.copy(cameraBound = false, errorMessage = e.message) }
            },
        )
    }

    fun onAction(action: CameraAction) {
        when (action) {
            CameraAction.Capture -> capture()
            CameraAction.Retake -> retake()
            CameraAction.CancelRetake -> cancelRetake()
            CameraAction.Confirm -> submitFlow()
            CameraAction.RefreshLocation -> refreshGate()
            CameraAction.DiscardPendingDraft -> discardPendingDraft()
            CameraAction.KeepPendingDraft -> effectChannel.trySend(CameraEffect.NavigateBack)
        }
    }

    /** 取位 + 门控（仅刷新 UI 门控展示，不冻结定位；冻结只在开拍动作发生时进行）。 */
    private fun refreshGate() {
        viewModelScope.launch {
            val (_, gate) = fetchLocationAndGate()
            mutableUiState.update { it.copy(gate = gate) }
        }
    }

    /** 取一次位并计算门控；返回 (定位样本, 门控)。landmark 未加载时返回 NO_LOCATION。 */
    private suspend fun fetchLocationAndGate(): Pair<LocationSample?, CheckInGateResult> {
        val currentLandmark = landmark
        if (currentLandmark == null) {
            return null to CheckInGateResult.Blocked(BlockReason.NO_LOCATION)
        }
        return when (val result = locationProvider.getCurrentLocation()) {
            is AppResult.Success -> {
                val loc = result.data
                val gate = CheckInGate.evaluate(loc, currentLandmark.point, currentLandmark.checkInArea)
                loc to gate
            }
            is AppResult.Failure -> null to CheckInGateResult.Blocked(BlockReason.NO_LOCATION)
        }
    }

    private fun capture() {
        val state = mutableUiState.value
        if (state.isCapturing || state.isProcessing) return
        if (state.isOutcomeUnknown) {
            mutableUiState.update { it.copy(errorMessage = "提交结果未知，请先重试恢复，不要重拍") }
            return
        }
        viewModelScope.launch {
            // 开拍动作发生时重新取位 + 重新门控（不依赖缓存 gate），通过即冻结本次定位
            val (location, gate) = fetchLocationAndGate()
            if (gate !is CheckInGateResult.Ready) {
                mutableUiState.update {
                    it.copy(gate = gate, errorMessage = (gate as CheckInGateResult.Blocked).reason.message)
                }
                return@launch
            }
            frozenLocation = location
            mutableUiState.update {
                it.copy(isCapturing = true, gate = gate, errorMessage = null, faceCheckMessage = null)
            }
            if (!hasValidChallenge()) {
                try {
                    val challenge = checkInRepository.createChallenge(
                        landmarkId = landmarkId,
                        attemptId = currentAttemptId,
                    )
                    challengeId = challenge.id
                    challengeExpiresAt = challenge.expiresAt
                    persistDraft(
                        localPhoto = mutableUiState.value.photoPath?.let {
                            LocalPhotoRef(it, "image/jpeg", File(it).length())
                        },
                        uploadState = mutableUiState.value.uploadState,
                    )
                } catch (e: Throwable) {
                    mutableUiState.update {
                        it.copy(isCapturing = false, errorMessage = checkInFailureMessage(e))
                    }
                    return@launch
                }
            }
            val rotation = attachedPreviewView?.display?.rotation ?: Surface.ROTATION_0
            cameraManager.takePhoto(
                rotation = rotation,
                onCaptured = { file ->
                    // 新照成功才替换旧照；首拍无旧照
                    val old = mutableUiState.value.photoPath
                    if (old != null && old != file.absolutePath) CameraXManager.deletePhoto(old)
                    uploadedPhotoId = null
                    mutableUiState.update {
                        it.copy(
                            photoPath = file.absolutePath,
                            isCapturing = false,
                            retaking = false,
                            uploadState = UploadState.CAPTURED,
                        )
                    }
                    persistDraft(
                        localPhoto = LocalPhotoRef(file.absolutePath, "image/jpeg", file.length()),
                        uploadState = UploadState.CAPTURED,
                    )
                },
                onError = { e ->
                    mutableUiState.update { it.copy(isCapturing = false, errorMessage = e.message) }
                },
            )
        }
    }

    private fun retake() {
        val state = mutableUiState.value
        if (state.isProcessing) return
        if (state.isOutcomeUnknown) {
            mutableUiState.update { it.copy(errorMessage = "提交结果未知，请先重试恢复，不要重拍") }
            return
        }
        if (state.uploadState == UploadState.FAILED) {
            // 明确失败：重拍即安全新尝试，原子生成新 attemptId（R2）
            beginNewAttempt("已放弃上次失败尝试，请重新拍照")
            return
        }
        // 进入重拍模式：旧照保留，不在此删除（取消重拍可回旧预览）
        mutableUiState.update { it.copy(retaking = true, errorMessage = null, faceCheckMessage = null) }
        refreshGate()
    }

    private fun cancelRetake() {
        mutableUiState.update { it.copy(retaking = false, errorMessage = null, faceCheckMessage = null) }
    }

    private fun discardPendingDraft() {
        val pending = mutableUiState.value.pendingDraftUploadState
        // 结果未知或提交进行中的草稿不可丢弃（可能已成功），只能返回继续恢复
        if (pending != null && !CheckInAttemptRules.canDiscard(pending)) {
            mutableUiState.update { it.copy(errorMessage = "上一个打卡结果未知，请返回继续恢复") }
            return
        }
        viewModelScope.launch {
            draftStore.clear()
            mutableUiState.update { it.copy(pendingUnknownDraft = false, pendingDraftUploadState = null) }
            refreshGate()
        }
    }

    /** 提交入口：按是否已发出过请求分流（R4）。 */
    private fun submitFlow() {
        val state = mutableUiState.value
        if (state.isProcessing || state.uploadState == UploadState.CONFIRMED) return

        // 明确未成功：重试无意义，直接安全新尝试（R2）
        if (state.uploadState == UploadState.FAILED) {
            beginNewAttempt("已放弃上次失败尝试，请重新拍照")
            return
        }

        val photoPath = state.photoPath ?: return

        if (requestSent) {
            // 已发出过提交：优先按 attemptId 查询恢复，不重放 POST、不重做本地前处理（R3/R4）
            recoverAttempt()
        } else {
            // 首次提交：完整前处理 + 上传照片 + POST
            firstSubmit(photoPath)
        }
    }

    /** 首次提交：门控 → 本地初检 → 上传照片 → POST（冻结内容 + 幂等键 currentAttemptId）。 */
    private fun firstSubmit(photoPath: String) {
        // 门控：提交复用同一冻结定位（不重取位）
        val location = frozenLocation
        if (location == null) {
            mutableUiState.update { it.copy(errorMessage = "定位已过期，请重新定位后重拍") }
            return
        }
        val challenge = challengeId
        if (challenge == null) {
            mutableUiState.update { it.copy(errorMessage = "挑战码缺失，请重新拍照") }
            return
        }

        // 首次提交：动作发生时检查冻结定位的新鲜度/精度/范围
        val currentLandmark = landmark
        if (currentLandmark != null) {
            val now = Clock.System.now()
            val submitGate = CheckInGate.evaluate(location, currentLandmark.point, currentLandmark.checkInArea, now)
            if (submitGate !is CheckInGateResult.Ready) {
                mutableUiState.update {
                    it.copy(
                        gate = submitGate,
                        errorMessage = (submitGate as CheckInGateResult.Blocked).reason.message,
                    )
                }
                return
            }
        }

        mutableUiState.update { it.copy(isProcessing = true, errorMessage = null, faceCheckMessage = null) }
        persistDraft(
            localPhoto = LocalPhotoRef(photoPath, "image/jpeg", File(photoPath).length()),
            uploadState = UploadState.UPLOADING,
        )

        viewModelScope.launch(Dispatchers.IO) {
            val processed = PhotoCompressor.process(File(photoPath))
            val bitmap = PhotoCompressor.decodeUpright(processed.file.absolutePath)
            if (bitmap == null) {
                onFaceCheckFailed("照片读取失败，请重拍")
                return@launch
            }
            val faceResult = faceQualityChecker.check(bitmap)
            if (faceResult is FaceCheckResult.Failure) {
                onFaceCheckFailed(faceResult.reason.message)
                return@launch
            }

            var photoId = uploadedPhotoId
            if (photoId == null) {
                photoId = try {
                    checkInRepository.uploadPhoto(
                        photoFile = processed.file,
                        challengeId = challenge,
                        attemptId = currentAttemptId,
                    ).photoId.also {
                        uploadedPhotoId = it
                        persistDraft(
                            localPhoto = LocalPhotoRef(
                                processed.file.absolutePath,
                                "image/jpeg",
                                processed.file.length(),
                            ),
                            uploadState = UploadState.UPLOADED,
                        )
                    }
                } catch (e: Throwable) {
                    onSubmitFailed(e, unknownResult = false)
                    return@launch
                }
            }

            val submitLocation = if (location.elapsedRealtimeNanos != null) {
                location.copy(
                    submittedElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                )
            } else {
                location
            }

            // 标记已发送：此后冻结内容不可变，重试即查询恢复（不重放）
            requestSent = true
            when (val outcome = submitCoordinator.submit(landmarkId, currentAttemptId, challenge, photoId, submitLocation)) {
                is SubmitOutcome.Success -> onSuccess(outcome.receipt.checkInId)
                is SubmitOutcome.Recovered -> onSuccess(outcome.receipt.checkInId)
                is SubmitOutcome.ChallengeExpired -> onChallengeExpired(outcome)
                is SubmitOutcome.Failed -> onSubmitFailed(outcome.error, outcome.unknownResult)
            }
        }
    }

    /** 已发出提交的 attempt：只查询恢复，不重放 POST、不重做本地前处理（R3/R4）。 */
    private fun recoverAttempt() {
        mutableUiState.update { it.copy(isProcessing = true, errorMessage = null, faceCheckMessage = null) }
        viewModelScope.launch {
            when (val outcome = submitCoordinator.recover(currentAttemptId)) {
                is SubmitOutcome.Recovered -> onSuccess(outcome.receipt.checkInId)
                is SubmitOutcome.Failed ->
                    if (outcome.unknownResult) onRecoveryUnknown() else onRecoveryConfirmedAbsent()
                else -> onRecoveryUnknown()
            }
        }
    }

    private fun hasValidChallenge(): Boolean {
        val id = challengeId ?: return false
        val expires = challengeExpiresAt ?: return false
        return Clock.System.now() < expires
    }

    private fun onFaceCheckFailed(message: String) {
        viewModelScope.launch {
            val current = mutableUiState.value
            // 防御（R4）：结果未知的 attempt 不得被前处理失败降级为 CAPTURED；分流后理论不触发，保留兜底
            if (current.uploadState == UploadState.UNKNOWN) {
                onRecoveryUnknown()
                return@launch
            }
            val path = current.photoPath
            mutableUiState.update {
                it.copy(isProcessing = false, uploadState = UploadState.CAPTURED, faceCheckMessage = message)
            }
            persistDraft(
                localPhoto = path?.let { LocalPhotoRef(it, "image/jpeg", File(it).length()) },
                uploadState = UploadState.CAPTURED,
            )
        }
    }

    private fun onSubmitFailed(throwable: Throwable, unknownResult: Boolean) {
        viewModelScope.launch {
            val current = mutableUiState.value
            // 防御（R4）：结果未知的 attempt 不得被普通失败降级为 FAILED
            if (current.uploadState == UploadState.UNKNOWN && !unknownResult) {
                onRecoveryUnknown()
                return@launch
            }
            val path = current.photoPath
            val targetState = if (unknownResult) UploadState.UNKNOWN else UploadState.FAILED
            mutableUiState.update {
                it.copy(
                    isProcessing = false,
                    uploadState = targetState,
                    errorMessage = if (unknownResult) {
                        "提交结果未知，请重试以恢复（不要重拍）"
                    } else {
                        checkInFailureMessage(throwable)
                    },
                )
            }
            persistDraft(
                localPhoto = path?.let { LocalPhotoRef(it, "image/jpeg", File(it).length()) },
                uploadState = targetState,
            )
        }
    }

    private fun onChallengeExpired(outcome: SubmitOutcome.ChallengeExpired) {
        val recovered = outcome.recoveredReceipt
        if (recovered != null) {
            onSuccess(recovered.checkInId)
            return
        }
        if (outcome.unknownResult) {
            onChallengeExpiryUnknown()
        } else {
            onChallengeExpiredConfirmedAbsent()
        }
    }

    /** 挑战过期且明确未成功：可靠结束旧尝试，原子生成新 attemptId 后重拍。 */
    private fun onChallengeExpiredConfirmedAbsent() {
        beginNewAttempt("挑战码已过期，请重新拍照")
    }

    /** 查询确认无记录：可靠结束旧尝试，换新 attemptId 重拍。 */
    private fun onRecoveryConfirmedAbsent() {
        beginNewAttempt("上次尝试未成功，请重新拍照")
    }

    /**
     * 可靠确认旧尝试已结束（明确未成功）后，原子生成新 attemptId 并重置冻结状态（R2）。
     * 旧未知尝试不得调用本方法（调用方须先确认 resolution 为 CONFIRMED_ABSENT）。
     */
    private fun beginNewAttempt(message: String) {
        val oldPath = mutableUiState.value.photoPath
        CameraXManager.deletePhoto(oldPath)
        currentAttemptId = "att_" + UUID.randomUUID().toString()
        challengeId = null
        challengeExpiresAt = null
        uploadedPhotoId = null
        frozenLocation = null
        requestSent = false
        draftCreatedAt = null
        mutableUiState.update {
            it.copy(
                attemptId = currentAttemptId,
                isProcessing = false,
                photoPath = null,
                retaking = true,
                uploadState = UploadState.EMPTY,
                errorMessage = message,
            )
        }
        persistDraft(localPhoto = null, uploadState = UploadState.EMPTY)
    }

    /** 挑战过期但旧结果查询失败/未知：保留照片与冻结内容，置 UNKNOWN，仅可同 key 重试。 */
    private fun onChallengeExpiryUnknown() {
        val path = mutableUiState.value.photoPath
        mutableUiState.update {
            it.copy(
                isProcessing = false,
                uploadState = UploadState.UNKNOWN,
                errorMessage = "挑战已过期且结果未知，请重试以恢复（不要重拍）",
            )
        }
        persistDraft(
            localPhoto = path?.let { LocalPhotoRef(it, "image/jpeg", File(it).length()) },
            uploadState = UploadState.UNKNOWN,
        )
    }

    /** 查询故障/处理中：保持 UNKNOWN，仅可再次查询恢复（R3/R4，不降级为 CAPTURED/FAILED）。 */
    private fun onRecoveryUnknown() {
        val path = mutableUiState.value.photoPath
        mutableUiState.update {
            it.copy(
                isProcessing = false,
                uploadState = UploadState.UNKNOWN,
                errorMessage = "无法确认上次结果，请重试恢复（不要重拍）",
            )
        }
        persistDraft(
            localPhoto = path?.let { LocalPhotoRef(it, "image/jpeg", File(it).length()) },
            uploadState = UploadState.UNKNOWN,
        )
    }

    private fun onSuccess(checkInId: String) {
        // 只有服务端确认成功后才发碎片 + 清草稿，不因本地拍照成功提前发奖
        progressStore.markLandmarkCompleted(landmarkId)
        viewModelScope.launch { draftStore.clear() }
        mutableUiState.update { it.copy(isProcessing = false, uploadState = UploadState.CONFIRMED) }
        effectChannel.trySend(CameraEffect.NavigateToResult(checkInId = checkInId, landmarkId = landmarkId))
    }

    private fun persistDraft(localPhoto: LocalPhotoRef?, uploadState: UploadState) {
        viewModelScope.launch {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val createdAt = draftCreatedAt ?: now.also { draftCreatedAt = it }
            draftStore.save(
                CheckInDraft(
                    id = currentAttemptId,
                    landmarkId = landmarkId,
                    localPhoto = localPhoto,
                    uploadedPhotoId = uploadedPhotoId,
                    challengeId = challengeId,
                    challengeExpiresAt = challengeExpiresAt,
                    frozenLocation = frozenLocation,
                    uploadState = uploadState,
                    createdAt = createdAt,
                    updatedAt = now,
                ),
            )
        }
    }

    override fun onCleared() {
        cameraManager.unbind()
        faceQualityChecker.close()
    }

    companion object {
        fun factory(
            landmarkId: String,
            attemptId: String,
            draftStore: CheckInDraftStore,
            cameraManager: CameraXManager,
            checkInRepository: CheckInRepository,
            submitCoordinator: CheckInSubmitCoordinator,
            progressStore: ExplorationProgressStore,
            locationProvider: LocationProvider,
            faceQualityChecker: FaceQualityChecker,
            landmarkRepository: LandmarkRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CameraViewModel(
                landmarkId = landmarkId,
                attemptId = attemptId,
                draftStore = draftStore,
                cameraManager = cameraManager,
                checkInRepository = checkInRepository,
                submitCoordinator = submitCoordinator,
                progressStore = progressStore,
                locationProvider = locationProvider,
                faceQualityChecker = faceQualityChecker,
                landmarkRepository = landmarkRepository,
            ) as T
        }
    }
}

/** 把服务端/网络异常映射为可读提示（可 JVM 单测）。 */
internal fun checkInFailureMessage(throwable: Throwable): String = when (throwable) {
    is CheckInApiException -> when (throwable.code) {
        "OUT_OF_RANGE" -> "已离开打卡范围，请回到地标附近重试"
        "LOCATION_ACCURACY_TOO_LOW" -> "定位精度不足，请到开阔处重试"
        "CHALLENGE_EXPIRED" -> "挑战码已过期，请重新提交"
        "IDEMPOTENCY_CONFLICT" -> "提交冲突，正在恢复上次结果"
        "PHOTO_REQUIRED" -> "照片缺失，请重新拍照"
        "NOT_CONFIGURED" -> throwable.message ?: "打卡服务端未配置"
        else -> throwable.message ?: "打卡失败，请重试"
    }

    else -> throwable.message ?: "打卡失败，请重试"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    onResult: (String, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CameraEffect.NavigateToResult -> onResult(effect.checkInId, effect.landmarkId)
                CameraEffect.NavigateBack -> onBack()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (state.pendingUnknownDraft) {
        PendingDraftDialog(
            canDiscard = state.pendingDraftUploadState?.let { CheckInAttemptRules.canDiscard(it) } ?: true,
            onDiscard = { viewModel.onAction(CameraAction.DiscardPendingDraft) },
            onKeep = { viewModel.onAction(CameraAction.KeepPendingDraft) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照打卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val photoPath = state.photoPath
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                when {
                    !state.hasCameraPermission -> PermissionHint(
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )

                    state.retaking || photoPath == null -> LivePreview(viewModel, lifecycleOwner)
                    else -> PhotoPreview(photoPath)
                }
            }

            state.faceCheckMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            state.errorMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            state.gateMessage?.takeIf { state.photoPath == null || state.retaking }?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            ActionButtons(state = state, onAction = viewModel::onAction)
        }
    }
}

@Composable
private fun PendingDraftDialog(canDiscard: Boolean, onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("发现未完成的打卡") },
        text = {
            Text(
                if (canDiscard) {
                    "上一个打卡任务尚未完成，继续完成它，还是放弃并重新开始？"
                } else {
                    "上一个打卡结果尚未确认（可能已成功），为避免丢失，请返回继续完成它。"
                },
            )
        },
        confirmButton = {
            if (canDiscard) {
                TextButton(onClick = onDiscard) { Text("放弃并重新开始") }
            } else {
                TextButton(onClick = onKeep) { Text("返回继续上一个") }
            }
        },
        dismissButton = {
            if (canDiscard) {
                TextButton(onClick = onKeep) { Text("返回继续上一个") }
            }
        },
    )
}

@Composable
private fun LivePreview(viewModel: CameraViewModel, lifecycleOwner: LifecycleOwner) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(previewView) {
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun PhotoPreview(photoPath: String) {
    val bitmap = remember(photoPath) {
        PhotoCompressor.decodeUpright(photoPath)?.asImageBitmap()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "已拍照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("照片已保存", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PermissionHint(onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.NoPhotography, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Text("需要相机权限才能拍照", fontWeight = FontWeight.SemiBold)
            Button(onClick = onRequestPermission) {
                Text("授予权限")
            }
        }
    }
}

@Composable
private fun ActionButtons(state: CameraUiState, onAction: (CameraAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.photoPath != null && !state.retaking -> {
                OutlinedButton(
                    onClick = { onAction(CameraAction.Retake) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCapturing && !state.isProcessing && !state.isOutcomeUnknown,
                ) {
                    Text("重拍")
                }
                Button(
                    onClick = { onAction(CameraAction.Confirm) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCapturing && !state.isProcessing,
                ) {
                    Text(
                        when {
                            state.isProcessing -> "正在提交打卡…"
                            state.uploadState == UploadState.UNKNOWN -> "重试恢复"
                            state.uploadState == UploadState.FAILED -> "重试"
                            else -> "确认并提交打卡"
                        },
                    )
                }
            }

            state.retaking -> {
                OutlinedButton(
                    onClick = { onAction(CameraAction.CancelRetake) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCapturing,
                ) {
                    Text("取消重拍")
                }
                Button(
                    onClick = { onAction(CameraAction.Capture) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.hasCameraPermission && state.canShoot && !state.isCapturing,
                ) {
                    if (state.isCapturing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    } else {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    }
                    Text(if (state.isCapturing) "  正在拍照…" else "  拍照")
                }
            }

            else -> {
                Button(
                    onClick = { onAction(CameraAction.Capture) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.hasCameraPermission && state.canShoot && !state.isCapturing,
                ) {
                    if (state.isCapturing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    } else {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    }
                    Text(if (state.isCapturing) "  正在拍照…" else "  拍照")
                }
                OutlinedButton(
                    onClick = { onAction(CameraAction.RefreshLocation) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重新定位")
                }
            }
        }
    }
}
