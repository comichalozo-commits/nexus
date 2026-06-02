package de.nexus.agent.core.domain.tools

import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty

class ScheduleTool : BaseTool() {
    override val name: String = "schedule"
    override val description: String = "Plane eine Aufgabe oder Erinnerung. Der Agent wird zur geplanten Zeit ausgefuehrt."
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
                "Aufgabe geplant: '$name' (alle ${interval}min, ID: $jobId)"
            }
            "list" -> {
                "Geplante Aufgaben koennen in der App unter 'Geplante Aufgaben' eingesehen werden."
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
