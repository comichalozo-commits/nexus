package de.nexus.agent.core.domain.tools

import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShellTool : BaseTool() {
    override val name: String = "shell_execute"
    override val description: String = "Führe einen Shell-Befehl aus. Achtung: Nur sichere, schreibgeschützte Befehle sollten genutzt werden. Max. Ausführungszeit: 30 Sekunden."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "command" to ToolProperty(
                type = "string",
                description = "Der auszuführende Shell-Befehl"
            ),
            "timeout_seconds" to ToolProperty(
                type = "integer",
                description = "Timeout in Sekunden (Standard: 15, Max: 30)"
            )
        ),
        required = listOf("command")
    )

    private val blockedCommands = setOf(
        "rm", "rmdir", "mv", "dd", "mkfs", "fdisk",
        "wget", "curl", "nc", "netcat",
        "chmod", "chown", "su", "sudo"
    )

    override suspend fun execute(arguments: String): String {
        val command = getStringParam(arguments, "command").trim()
        val timeout = getIntParam(arguments, "timeout_seconds", 15).coerceIn(1, 30)

        if (command.isBlank()) {
            return "Fehler: Befehl darf nicht leer sein."
        }

        // Check for blocked commands
        val firstWord = command.split("\\s+".toRegex()).firstOrNull() ?: ""
        if (firstWord in blockedCommands) {
            return "Fehler: Befehl '$firstWord' ist aus Sicherheitsgründen gesperrt."
                .replace("firstWord", firstWord)
        }

        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder()
                    .command("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()

                val completed = process.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)

                if (!completed) {
                    process.destroyForcibly()
                    return@withContext "Fehler: Befehl nach $timeout Sekunden abgebrochen (Timeout)."
                }

                val output = process.inputStream.bufferedReader().readText().trim()
                val exitCode = process.exitValue()

                if (output.isEmpty()) {
                    "Befehl ausgeführt (Exit-Code: $exitCode, keine Ausgabe)"
                } else {
                    val truncatedOutput = output.take(2000)
                    val suffix = if (output.length > 2000) "\n... (gekürzt)" else ""
                    "Exit-Code: $exitCode\nAusgabe:\n$truncatedOutput$suffix"
                }
            } catch (e: SecurityException) {
                "Berechtigungsfehler: ${e.message}"
            } catch (e: Exception) {
                "Fehler bei der Ausführung: ${e.message}"
            }
        }
    }
}
