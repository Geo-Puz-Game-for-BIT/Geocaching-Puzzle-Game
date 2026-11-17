package com.bitse.geotreasure.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBaseCancellationTest {
    @Test
    fun `cancellation is rethrown instead of becoming a repository failure`() {
        val error = runCatching {
            runBlocking {
                executeCloudBaseOperation<Unit> {
                    throw CancellationException("cancelled")
                }
            }
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }
}
