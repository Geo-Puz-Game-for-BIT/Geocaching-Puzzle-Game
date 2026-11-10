package com.bitse.geotreasure.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiEndpointTest {
    @Test
    fun `keeps the api v1 base when already configured`() {
        assertEquals(
            "https://example.test/api/v1",
            normalizedApiBaseUrl("https://example.test/api/v1/"),
        )
    }

    @Test
    fun `adds api v1 once when only the host is configured`() {
        assertEquals(
            "https://example.test/api/v1",
            normalizedApiBaseUrl("https://example.test/"),
        )
    }
}
