package dev.hubops

import dev.hubops.data.github.GitHubService
import dev.hubops.data.wordpress.WordPressService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test
    fun githubUser() {
        assertTrue(GitHubService.isValidUser("octocat"))
        assertTrue(GitHubService.isValidUser("a-b-1"))
        assertFalse(GitHubService.isValidUser("../etc"))
        assertFalse(GitHubService.isValidUser("-bad"))
        assertFalse(GitHubService.isValidUser("bad-"))
        assertFalse(GitHubService.isValidUser(""))
        assertTrue(GitHubService.isValidRepository("octocat/hello-world"))
        assertFalse(GitHubService.isValidRepository("octocat/../secret"))
        assertFalse(GitHubService.isValidRepository("octocat"))
        assertTrue(GitHubService.isValidResourceKind("branches"))
        assertFalse(GitHubService.isValidResourceKind("contents/../../secrets"))
    }

    @Test
    fun wordpressSite_normalizes() {
        assertEquals("https://exemplo.com", WordPressService.normalizeSite(" exemplo.com/ "))
        assertEquals("https://blog.pt/wp", WordPressService.normalizeSite("https://blog.pt/wp/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wordpressSite_rejectsHttp() {
        WordPressService.normalizeSite("http://exemplo.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun wordpressSite_rejectsEmbeddedCredentials() {
        WordPressService.normalizeSite("https://user:pass@example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun wordpressSite_rejectsQueryString() {
        WordPressService.normalizeSite("https://example.com/?redirect=other")
    }

    @Test(expected = IllegalArgumentException::class)
    fun wordpressSite_rejectsFragment() {
        WordPressService.normalizeSite("https://example.com/#admin")
    }

    @Test(expected = IllegalArgumentException::class)
    fun wordpressSite_rejectsJunk() {
        WordPressService.normalizeSite("https://a.com/?x=<script>")
    }
}
