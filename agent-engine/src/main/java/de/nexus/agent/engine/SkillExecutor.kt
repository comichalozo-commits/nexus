package de.nexus.agent.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.nexus.agent.core.data.db.SkillDao
import de.nexus.agent.core.data.db.SkillEntity
import de.nexus.agent.core.domain.agent.ToolRegistry
import de.nexus.agent.core.domain.tools.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val skillDao: SkillDao,
    private val toolRegistry: ToolRegistry
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun installSkill(manifestJson: String): Result<String> {
        return try {
            val manifest = json.parseToJsonElement(manifestJson) as? JsonObject
                ?: return Result.failure(Exception("Ungültiges Skill-Manifest"))

            val id = manifest["id"]?.jsonPrimitive?.contentOrNull
                ?: return Result.failure(Exception("Skill-ID fehlt"))
            val name = manifest["name"]?.jsonPrimitive?.contentOrNull
                ?: return Result.failure(Exception("Skill-Name fehlt"))
            val description = manifest["description"]?.jsonPrimitive?.contentOrNull ?: ""

            val skill = SkillEntity(
                id = id,
                name = name,
                description = description,
                manifestJson = manifestJson,
                isEnabled = true,
                installedTimestamp = System.currentTimeMillis()
            )

            skillDao.insertSkill(skill)
            Result.success("Skill '$name' installiert")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uninstallSkill(skillId: String): Result<String> {
        return try {
            val skill = skillDao.getSkillById(skillId)
                ?: return Result.failure(Exception("Skill nicht gefunden"))

            skillDao.deleteSkillById(skillId)
            Result.success("Skill '${skill.name}' deinstalliert")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enableSkill(skillId: String, enabled: Boolean): Result<String> {
        return try {
            val skill = skillDao.getSkillById(skillId)
                ?: return Result.failure(Exception("Skill nicht gefunden"))

            skillDao.updateSkill(skill.copy(isEnabled = enabled))
            val action = if (enabled) "aktiviert" else "deaktiviert"
            Result.success("Skill '${skill.name}' $action")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInstalledSkills(): List<SkillEntity> {
        return skillDao.getAllSkills().let { flow ->
            var result = emptyList<SkillEntity>()
            flow.collect { result = it }
            result
        }
    }

    suspend fun getEnabledSkills(): List<SkillEntity> {
        return skillDao.getEnabledSkills().let { flow ->
            var result = emptyList<SkillEntity>()
            flow.collect { result = it }
            result
        }
    }

    suspend fun executeSkillAction(skillId: String, action: String, params: Map<String, String>): Result<String> {
        return try {
            val skill = skillDao.getSkillById(skillId)
                ?: return Result.failure(Exception("Skill nicht gefunden"))

            if (!skill.isEnabled) {
                return Result.failure(Exception("Skill ist deaktiviert"))
            }

            val manifest = json.parseToJsonElement(skill.manifestJson) as? JsonObject
                ?: return Result.failure(Exception("Ungültiges Manifest"))

            val actions = manifest["actions"]?.jsonObject
                ?: return Result.failure(Exception("Keine Aktionen definiert"))

            val actionDef = actions[action]?.jsonObject
                ?: return Result.failure(Exception("Aktion '$action' nicht gefunden"))

            val toolName = actionDef["tool"]?.jsonPrimitive?.contentOrNull
            val paramMapping = actionDef["params"]?.jsonObject ?: buildJsonObject {}

            val toolArgs = buildJsonObject {
                paramMapping.forEach { (key, mapping) ->
                    val paramKey = mapping.jsonPrimitive.contentOrNull ?: key
                    val paramValue = params[paramKey] ?: ""
                    put(key, JsonPrimitive(paramValue))
                }
            }.toString()

            val tool = toolRegistry.getTool(toolName ?: "")
            if (tool != null) {
                val result = tool.execute(toolArgs)
                Result.success(result)
            } else {
                Result.failure(Exception("Tool '$toolName' nicht gefunden"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
