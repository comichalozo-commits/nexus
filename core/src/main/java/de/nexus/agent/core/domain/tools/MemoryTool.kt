package de.nexus.agent.core.domain.tools

import android.content.Context
import android.content.SharedPreferences
import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryTool(
    private val context: Context
) : BaseTool() {
    override val name: String = "memory"
    override val description: String = "Speichere und rufe Erinnerungen (Fakten) ab, die der Agent ueber den Nutzer oder Gespraeche gespeichert hat."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "action" to ToolProperty(
                type = "string",
                description = "Aktion ausfuehren",
                enum = listOf("remember", "recall", "list")
            ),
            "content" to ToolProperty(
                type = "string",
                description = "Der Inhalt zum Speichern (bei action=remember)"
            ),
            "query" to ToolProperty(
                type = "string",
                description = "Suchbegriff (bei action=recall)"
            )
        ),
        required = listOf("action")
    )

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences("nexus_memory", Context.MODE_PRIVATE)
    }

    private fun getAllFacts(): List<Pair<String, String>> {
        val prefs = getPrefs()
        return prefs.all.entries.map { it.key to (it.value as? String ?: "") }.filter { it.second.isNotBlank() }
    }

    override suspend fun execute(arguments: String): String {
        val action = getStringParam(arguments, "action")

        return when (action) {
            "remember" -> {
                val content = getStringParam(arguments, "content")
                if (content.isBlank()) return "Fehler: Inhalt darf nicht leer sein."
                val id = "fact_${System.currentTimeMillis()}"
                getPrefs().edit().putString(id, content).apply()
                "Erinnerung gespeichert (ID: $id)"
            }
            "recall" -> {
                val query = getStringParam(arguments, "query")
                if (query.isBlank()) return "Fehler: Suchbegriff darf nicht leer sein."
                val results = getAllFacts().filter { it.second.contains(query, ignoreCase = true) }
                if (results.isEmpty()) {
                    "Keine Erinnerungen fuer \"$query\" gefunden."
                } else {
                    "Erinnerungen:\n${results.joinToString("\n") { "\u2022 ${it.second}" }}"
                }
            }
            "list" -> {
                val facts = getAllFacts()
                if (facts.isEmpty()) {
                    "Keine Erinnerungen gespeichert."
                } else {
                    "Gespeicherte Erinnerungen (${facts.size}):\n" +
                        facts.joinToString("\n") { "${it.first}: ${it.second}" }
                }
            }
            else -> "Fehler: Unbekannte Aktion: $action"
        }
    }
}
