package com.bitse.geotreasure.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
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
import com.bitse.geotreasure.core.checkin.AttemptSnapshot
import com.bitse.geotreasure.core.checkin.BlockReason
import com.bitse.geotreasure.core.checkin.CheckInAttemptRules
import com.bitse.geotreasure.core.checkin.CheckInCandidateRules
import com.bitse.geotreasure.core.checkin.CheckInFlowRules
import com.bitse.geotreasure.core.checkin.CheckInGate
import com.bitse.geotreasure.core.checkin.CheckInGateResult
import com.bitse.geotreasure.core.checkin.CheckInSubmitCoordinator
import com.bitse.geotreasure.core.checkin.SubmitOutcome
import com.bitse.geotreasure.core.checkin.SubmitRoute
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
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.AppButtonStyle
import com.bitse.geotreasure.core.ui.components.BannerBar
import com.bitse.geotreasure.core.ui.components.BannerTone
import com.bitse.geotreasure.core.ui.components.PageHeader
import com.bitse.geotreasure.core.ui.system.StatusBarIconsDark
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.HourglassTop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

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
    val pendingDraftLandmarkId: String? = null,
    val pendingDraftAttemptId: String? = null,
    val errorMessage: String? = null,
    val faceCheckMessage: String? = null,
    /** P03 头栏副标题用：地标名（仅展示，不参与业务判定） */
    val landmarkName: String? = null,
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

    /** 续用旧草稿：重新导航到旧草稿对应的地标 + attemptId（C-2）。 */
    data class NavigateToDraft(val landmarkId: String, val attemptId: String) : CameraEffect
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
    /** 拍摄前的候选尝试（尚未 commit）：拍摄成功才采用，失败/取消即丢弃、保留官方上下文。 */
    private var candidateAttempt: AttemptSnapshot? = null

    init {
        loadLandmarkAndDraft()
    }

    private fun loadLandmarkAndDraft() {
        viewModelScope.launch {
            landmark = when (val result = landmarkRepository.getLandmark(landmarkId)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> null
            }
            mutableUiState.update { it.copy(landmarkName = landmark?.name) }
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
                                pendingDraftLandmarkId = saved.landmarkId,
                                pendingDraftAttemptId = saved.id,
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
        // C-1：恢复「是否已发送」事实——明确失败的 FAILED（已发送）重拍须换新 ID，上传失败（未发送）可同 attempt 重试
        requestSent = saved.requestSent
        // F1：无条件恢复 uploadState；照片缺失只影响预览，不影响「已发出/未知」状态与查询恢复入口
        val path = saved.localPhoto?.absolutePath
        val patch = CheckInFlowRules.buildRecoveredDraftPatch(
            uploadState = saved.uploadState,
            localPhotoPath = path,
            photoExists = path != null && File(path).exists(),
        )
        mutableUiState.update {
            it.copy(
                photoPath = patch.photoPath,
                uploadState = patch.uploadState,
                restoredDraft = patch.restoredDraft,
                errorMessage = patch.errorMessage,
            )
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
            CameraAction.KeepPendingDraft -> keepPendingDraft()
        }
    }

    /** 「继续上一个」：真正续用旧草稿，导航到旧地标 + 原 attemptId（C-2）。 */
    private fun keepPendingDraft() {
        val landmarkId = mutableUiState.value.pendingDraftLandmarkId ?: return
        val attemptId = mutableUiState.value.pendingDraftAttemptId ?: return
        effectChannel.trySend(CameraEffect.NavigateToDraft(landmarkId = landmarkId, attemptId = attemptId))
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
            // 开拍动作发生时重新取位 + 重新门控（不依赖缓存 gate），通过后进入候选准备
            val (location, gate) = fetchLocationAndGate()
            if (gate !is CheckInGateResult.Ready) {
                mutableUiState.update {
                    it.copy(gate = gate, errorMessage = (gate as CheckInGateResult.Blocked).reason.message)
                }
                return@launch
            }

            // 准备候选尝试（C-4）：已发送的 attempt 不可复用 → 换新候选 ID；否则沿用当前 attemptId。
            // 需领 challenge 时用候选 attemptId 领取（challenge 拍前绑定 attempt，D 契约）；
            // 候选不落正式字段、不覆盖草稿，拍摄成功才 commit、失败/取消即丢弃。
            val candidateAttemptId = if (CheckInCandidateRules.needsNewAttemptId(requestSent)) {
                "att_" + UUID.randomUUID().toString()
            } else {
                currentAttemptId
            }
            val candidate = if (CheckInCandidateRules.needsNewChallenge(requestSent, hasValidChallenge())) {
                try {
                    val challenge = checkInRepository.createChallenge(landmarkId, candidateAttemptId)
                    AttemptSnapshot(
                        attemptId = candidateAttemptId,
                        challengeId = challenge.id,
                        challengeExpiresAt = challenge.expiresAt,
                        frozenLocation = location,
                    )
                } catch (e: Throwable) {
                    mutableUiState.update {
                        it.copy(isCapturing = false, errorMessage = checkInFailureMessage(e))
                    }
                    return@launch
                }
            } else {
                AttemptSnapshot(
                    attemptId = currentAttemptId,
                    challengeId = challengeId,
                    challengeExpiresAt = challengeExpiresAt,
                    frozenLocation = location,
                )
            }
            candidateAttempt = candidate

            mutableUiState.update {
                it.copy(isCapturing = true, gate = gate, errorMessage = null, faceCheckMessage = null)
            }
            val rotation = attachedPreviewView?.display?.rotation ?: Surface.ROTATION_0
            cameraManager.takePhoto(
                rotation = rotation,
                onCaptured = { file ->
                    // 拍摄成功：commit 候选——采用候选的 attempt/challenge/定位（保持 challenge↔attempt 一致），再替换照片
                    val adopted = candidateAttempt
                    candidateAttempt = null
                    if (adopted != null) {
                        currentAttemptId = adopted.attemptId
                        challengeId = adopted.challengeId
                        challengeExpiresAt = adopted.challengeExpiresAt
                        frozenLocation = adopted.frozenLocation
                    }
                    uploadedPhotoId = null
                    requestSent = false
                    // 新照成功才替换旧照；首拍无旧照
                    val old = mutableUiState.value.photoPath
                    if (old != null && old != file.absolutePath) CameraXManager.deletePhoto(old)
                    mutableUiState.update {
                        it.copy(
                            attemptId = currentAttemptId,
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
                    // 拍摄失败：abort 候选，保留官方上下文（旧 challenge/定位/照片/attempt 全部不变）
                    candidateAttempt = null
                    mutableUiState.update { it.copy(isCapturing = false, errorMessage = e.message) }
                },
            )
        }
    }

    private fun retake() {
        val state = mutableUiState.value
        // F2：进入重拍仅切预览，保留旧照与当前 attempt；未知/处理中/已确认不可进入
        if (!CheckInAttemptRules.canRetake(state.uploadState, state.isProcessing)) {
            if (state.isOutcomeUnknown) {
                mutableUiState.update { it.copy(errorMessage = "提交结果未知，请先重试恢复，不要重拍") }
            }
            return
        }
        // 进入重拍模式：旧照保留，不删除、不换新 attemptId（取消重拍可回旧预览，F2）
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

    /** 提交入口：先「已发出/未知」进入查询恢复，再「明确失败」换新尝试，最后首次提交（F1/R4）。 */
    private fun submitFlow() {
        val state = mutableUiState.value
        when (CheckInFlowRules.submitRoute(state.uploadState, requestSent, state.isProcessing)) {
            SubmitRoute.RETURN -> Unit
            SubmitRoute.NEW_ATTEMPT -> beginNewAttempt("已放弃上次失败尝试，请重新拍照")
            SubmitRoute.RECOVER -> recoverAttempt()
            SubmitRoute.FIRST_SUBMIT -> {
                val photoPath = state.photoPath ?: return
                firstSubmit(photoPath)
            }
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
            // C-1：立即持久化「已发送」事实，避免 submit 期间进程被杀后草稿丢失 requestSent
            persistDraft(
                localPhoto = LocalPhotoRef(
                    processed.file.absolutePath,
                    "image/jpeg",
                    processed.file.length(),
                ),
                uploadState = UploadState.UPLOADED,
            )
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
                    // F3：终态保证成立前 recover 只返回 unknownResult=true（NotFound 亦未知）；
                    // 保留 CONFIRMED_ABSENT 分支作为「D 落地可靠终态后放行」的扩展点
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
     * 可靠确认旧尝试已结束（明确未成功）后，删除旧照、原子生成新 attemptId 并重置冻结状态（R2）。
     * 旧未知尝试不得调用本方法（调用方须先确认 resolution 为 CONFIRMED_ABSENT）。
     */
    private fun beginNewAttempt(message: String) {
        val oldPath = mutableUiState.value.photoPath
        CameraXManager.deletePhoto(oldPath)
        resetAttemptContext()
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

    /**
     * 可靠结束旧尝试（明确未成功）后，原子生成新 attemptId 并清空本次尝试的冻结状态（不触碰照片/预览/uploadState）。
     * 新 ID 必须在此生成——否则清掉 requestSent 后，capture 候选看到 requestSent=false 会继续复用旧 ID，
     * 触发 D 的幂等冲突。后续 capture 复用此新 ID 领新 challenge，challenge/上传/POST 全程同一 ID。
     */
    private fun resetAttemptContext() {
        currentAttemptId = "att_" + UUID.randomUUID().toString()
        challengeId = null
        challengeExpiresAt = null
        uploadedPhotoId = null
        frozenLocation = null
        requestSent = false
        draftCreatedAt = null
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
                    requestSent = requestSent,
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


/**
 * P03 拍照打卡 —— 视觉层严格按交付原型 .fig（P03 拍照打卡 · 取景 / 拍后预览）实现：
 * 深色全幅取景（#0B0F0E）+ 顶栏（拍照打卡 / X · 同框打卡 #9FB5AC）
 * → 状态 chips「距目标约 Xm」「定位正常」（#0A1210）→ 三分构图线 +「地标完整入镜」标
 * → 底部渐变 + 引导语「把地标放在上半部分，人站在它前面」+ 72dp 快门圆 +「轻点拍摄」
 * 拍后预览态：「已拍摄 · 还没有提交」+ 确认文案 + 重拍 / 确认提交。
 * 业务（门控/幂等/草稿恢复）全部沿用 CameraViewModel，本文件不新增判定。
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    onResult: (String, String) -> Unit,
    onOpenDraft: (String, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CameraEffect.NavigateToResult -> onResult(effect.checkInId, effect.landmarkId)
                is CameraEffect.NavigateToDraft -> onOpenDraft(effect.landmarkId, effect.attemptId)
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

    val inPreview = state.photoPath != null && !state.retaking
    val landmarkName = state.landmarkName ?: ""

    // 深底页面：状态栏「时间/电量」用浅色图标，否则会和 #10201B 顶栏融成一片看不见
    StatusBarIconsDark(darkIcons = false)

    // contentWindowInsets 归零：顶部由 PageHeader 自己做 statusBarsPadding（避免双重 inset），
    // 底部操作区已有 navigationBarsPadding
    Scaffold(
        containerColor = Color(0xFF0B0F0E),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF0B0F0E)),
        ) {
            // 顶栏（原型 60dp #10201B：拍照打卡 18 Bold #EAF3EE / 副题 11.5 #9FB5AC）
            PageHeader(
                title = "拍照打卡",
                subtitle = when {
                    state.isOutcomeUnknown -> "$landmarkName · 结果确认中"
                    state.isProcessing -> "$landmarkName · 服务端正在确认"
                    state.retaking -> "$landmarkName · 重新取景"
                    inPreview -> "$landmarkName · 确认人物与地标同框"
                    !state.hasCameraPermission -> "需要相机权限"
                    else -> "$landmarkName · 同框打卡"
                },
                onBack = onBack,
                dark = true,
            )

            // 全幅取景/预览区
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0B0F0E)),
            ) {
                when {
                    !state.hasCameraPermission -> PermissionHint(
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )

                    state.retaking || state.photoPath == null -> LivePreview(viewModel, lifecycleOwner)
                    else -> {
                        val captured = state.photoPath
                        if (captured != null) PhotoPreview(captured)
                    }
                }

                // 取景态叠加层
                if (!inPreview && state.hasCameraPermission) {
                    Row(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 58.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val distance = when (val g = state.gate) {
                            is CheckInGateResult.Ready -> g.distanceMeters
                            is CheckInGateResult.Blocked -> g.distanceMeters
                            else -> null
                        }
                        SurfaceChip(text = distance?.let { "距目标约 ${it.toInt()} m" } ?: "定位中…")
                        SurfaceChip(
                            text = when (state.gate) {
                                is CheckInGateResult.Ready -> "定位正常"
                                else -> "定位未就绪"
                            },
                        )
                    }

                    FramingGuides(
                        modifier = Modifier.fillMaxSize(),
                        labelAlpha = if (state.canShoot) 1f else 0.4f,
                    )

                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(170.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color(0xB3000000),
                                ),
                            ),
                    )
                    Text(
                        "把地标放在上半部分，人站在它前面",
                        fontSize = 12.5.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 150.dp),
                    )
                }

                // 错误/提示横幅（暗色半透明底）
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.isOutcomeUnknown && state.errorMessage == null) {
                        DarkNote("结果确认中：正在查询上次提交的结果，请勿重复提交")
                    }
                    if (state.restoredDraft) {
                        DarkNote("已恢复上次未完成的拍摄，可直接继续")
                    }
                    state.gateMessage?.takeIf { state.photoPath == null || state.retaking }?.let { DarkNote(it) }
                    state.faceCheckMessage?.let { DarkNote(it) }
                    state.errorMessage?.let { DarkNote(it) }
                }
            }

            // 底部操作区
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10201B))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    // F1：结果未知只提供「重试恢复」，不显示拍照/重拍入口
                    state.isOutcomeUnknown -> {
                        AppButton(
                            text = if (state.isProcessing) "正在恢复…" else "重试恢复",
                            onClick = { viewModel.onAction(CameraAction.Confirm) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isProcessing,
                        )
                    }

                    inPreview -> {
                        // 原型拍后预览：说明行 + 重拍（描边）/ 确认提交（主色）
                        Text(
                            "已拍摄 · 还没有提交",
                            fontSize = 12.sp,
                            color = Color.White,
                        )
                        Text(
                            "确认人物与${landmarkName.ifEmpty { "地标" }}同框后再提交。现在还没有发出去，重拍不会留下记录。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Color(0xFFC9D6D1),
                        )
                        Spacer(Modifier.height(2.dp))
                        AppButton(
                            text = "重拍",
                            onClick = { viewModel.onAction(CameraAction.Retake) },
                            modifier = Modifier.fillMaxWidth(),
                            style = AppButtonStyle.Outline,
                            enabled = !state.isCapturing && !state.isProcessing && !state.isOutcomeUnknown,
                        )
                        AppButton(
                            text = when {
                                state.isProcessing -> "正在提交打卡…"
                                state.uploadState == UploadState.UNKNOWN -> "重试恢复"
                                state.uploadState == UploadState.FAILED -> "重试"
                                else -> "确认提交"
                            },
                            onClick = { viewModel.onAction(CameraAction.Confirm) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isCapturing && !state.isProcessing,
                        )
                    }

                    else -> {
                        // 取景态：72dp 白色快门圆（原型 hot:// shoot）+「轻点拍摄」
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    if (state.canShoot && !state.isCapturing) Color.White else Color(0x61FFFFFF),
                                    CircleShape,
                                )
                                .border(3.dp, Color(0x3300634D), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { viewModel.onAction(CameraAction.Capture) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.isCapturing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF00634D),
                                )
                            }
                        }
                        Text("轻点拍摄", fontSize = 11.sp, color = Color(0xFF9FB5AC))
                        AppButton(
                            text = "重新定位",
                            onClick = { viewModel.onAction(CameraAction.RefreshLocation) },
                            modifier = Modifier.fillMaxWidth(),
                            style = AppButtonStyle.Outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceChip(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = Color.White,
        modifier = Modifier
            .background(Color(0xF00A1210), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** 三分构图线 + 中心「地标完整入镜」标签（原型白 1px 线 + #2E5145 字白底标） */
@Composable
private fun FramingGuides(modifier: Modifier = Modifier, labelAlpha: Float) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val lineColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f)
        val w = size.width
        val h = size.height
        listOf(0.351f, 0.640f).forEach { fx ->
            drawLine(
                lineColor,
                androidx.compose.ui.geometry.Offset(w * fx, h * 0.18f),
                androidx.compose.ui.geometry.Offset(w * fx, h * 0.86f),
                1.dp.toPx(),
            )
        }
        listOf(0.335f, 0.547f).forEach { fy ->
            drawLine(
                lineColor,
                androidx.compose.ui.geometry.Offset(w * 0.06f, h * fy),
                androidx.compose.ui.geometry.Offset(w * 0.94f, h * fy),
                1.dp.toPx(),
            )
        }
    }
    Box(modifier = modifier) {
        Text(
            "地标完整入镜",
            fontSize = 10.5.sp,
            color = Color(0xFF2E5145),
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(labelAlpha)
                .background(Color.White, RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DarkNote(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = Color(0xFFC9D6D1),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE60A1210), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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
                TextButton(onClick = onKeep) { Text("继续上一个") }
            }
        },
        dismissButton = {
            if (canDiscard) {
                TextButton(onClick = onKeep) { Text("继续上一个") }
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
            Icon(
                Icons.Rounded.NoPhotography,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(44.dp),
            )
            Text("需要相机权限才能拍照", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            AppButton(text = "授予权限", onClick = onRequestPermission)
        }
    }
}
