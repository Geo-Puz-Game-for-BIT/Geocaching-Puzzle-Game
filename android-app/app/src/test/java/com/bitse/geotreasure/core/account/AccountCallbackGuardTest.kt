package com.bitse.geotreasure.core.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧回调隔离纯规则测试：迟到响应按「账号 + 会话代次」判定是否过期。
 * 过期响应必须丢弃，不得更新新账号页面、不得踢回登录页、不得污染新账号草稿。
 */
class AccountCallbackGuardTest {

    @Test
    fun `未绑定身份的请求不判过期`() {
        assertFalse(AccountCallbackGuard.isStale(null, null))
        assertFalse(AccountCallbackGuard.isStale(null, AccountIdentity("a", 0L)))
    }

    @Test
    fun `已退出（当前无身份）时旧请求过期`() {
        assertTrue(AccountCallbackGuard.isStale(AccountIdentity("a", 0L), null))
    }

    @Test
    fun `同账号同代次不过期`() {
        val binding = AccountIdentity("a", 3L)
        val current = AccountIdentity("a", 3L)
        assertFalse(AccountCallbackGuard.isStale(binding, current))
    }

    @Test
    fun `换账号后旧请求过期`() {
        val binding = AccountIdentity("a", 0L)
        val current = AccountIdentity("b", 0L)
        assertTrue(AccountCallbackGuard.isStale(binding, current))
    }

    @Test
    fun `同账号但代次变化后旧请求过期`() {
        val binding = AccountIdentity("a", 0L)
        val current = AccountIdentity("a", 1L)
        assertTrue(AccountCallbackGuard.isStale(binding, current))
    }
}
