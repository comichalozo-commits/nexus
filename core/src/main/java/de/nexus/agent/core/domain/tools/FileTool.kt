package de.nexus.agent.core.domain.tools

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileTool(
    @ApplicationContext private val context: Context
) : BaseTool() {
    override val name: String = "file_operation"
    override val description: String = "Lesen und Schreiben von Dateien auf dem Gerät. Unterstützte operationen: read, write, list, delete, exists."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "operation" to ToolProperty(
                type = "string",
                description = "Die auszuführende Operation",
                enum = listOf("read", "write", "list", "delete", "exists", "append", "info")
            ),
            "path" to ToolProperty(
                type = "string",
                description = "Der Dateipfad (relativ zum Arbeitsverzeichnis oder absolut)"
            ),
            "content" to ToolProperty(
                type = "string",
                description = "Der Inhalt zum Schreiben (nur bei write/append)"
            ),
            "encoding" to ToolProperty(
                type = "string",
                description = "Zeichensatz (Standard: UTF-8)"
            )
        ),
        required = listOf("operation", "path")
    )

    private fun resolvePath(path: String): File {
        return if (path.startsWith("/")) {
            File(path)
        } else {
            File(context.filesDir, path)
        }
    }

    override suspend fun execute(arguments: String): String {
        val operation = getStringParam(arguments, "operation")
        val path = getStringParam(arguments, "path")
        val content = getStringParam(arguments, "content")

        if (operation.isBlank() || path.isBlank()) {
            return "Fehler: Operation und Pfad sind erforderlich."
        }

        return withContext(Dispatchers.IO) {
            try {
                val file = resolvePath(path)

                when (operation) {
                    "read" -> {
                        if (!file.exists()) return@withContext "Fehler: Datei nicht gefunden: $path"
                        if (file.length() > 1_000_000) {
                            return@withContext "Fehler: Datei zu groß (> 1MB). Erste 10000 Zeichen:\n" +
                                file.readText().take(10000)
                        }
                        "Inhalt von $path:\n${file.readText()}"
                    }
                    "write" -> {
                        file.parentFile?.mkdirs()
                        file.writeText(content)
                        "Datei geschrieben: $path (${content.length} Zeichen)"
                    }
                    "append" -> {
                        file.parentFile?.mkdirs()
                        file.appendText(content)
                        "Inhalt angehängt an $path"
                    }
                    "list" -> {
                        val dir = if (file.isDirectory) file else file.parentFile ?: context.filesDir
                        val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
                        if (files.isEmpty()) {
                            "Verzeichnis ist leer: ${dir.absolutePath}"
                        } else {
                            val listing = files.joinToString("\n") { f ->
                                val type = if (f.isDirectory) "[DIR]" else "[FILE]"
                                val size = if (f.isFile) " (${f.length()} bytes)" else ""
                                "$type ${f.name}$size"
                            }
                            "Inhalt von ${dir.absolutePath}:\n$listing"
                        }
                    }
                    "delete" -> {
                        if (!file.exists()) return@withContext "Fehler: Nicht gefunden: $path"
                        val deleted = file.deleteRecursively()
                        if (deleted) "Gelöscht: $path" else "Fehler beim Löschen: $path"
                    }
                    "exists" -> {
                        val exists = file.exists()
                        val type = when {
                            !exists -> "nicht vorhanden"
                            file.isDirectory -> "Verzeichnis"
                            else -> "Datei (${file.length()} bytes)"
                        }
                        "$path: $type"
                    }
                    "info" -> {
                        if (!file.exists()) return@withContext "Fehler: Nicht gefunden: $path"
                        buildString {
                            appendLine("Pfad: ${file.absolutePath}")
                            appendLine("Typ: ${if (file.isDirectory) "Verzeichnis" else "Datei"}")
                            appendLine("Größe: ${file.length()} bytes")
                            appendLine("Letzte Änderung: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(java.util.Date(file.lastModified()))}")
                            appendLine("Lesbar: ${file.canRead()}")
                            appendLine("Schreibbar: ${file.canWrite()}")
                        }.trimEnd()
                    }
                    else -> "Fehler: Unbekannte Operation: $operation"
                }
            } catch (e: SecurityException) {
                "Berechtigungsfehler: ${e.message}"
            } catch (e: Exception) {
                "Fehler: ${e.message}"
            }
        }
    }
}
