package com.bitse.geotreasure.core.account

/**
 * 账号身份：稳定账号标识 + 会话代次。
 * 代次在每次登录 / 换号时自增，用于「迟到响应隔离」——旧代次的响应不得更新新会话页面。
 */
data class AccountIdentity(
    val accountId: String,
    val sessionGeneration: Long,
)

/**
 * 鉴权上下文：请求要携带的 token + 所属账号身份。
 * token 仅用于 `Authorization: Bearer <token>`，不凭请求体 userId 替代服务端判权。
 */
data class AccountAuth(
    val token: String,
    val identity: AccountIdentity,
)

/** 账号会话状态。 */
enum class AccountState {
    /** 未登录。 */
    SIGNED_OUT,

    /** 登录进行中。 */
    SIGNING_IN,

    /** 已登录且已联网验证。 */
    SIGNED_IN,

    /** 主动锁定（应用重启不自动恢复进入）。 */
    LOCKED,

    /** 会话失效（异地登录 / 服务端拒绝），须重新登录。 */
    INVALIDATED,

    /** 已登录但暂时断网（离线只读，未验证）。 */
    OFFLINE,
}

/**
 * 当前账号上下文快照（供 UI 与 store 观察）。
 * 单一事实源：前端页面不各自维护一份「已登录/已切换」判断。
 */
data class AccountContext(
    val identity: AccountIdentity? = null,
    val state: AccountState = AccountState.SIGNED_OUT,
    /** [state] 为 SIGNED_IN 时非空，携带请求 token。 */
    val auth: AccountAuth? = null,
) {
    /** 是否具备受保护联网操作资格（提交/上传/恢复查询）。 */
    val canUseProtected: Boolean get() = state == AccountState.SIGNED_IN && auth != null

    /** 是否允许离线只读本人缓存（有本地账号、未锁定）。 */
    val canReadCacheOffline: Boolean get() =
        identity != null && (state == AccountState.OFFLINE)

    /** 是否被锁定或明确失效（不能经草稿入口绕过门禁）。 */
    val isBlocked: Boolean get() = state == AccountState.LOCKED || state == AccountState.INVALIDATED

    companion object {
        val SIGNED_OUT = AccountContext(state = AccountState.SIGNED_OUT)
    }
}
