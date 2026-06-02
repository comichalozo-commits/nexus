package de.nexus.agent.core.domain.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool for opening URLs and searching the web in the browser.
 */
@Singleton
class BrowserTool @Inject constructor(
    @ApplicationContext private val context: Context
) : BaseTool() {

    override val name: String = "browser"
    override val description: String =
        "Open URLs in the browser or search the web. " +
            "Actions: open (open a URL in the default browser or Chrome Custom Tab), search (perform a web search and open results)."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "action" to ToolProperty(
                type = "string",
                description = "The action to perform",
                enum = listOf("open", "search")
            ),
            "url" to ToolProperty(
                type = "string",
                description = "The URL to open (required for open action). Must include http:// or https://"
            ),
            "query" to ToolProperty(
                type = "string",
                description = "The search query (required for search action)"
            ),
            "use_custom_tab" to ToolProperty(
                type = "string",
                description = "Use Chrome Custom Tab instead of default browser (true/false, default: true)"
            )
        ),
        required = listOf("action")
    )

    override suspend fun execute(arguments: String): String {
        val action = getStringParam(arguments, "action")

        return when (action) {
            "open" -> {
                val url = getStringParam(arguments, "url")
                val useCustomTab = getBooleanParam(arguments, "use_custom_tab", true)
                if (url.isBlank()) {
                    return "Error: url is required for open action."
                }
                openUrl(url, useCustomTab)
            }
            "search" -> {
                val query = getStringParam(arguments, "query")
                if (query.isBlank()) {
                    return "Error: query is required for search action."
                }
                searchWeb(query)
            }
            else -> "Error: Unknown action '$action'. Use open or search."
        }
    }

    private fun openUrl(url: String, useCustomTab: Boolean): String {
        return try {
            val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            val uri = Uri.parse(normalizedUrl)

            if (useCustomTab) {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(context, uri)
                "Opened in Chrome Custom Tab: $normalizedUrl"
            } else {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened in browser: $normalizedUrl"
            }
        } catch (e: Exception) {
            "Error opening URL: ${e.message}"
        }
    }

    private suspend fun searchWeb(query: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://www.google.com/search?q=$encodedQuery"

                // Open the search in browser
                try {
                    val customTabsIntent = CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build()
                    val uri = Uri.parse(searchUrl)
                    customTabsIntent.launchUrl(context, uri)
                } catch (_: Exception) {
                    // Fallback to default browser
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }

                // Also fetch top results for the agent
                val url = URL("https://www.google.com/search?q=$encodedQuery")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val html = connection.inputStream.bufferedReader().readText()
                    val results = parseGoogleResults(html)
                    if (results.isNotEmpty()) {
                        "Search results for \"$query\":\n\n${results.joinToString("\n\n")}\n\nOpened in browser: $searchUrl"
                    } else {
                        "Search opened in browser: $searchUrl"
                    }
                } else {
                    "Search opened in browser: $searchUrl"
                }
            } catch (e: Exception) {
                "Error searching: ${e.message}"
            }
        }
    }

    private fun parseGoogleResults(html: String): List<String> {
        val results = mutableListOf<String>()
        // Simple regex-based extraction of search result titles and URLs
        val linkRegex = Regex("<a[^>]*href=\"(/url\\?q=|https?://)([^\"]*?)\"[^>]*>.*?<h3[^>]*>(.*?)</h3>", RegexOption.DOT_MATCHES_ALL)
        linkRegex.findAll(html).take(5).forEach { match ->
            val rawUrl = match.groupValues[2]
            val title = match.groupValues[3].replace(Regex("<[^>]*>"), "").trim()
            val cleanUrl = rawUrl.split("&").first()
            if (title.isNotBlank() && cleanUrl.startsWith("http")) {
                results.add("• $title\n  $cleanUrl")
            }
        }
        return results
    }
}
