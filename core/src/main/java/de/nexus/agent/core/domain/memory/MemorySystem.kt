package de.nexus.agent.core.domain.memory

import de.nexus.agent.core.data.db.MemoryFactDao
import de.nexus.agent.core.data.db.MemoryFactEntity
import de.nexus.agent.core.data.model.MemoryFact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemorySystem  constructor(
    private val memoryFactDao: MemoryFactDao,
    private val embeddingService: EmbeddingService
) {
    fun getAllFactsFlow(): Flow<List<MemoryFact>> {
        return memoryFactDao.getAllFacts().map { entities ->
            entities.map { it.toMemoryFact() }
        }
    }

    suspend fun remember(content: String, source: String = "conversation"): String {
        val fact = MemoryFactEntity(
            id = java.util.UUID.randomUUID().toString(),
            content = content,
            source = source,
            relevance = 1.0f,
            timestamp = System.currentTimeMillis()
        )
        memoryFactDao.insertFact(fact)
        return fact.id
    }

    suspend fun recall(query: String, limit: Int = 10): List<MemoryFact> {
        val facts = memoryFactDao.searchFacts(query)
        return facts
            .map { it.toMemoryFact() }
            .sortedByDescending { calculateRelevance(it.content, query) }
            .take(limit)
    }

    suspend fun search(query: String): List<MemoryFact> {
        return recall(query, limit = 20)
    }

    suspend fun forget(factId: String): Boolean {
        memoryFactDao.deleteFactById(factId)
        return true
    }

    suspend fun getAllFacts(): List<MemoryFact> {
        return memoryFactDao.getRecentFacts(100).map { it.toMemoryFact() }
    }

    suspend fun getFactCount(): Int {
        return memoryFactDao.getFactCount()
    }

    private fun calculateRelevance(content: String, query: String): Float {
        val contentLower = content.lowercase()
        val queryLower = query.lowercase()
        val queryWords = queryLower.split("\\s+".toRegex())

        var score = 0f
        if (contentLower.contains(queryLower)) score += 5f

        queryWords.forEach { word ->
            if (contentLower.contains(word)) score += 1f
        }

        return score
    }

    private fun MemoryFactEntity.toMemoryFact(): MemoryFact {
        return MemoryFact(
            id = this.id,
            content = this.content,
            source = this.source,
            relevance = this.relevance,
            timestamp = this.timestamp
        )
    }
}

