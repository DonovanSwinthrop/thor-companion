package com.pokedaisy.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The docs reader: the API's JSON parses into blocks, and DocsSearch ranks website/test/docsearch-cases.json
 * the way the website's search (docsearch.js, docsearch.test.js) does - the two engines must agree.
 */
class DocsSearchTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }
    private val cases = JSONObject(File(root, "website/test/docsearch-cases.json").readText())
    private val manual = DocsApi.parse(cases.getJSONObject("docs").toString())

    @Test
    fun parsesTheApi() {
        assertEquals(4, manual.pages.size)
        assertEquals(listOf("fast-forward", "saves", "screen", "cheats"), manual.sections.single().pages)
        val ff = manual.page("fast-forward")!!
        assertEquals(DocsApi.Block.Heading(2, "smart-mode", "Smart mode"), ff.blocks[1])
        val saves = manual.page("saves")!!.blocks.last() as DocsApi.Block.Bullets
        assertTrue(!saves.ordered && saves.items.single().single() is DocsApi.Block.Paragraph)
        val table = manual.page("screen")!!.blocks.last() as DocsApi.Block.Table
        assertTrue(table.rows.single().first().single().code)
        assertEquals("warning", (manual.page("cheats")!!.blocks.single() as DocsApi.Block.Note).kind)
        assertEquals("saves", manual.next("fast-forward")?.slug)
        assertEquals(null, manual.previous("fast-forward"))
    }

    @Test
    fun ranksLikeTheWebsite() {
        val search = DocsSearch(manual)
        val list = cases.getJSONArray("cases")
        for (i in 0 until list.length()) {
            val c = list.getJSONObject(i)
            val q = c.getString("q")
            val hits = search.search(q)
            if (c.isNull("top")) assertTrue("\"$q\" should find nothing", hits.isEmpty())
            else assertEquals("\"$q\"", c.getString("top"), hits.firstOrNull()?.let { it.page + (it.anchor?.let { a -> "#$a" } ?: "") })
        }
    }

    @Test
    fun wordsStemsTypos() {
        assertEquals(listOf("fast", "forward", "pokemon", "battle"), DocsSearch.terms("How do I fast-forward the Pokémon battles?"))
        assertEquals("cheat", DocsSearch.stem("cheating"))
        assertEquals(1, DocsSearch.distance("shader", "shdaer", 2))
    }

    @Test
    fun snippetsMarkTheMatch() {
        val hit = DocsSearch(manual).search("television").first()
        val marked = hit.marks.map { hit.snippet.substring(it.first, it.last + 1) }
        assertEquals(listOf("television"), marked)
    }
}
