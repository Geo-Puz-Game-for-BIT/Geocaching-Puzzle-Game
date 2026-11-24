package com.bitse.geotreasure.core.account

/**
 * 旧回调隔离纯规则。
 *
 * 请求发起时冻结账号身份（accountId + 代次），响应到达时与当前身份比对；
 * 若账号已切换或会话代次已变化，该响应属于「旧账号/旧会话」，必须丢弃：
 * - 不得更新新账号页面；
 * - 不得把新账号踢回登录页；
 * - 不得用旧 photoId / 旧草稿污染新账号。
 */
object AccountCallbackGuard {

    /** 响应是否已过期（属于旧账号/旧会话），true = 必须丢弃。 */
    fun isStale(binding: AccountIdentity?, current: AccountIdentity?): Boolean {
        // 未绑定身份（无账号时代的 demo 请求）不判 stale
        if (binding == null) return false
        // 当前无身份（已退出）或身份不一致（换号/代次变化）→ 过期
        return current == null ||
            binding.accountId != current.accountId ||
            binding.sessionGeneration != current.sessionGeneration
    }

    /** 便捷构造：冻结发起请求时的账号绑定。 */
    fun bind(identity: AccountIdentity?): AccountIdentity? = identity
}
