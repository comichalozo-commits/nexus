package de.nexus.agent.core.domain.tools

import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebSearchTool : BaseTool() {
    override val name: String = "web_search"
    override val description: String = "Durchsuche das Internet nach aktuellen Informationen. Gib einen Suchbegriff an."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "query" to ToolProperty(
                type = "string",
                description = "Der Suchbegriff oder die Frage für die Websuche"
            ),
            "max_results" to ToolProperty(
                type = "integer",
                description = "Maximale Anzahl Ergebnisse (Standard: 5)",
                enum = null
            )
        ),
        required = listOf("query")
    )

    override suspend fun execute(arguments: String): String {
        val query = getStringParam(arguments, "query")
        val maxResults = getIntParam(arguments, "max_results", 5)

        if (query.isBlank()) {
            return "Fehler: Suchbegriff darf nicht leer sein."
        }

        return withContext(Dispatchers.IO) {
            try {
                // Using DuckDuckGo Lite as a simple search endpoint
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://duckduckgo.com/lite/?q=$encodedQuery")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    return@withContext "Fehler: HTTP $responseCode bei der Websuche."
                }

                val response = connection.inputStream.bufferedReader().readText()

                // Simple HTML parsing for results
                val results = mutableListOf<String>()
                val linkRegex = Regex("<a[^>]*class=\"result-link\"[^>]*href=\"([^\"]*)\"[^>]*>([^<]*)</a>")
                val snippetRegex = Regex("<td[^>]*class=\"result-snippet\"[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)

                val links = linkRegex.findAll(response).take(maxResults).toList()
                val snippets = snippetRegex.findAll(response).take(maxResults).toList()

                links.forEachIndexed { index, match ->
                    val linkUrl = match.groupValues[1].replace(Regex("&amp;"), "&")
                    val title = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                    val snippet = if (index < snippets.size) {
                        snippets[index].groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    } else ""

                    if (title.isNotBlank()) {
                        results.add("• $title\n  $linkUrl\n  ${snippet.take(200)}")
                    }
                }

                if (results.isEmpty()) {
                    // Fallback: try a different approach with the full HTML
                    val titleRegex = Regex("<a[^>]*rel=\"nofollow\"[^>]*>([^<]+)</a>")
                    titleRegex.findAll(response).take(maxResults).forEach { match ->
                        val title = match.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                        if (title.isNotBlank() && title.length > 3) {
                            results.add("• $title")
                        }
                    }
                }

                if (results.isEmpty()) {
                    "Keine Suchergebnisse für \"$query\" gefunden."
                } else {
                    "Suchergebnisse für \"$query\":\n\n${results.joinToString("\n\n")}"
                }
            } catch (e: Exception) {
                "Fehler bei der Websuche: ${e.message}"
            }
        }
    }
}

