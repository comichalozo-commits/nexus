package de.nexus.agent.core.domain.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

@Tool(
    name = "web_fetch",
    description = "Fetch a web page and extract its content as plain text or markdown.",
    category = "web"
)
class WebFetchTool(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : Tool {

    override val name: String = "web_fetch"
    override val description: String = "Fetch a web page and extract its content as plain text or markdown."
    override val parameterSchema: JsonSchema = JsonSchema(
        properties = mapOf(
            "url" to PropertySchema("string", "HTTP or HTTPS URL to fetch", null),
            "maxChars" to PropertySchema("integer", "Maximum characters to return (default 5000)", 5000),
            "extractMode" to PropertySchema("string", "Extraction mode", "markdown", enum = listOf("markdown", "text"))
        ),
        required = listOf("url")
    )

    override suspend fun execute(params: ToolExecutionParams): ToolResult = withContext(Dispatchers.IO) {
        val url = params.params["url"] as? String
            ?: return@withContext ToolResult.fail("Parameter 'url' is required")
        val maxChars = (params.params["maxChars"] as? Number)?.toInt() ?: 5000
        val extractMode = (params.params["extractMode"] as? String) ?: "markdown"

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext ToolResult.fail("URL must start with http:// or https://")
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext ToolResult.fail("HTTP ${response.code}: ${response.message}")
            }

            val contentType = response.header("Content-Type") ?: ""
            val html = response.body?.string() ?: ""

            val extracted = if (contentType.contains("text/html") || html.trimStart().startsWith("<")) {
                val doc = Jsoup.parse(html, url)
                when (extractMode) {
                    "text" -> extractAsText(doc)
                    else -> extractAsMarkdown(doc)
                }
            } else {
                html
            }

            val truncated = if (extracted.length > maxChars) extracted.take(maxChars) + "\n\n[truncated]" else extracted

            val title = try {
                Jsoup.parse(html).title()
            } catch (_: Exception) { url }

            ToolResult.ok(
                result = truncated,
                metadata = mapOf(
                    "url" to url,
                    "title" to title,
                    "extractedChars" to truncated.length,
                    "extractMode" to extractMode
                )
            )
        } catch (e: Exception) {
            ToolResult.fail("Failed to fetch URL: ${e.message}")
        }
    }

    private fun extractAsText(doc: Document): String {
        doc.select("script, style, nav, footer, header, noscript").remove()
        return doc.body()?.text()?.replace(Regex("\\s+"), " ")?.trim() ?: ""
    }

    private fun extractAsMarkdown(doc: Document): String {
        doc.select("script, style, noscript").remove()
        val sb = StringBuilder()

        val title = doc.title()
        if (title.isNotEmpty()) {
            sb.appendLine("# $title")
            sb.appendLine()
        }

        extractNodeMarkdown(doc.body() ?: return sb.toString(), sb)

        return sb.toString().trimEnd()
    }

    private fun extractNodeMarkdown(element: org.jsoup.nodes.Element, sb: StringBuilder) {
        for (node in element.childNodes()) {
            when (node) {
                is org.jsoup.nodes.TextNode -> {
                    val text = node.text().trim()
                    if (text.isNotEmpty()) sb.append(text).append(" ")
                }
                is org.jsoup.nodes.Element -> {
                    when (node.tagName()) {
                        "h1" -> { sb.appendLine(); sb.appendLine("# ${node.text().trim()}"); sb.appendLine() }
                        "h2" -> { sb.appendLine(); sb.appendLine("## ${node.text().trim()}"); sb.appendLine() }
                        "h3" -> { sb.appendLine(); sb.appendLine("### ${node.text().trim()}"); sb.appendLine() }
                        "h4" -> { sb.appendLine(); sb.appendLine("#### ${node.text().trim()}"); sb.appendLine() }
                        "h5" -> { sb.appendLine(); sb.appendLine("##### ${node.text().trim()}"); sb.appendLine() }
                        "h6" -> { sb.appendLine(); sb.appendLine("###### ${node.text().trim()}"); sb.appendLine() }
                        "p" -> {
                            val text = node.text().trim()
                            if (text.isNotEmpty()) { sb.appendLine(); sb.appendLine(text); sb.appendLine() }
                        }
                        "br" -> sb.appendLine()
                        "ul" -> {
                            sb.appendLine()
                            node.select("li").forEach { sb.appendLine("- ${it.text().trim()}") }
                            sb.appendLine()
                        }
                        "ol" -> {
                            sb.appendLine()
                            node.select("li").forEachIndexed { idx, li -> sb.appendLine("${idx + 1}. ${li.text().trim()}") }
                            sb.appendLine()
                        }
                        "blockquote" -> {
                            sb.appendLine()
                            node.text().trim().lines().forEach { sb.appendLine("> ${it.trim()}") }
                            sb.appendLine()
                        }
                        "pre" -> {
                            val code = node.text().trim()
                            if (code.isNotEmpty()) {
                                sb.appendLine()
                                sb.appendLine("```")
                                sb.appendLine(code)
                                sb.appendLine("```")
                                sb.appendLine()
                            }
                        }
                        "a" -> {
                            val href = node.attr("abs:href")
                            val text = node.text().trim()
                            if (text.isNotEmpty()) sb.append(if (href.isNotEmpty()) "[$text]($href)" else text).append(" ")
                        }
                        "img" -> {
                            val src = node.attr("abs:src")
                            val alt = node.attr("alt")
                            if (src.isNotEmpty()) { sb.appendLine(); sb.appendLine("![$alt]($src)"); sb.appendLine() }
                        }
                        "table" -> {
                            renderTable(node, sb)
                        }
                        "strong", "b" -> {
                            val text = node.text().trim()
                            if (text.isNotEmpty()) sb.append("**$text** ")
                        }
                        "em", "i" -> {
                            val text = node.text().trim()
                            if (text.isNotEmpty()) sb.append("_$text_ ")
                        }
                        else -> extractNodeMarkdown(node, sb)
                    }
                }
            }
        }
    }

    private fun renderTable(table: org.jsoup.nodes.Element, sb: StringBuilder) {
        val rows = table.select("tr")
        if (rows.isEmpty()) return
        sb.appendLine()
        rows.forEachIndexed { idx, row ->
            val cells = row.select("th, td").map { it.text().trim() }
            sb.appendLine("| ${cells.joinToString(" | ")} |")
            if (idx == 0) {
                sb.appendLine("| ${cells.joinToString(" | ") { "---" }} |")
            }
        }
        sb.appendLine()
    }
}
