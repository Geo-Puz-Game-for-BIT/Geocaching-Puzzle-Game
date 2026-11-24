package com.bitse.geotreasure.data.account

import com.bitse.geotreasure.BuildConfig
import com.bitse.geotreasure.core.account.AccountAuth
import com.bitse.geotreasure.core.account.AccountContext
import com.bitse.geotreasure.core.account.AccountIdentity
import com.bitse.geotreasure.core.account.AccountSession
import com.bitse.geotreasure.core.account.AccountState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 演示账号会话（兜底实现）。D1 落地真实登录/登出/校验前，映射 BuildConfig 的
 * DEMO_AUTH_TOKEN / DEMO_USER_ID 为固定单账号会话（代次 0、状态 SIGNED_IN）。
 * D1 就绪后用真实实现替换，调用方（CheckInApi / store / ViewModel）无需改动。
 */
class DemoAccountSession(
    private val token: String = BuildConfig.DEMO_AUTH_TOKEN,
    accountId: String = BuildConfig.DEMO_USER_ID,
) : AccountSession {
    private val identity: AccountIdentity? = if (token.isNotBlank()) AccountIdentity(accountId, 0L) else null
    private val auth: AccountAuth? = identity?.let { AccountAuth(token, it) }

    private val mutableContext = MutableStateFlow(
        if (auth != null) {
            AccountContext(identity = identity, state = AccountState.SIGNED_IN, auth = auth)
        } else {
            AccountContext.SIGNED_OUT
        },
    )
    override val context: StateFlow<AccountContext> = mutableContext

    override fun currentAuth(): AccountAuth? = auth

    override fun currentIdentity(): AccountIdentity? = identity
}
