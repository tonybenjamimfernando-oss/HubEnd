package dev.hubops

import dev.hubops.core.model.Parsers
import dev.hubops.core.model.Source
import dev.hubops.core.model.toFeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    @Test
    fun repos_fromSearchObject() {
        val json = """{"items":[{"full_name":"a/b","description":null,"stargazers_count":7,"language":"Kotlin","html_url":"https://github.com/a/b"},{"description":"sem nome"}]}"""
        val repos = Parsers.repos(json)
        assertEquals(1, repos.size)
        assertEquals("", repos[0].description)
        assertEquals(7, repos[0].stars)
        assertEquals(0, repos[0].forks)
        assertFalse(repos[0].isPrivate)
        assertEquals(Source.GITHUB, repos[0].toFeedItem().source)
    }

    @Test
    fun repos_fromPlainArray() {
        val json = """[{"full_name":"x/y","stargazers_count":1,"html_url":"https://github.com/x/y"}]"""
        assertEquals("x/y", Parsers.repos(json).single().fullName)
    }

    @Test
    fun categories_areParsedAndInvalidEntriesSkipped() {
        val json = """[{"id":3,"name":"Tecnologia &amp; Ciência","count":7,"slug":"tecnologia"},{"id":0,"name":"inválida"},{"id":4,"name":"  "}]"""
        val categories = Parsers.categories(json)
        assertEquals(1, categories.size)
        assertEquals("Tecnologia & Ciência", categories.single().name)
        assertEquals(7, categories.single().count)
    }

    @Test
    fun posts_stripHtml() {
        val json = """[{"id":5,"title":{"rendered":"Ol&aacute; <b>Mundo</b> &amp; co"},"excerpt":{"rendered":"<p>Resumo</p>"},"link":"https://s.pt/p","date":"2026-01-01T10:00:00"}]"""
        val p = Parsers.posts(json).single()
        assertEquals(5L, p.id)
        assertTrue(Parsers.posts("""[{"id":0,"title":{"rendered":"inválido"}}]""").isEmpty())
        assertTrue(p.title.contains("Mundo & co"))
        assertTrue(p.title.startsWith("Olá"))
        assertEquals("Resumo", p.excerpt)
    }

    @Test
    fun stripHtml_removesScriptAndDecodesNumericEntities() {
        val text = Parsers.stripHtml("<p>Olá<script>alert('x')</script> &#x1F680; &hellip;</p>")
        assertEquals("Olá 🚀 …", text)
    }

    @Test
    fun emptyInput_returnsEmpty() {
        assertTrue(Parsers.repos("").isEmpty())
        assertTrue(Parsers.posts("  ").isEmpty())
    }
}
