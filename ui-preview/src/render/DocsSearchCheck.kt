package render

import com.pokedaisy.app.DocsApi
import com.pokedaisy.app.DocsSearch
import org.json.JSONObject
import java.io.File

/**
 * `gradle docsSearchCheck`: the app's DocsSearch over website/test/docsearch-cases.json, like the app's
 * DocsSearchTest - runnable where the Android SDK isn't (cloud sessions). Exits 1 on a mismatch.
 */
fun main(args: Array<String>) {
    val cases = JSONObject(File(args[0]).readText())
    val search = DocsSearch(DocsApi.parse(cases.getJSONObject("docs").toString()))
    val list = cases.getJSONArray("cases")
    var bad = 0
    for (i in 0 until list.length()) {
        val c = list.getJSONObject(i)
        val q = c.getString("q")
        val got = search.search(q).firstOrNull()?.let { it.page + (it.anchor?.let { a -> "#$a" } ?: "") }
        val want = if (c.isNull("top")) null else c.getString("top")
        if (got != want) { bad++; println("\"$q\": got $got, want $want") }
    }
    println(if (bad == 0) "docs search: ${list.length()} cases agree with the website" else "docs search: $bad of ${list.length()} cases differ")
    System.exit(if (bad == 0) 0 else 1)
}
