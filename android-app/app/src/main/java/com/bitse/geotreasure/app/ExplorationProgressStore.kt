package com.bitse.geotreasure.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bitse.geotreasure.core.account.AccountSession
import com.bitse.geotreasure.core.model.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
 * 探索进度存储：已点亮拼图的地标集合，按账号命名空间分区。
 *
 * - 账号命名空间：`acc.<accountId>.`；无账号（legacy 演示数据）：无前缀。
 * - 旧无归属演示数据归 legacy，不自动分给首个登录人。
 * - 命名空间由 [AccountSession] 驱动：C 不另维护全局登录态。
 * - 以 Preferences DataStore 为唯一真源，应用重启后从磁盘读回（重启恢复）。
 */
class ExplorationProgressStore(
    context: Context,
    private val accountSession: AccountSession,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun scope(accountId: String?): String = accountId?.let { "acc.$it." } ?: ""

    private fun completedIdsKey(accountId: String?): Preferences.Key<Set<String>> =
        stringSetPreferencesKey("${scope(accountId)}completed_landmark_ids")

    private fun currentAccountId(): String? = accountSession.currentIdentity()?.accountId

    @OptIn(ExperimentalCoroutinesApi::class)
    val completedLandmarkIds: StateFlow<Set<String>> = accountSession.context
        .map { it.identity?.accountId }
        .flatMapLatest { accountId ->
            appContext.dataStore.data.map { prefs -> prefs[completedIdsKey(accountId)] ?: emptySet() }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** 分模块恢复：手动重试信号，触发一次「Loading → 重新读取」循环。 */
    private val progressReloadSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * 探索进度的加载状态（拼图详情等按模块消费）。
     *
     * 关键：本地默认空集合不得再代表「全部加载结果」。此处直接映射原始 DataStore 流，
     * 首次落盘值到达前保持 Loading，读取失败降级 Failure，成功后才 Success(empty=真零)；
     * 账号切换或 [retryProgressLoad] 都会重新进入 Loading 并重读。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val completedLandmarkIdsLoadState: StateFlow<LoadState<Set<String>>> =
        combine(accountSession.context, progressReloadSignal.onStart { emit(Unit) }) { ctx, _ ->
            ctx.identity?.accountId
        }
            .distinctUntilChanged()
            .flatMapLatest { accountId ->
                flow<LoadState<Set<String>>> {
                    emit(LoadState.Loading)
                    try {
                        appContext.dataStore.data.collect { prefs ->
                            val ids = prefs[completedIdsKey(accountId)] ?: emptySet()
                            emit(LoadState.Success(ids, empty = ids.isEmpty()))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emit(LoadState.Failure(e.message ?: "加载探索进度失败"))
                    }
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, LoadState.Loading)

    /** 拼图/探索进度加载失败后的手动重试入口。 */
    fun retryProgressLoad() {
        progressReloadSignal.tryEmit(Unit)
    }

    /** 幂等写入（fire-and-forget）。UI 即时更新场景使用。 */
    fun markLandmarkCompleted(landmarkId: String) {
        scope.launch {
            val accountId = currentAccountId()
            appContext.dataStore.edit { prefs ->
                prefs[completedIdsKey(accountId)] = mergeCompletedLandmark(
                    prefs[completedIdsKey(accountId)] ?: emptySet(),
                    landmarkId,
                )
            }
        }
    }

    /**
     * 可等待落盘的幂等写入。成功路径应先 await 此写入再清草稿，
     * 账号变化前保证「服务端成功 → 本地完成集合落盘 → 清草稿」顺序。
     */
    suspend fun markLandmarkCompletedAwait(landmarkId: String) {
        val accountId = currentAccountId()
        appContext.dataStore.edit { prefs ->
            prefs[completedIdsKey(accountId)] = mergeCompletedLandmark(
                prefs[completedIdsKey(accountId)] ?: emptySet(),
                landmarkId,
            )
        }
    }
}
