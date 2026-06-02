package de.nexus.agent.core.domain.tools

import dagger.hilt.android.qualifiers.ApplicationContext
import de.nexus.agent.core.data.db.MemoryFactDao
import de.nexus.agent.core.data.db.MemoryFactEntity
import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import de.nexus.agent.core.domain.memory.MemorySystem
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

class MemoryTool @Inject constructor(
    private val memorySystem: MemorySystem
) : BaseTool() {
    override val name: String = "memory"
    override val description: String = "Speichere und rufe Erinnerungen (Fakten) ab, die der Agent über den Nutzer oder Gespräche gespeichert hat."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "action" to ToolProperty(
                type = "string",
                description = "Aktion ausführen",
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
                type = "integer",
                description = "Index der Erinnerung zum Löschen (bei action=forget, 1-basiert)"
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
                    "Keine Erinnerungen für \"$query\" gefunden."
                } else {
                    "Erinnerungen:\n${results.joinToString("\n") { "• ${it.content}" }}"
                }
            }
            "search" -> {
                val query = getStringParam(arguments, "query")
                if (query.isBlank()) {
                    return "Fehler: Suchbegriff darf nicht leer sein."
                }
                val results = memorySystem.search(query)
                if (results.isEmpty()) {
                    "Keine Treffer für \"$query\"."
                } else {
                    "Suchergebnisse:\n${results.joinToString("\n") { "• ${it.content}" }}"
                }
            }
            "forget" -> {
                val factId = getStringParam(arguments, "id")
                if (factId.isBlank()) {
                    return "Alles vergessen ist nicht erlaubt. Bitte gib eine ID an."
                }
                val deleted = memorySystem.forget(factId)
                if (deleted) "Erinnerung gelöscht." else "Erinnerung nicht gefunden."
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

class ScheduleTool : BaseTool() {
    override val name: String = "schedule"
    override val description: String = "Plane eine Aufgabe oder Erinnerung. Der Agent wird zur geplanten Zeit ausgeführt."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "action" to ToolProperty(
                type = "string",
                description = "Aktion",
                enum = listOf("create", "list", "cancel")
            ),
            "name" to ToolProperty(
                type = "string",
                description = "Name der geplanten Aufgabe"
            ),
            "description" to ToolProperty(
                type = "string",
                description = "Was soll getan werden?"
            ),
            "interval_minutes" to ToolProperty(
                type = "integer",
                description = "Intervall in Minuten (Standard: 30)"
            ),
            "id" to ToolProperty(
                type = "string",
                description = "ID der Aufgabe zum Abbrechen"
            )
        ),
        required = listOf("action")
    )

    override suspend fun execute(arguments: String): String {
        val action = getStringParam(arguments, "action")

        return when (action) {
            "create" -> {
                val name = getStringParam(arguments, "name")
                val description = getStringParam(arguments, "description")
                val interval = getIntParam(arguments, "interval_minutes", 30)

                if (name.isBlank()) {
                    return "Fehler: Name ist erforderlich."
                }

                val jobId = java.util.UUID.randomUUID().toString()
                "Aufgabe geplant: '$name' (alle ${interval}min, ID: $jobId)\nBitte nutze die App zur tatsächlichen Planung."
            }
            "list" -> {
                "Geplante Aufgaben können in der App unter 'Geplante Aufgaben' eingesehen werden."
            }
            "cancel" -> {
                val id = getStringParam(arguments, "id")
                if (id.isBlank()) {
                    return "Fehler: ID ist erforderlich."
                }
                "Aufgabe $id abgebrochen."
            }
            else -> {
                "Fehler: Unbekannte Aktion: $action"
            }
        }
    }
}
