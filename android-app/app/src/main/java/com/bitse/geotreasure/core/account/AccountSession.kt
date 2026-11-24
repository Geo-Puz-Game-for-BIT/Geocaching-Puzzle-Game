package com.bitse.geotreasure.core.account

import kotlinx.coroutines.flow.StateFlow

/**
 * 账号会话（C↔D1 契约点）。C 不另维护全局登录态，统一依赖此接口：
 * - 动态鉴权：CheckInApi 每次请求经 [currentAuth] 取当前 token；
 * - 存储分区：store 监听 [context] 的 accountId 变化切换命名空间；
 * - 旧回调隔离：发起请求经 [currentIdentity] 冻结身份，响应时与当前身份比对。
 *
 * D1 落地真实登录/登出/校验后提供实现；当前由 DemoAccountSession 兜底（映射 BuildConfig）。
 */
interface AccountSession {
    /** 当前账号上下文快照（账号、状态、鉴权）。 */
    val context: StateFlow<AccountContext>

    /** 当前有效鉴权（未登录/锁定/失效/离线返回 null）。 */
    fun currentAuth(): AccountAuth?

    /** 当前账号身份（用于冻结请求绑定），未登录返回 null。 */
    fun currentIdentity(): AccountIdentity?
}

/** 动态鉴权 token 提供器：网络层每次请求取当前有效 token，未登录返回 null。 */
fun interface AuthTokenProvider {
    fun currentToken(): String?
}

/** 把 [AccountSession] 适配为 [AuthTokenProvider]。 */
fun AccountSession.asTokenProvider(): AuthTokenProvider = AuthTokenProvider { currentAuth()?.token }
