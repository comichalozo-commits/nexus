package de.nexus.agent.core.domain.tools

import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import de.nexus.agent.core.domain.memory.MemorySystem

class MemoryTool  constructor(
    private val memorySystem: MemorySystem
) : BaseTool() {
    override val name: String = "memory"
    override val description: String = "Speichere und rufe Erinnerungen (Fakten) ab, die der Agent ueber den Nutzer oder Gespraeche gespeichert hat."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "action" to ToolProperty(
                type = "string",
                description = "Aktion ausfuehren",
                enum = listOf("remember", "recall", "search", "forget", "list")
            ),
            "content" to ToolProperty(
                type = "string",
                description = "Der Inhalt zum Speichern (bei action=remember)"
            ),
            "query" to ToolProperty(
                type = "string",
                description = "Suchbegriff (bei action=search/recall)"
            ),
            "id" to ToolProperty(
                type = "string",
                description = "ID der Erinnerung zum Loeschen"
            )
        ),
        required = listOf("action")
    )

    override suspend fun execute(arguments: String): String {
        val action = getStringParam(arguments, "action")

        return when (action) {
            "remember" -> {
                val content = getStringParam(arguments, "content")
                if (content.isBlank()) {
                    return "Fehler: Inhalt darf nicht leer sein."
                }
                val id = memorySystem.remember(content)
                "Erinnerung gespeichert (ID: $id)"
            }
            "recall" -> {
                val query = getStringParam(arguments, "query")
                if (query.isBlank()) {
                    return "Fehler: Suchbegriff darf nicht leer sein."
                }
                val results = memorySystem.recall(query)
                if (results.isEmpty()) {
                    "Keine Erinnerungen fuer \"$query\" gefunden."
                } else {
                    "Erinnerungen:\n${results.joinToString("\n") { "â€¢ ${it.content}" }}"
                }
            }
            "search" -> {
                val query = getStringParam(arguments, "query")
                if (query.isBlank()) {
                    return "Fehler: Suchbegriff darf nicht leer sein."
                }
                val results = memorySystem.search(query)
                if (results.isEmpty()) {
                    "Keine Treffer fuer \"$query\"."
                } else {
                    "Suchergebnisse:\n${results.joinToString("\n") { "â€¢ ${it.content}" }}"
                }
            }
            "forget" -> {
                val factId = getStringParam(arguments, "id")
                if (factId.isBlank()) {
                    return "Fehler: ID ist erforderlich."
                }
                val deleted = memorySystem.forget(factId)
                if (deleted) "Erinnerung geloescht." else "Erinnerung nicht gefunden."
            }
            "list" -> {
                val facts = memorySystem.getAllFacts()
                if (facts.isEmpty()) {
                    "Keine Erinnerungen gespeichert."
                } else {
                    "Gespeicherte Erinnerungen (${facts.size}):\n" +
                        facts.joinToString("\n") { "${it.id}: ${it.content}" }
                }
            }
            else -> "Fehler: Unbekannte Aktion: $action"
        }
    }
}
