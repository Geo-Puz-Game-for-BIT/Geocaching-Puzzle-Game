package com.bitse.geotreasure.core.model

/**
 * 分模块恢复的加载状态：地图进度、探索记录、拼图详情各自独立，加载、成功（含真空）、
 * 失败与重试互不污染。某模块失败不注销账号、不清零其他已确认结果。
 *
 * 关键：本地默认空集合不能再代表「全部加载结果」。必须区分「未加载/加载失败」与「确实为空」。
 */
sealed interface LoadState<out T> {
    /** 加载中。 */
    data object Loading : LoadState<Nothing>

    /**
     * 加载成功。[data] 为实际数据（可能为空的集合/列表），
     * [empty] 为 true 表示「确实无数据」（真零），用于 UI 区分「未加载」与「真零」。
     */
    data class Success<T>(val data: T, val empty: Boolean = false) : LoadState<T>

    /** 加载失败，可重试。[error] 为可读原因。 */
    data class Failure(val error: String) : LoadState<Nothing>
}
