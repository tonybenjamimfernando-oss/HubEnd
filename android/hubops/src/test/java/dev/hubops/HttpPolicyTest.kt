package dev.hubops

import dev.hubops.core.net.Http
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpPolicyTest {
    @Test
    fun acceptsHttpsAndNormalizesPath() {
        assertEquals("https://example.com/api", Http.validateHttpsUrl("https://example.com/a/../api").toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPlainHttp() {
        Http.validateHttpsUrl("http://example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmbeddedCredentials() {
        Http.validateHttpsUrl("https://user:password@example.com/api")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFragments() {
        Http.validateHttpsUrl("https://example.com/api#fragment")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingHost() {
        Http.validateHttpsUrl("https:///missing-host")
    }
}
