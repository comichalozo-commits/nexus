package de.nexus.agent.core.domain.memory

import de.nexus.agent.core.data.db.MemoryFactDao
import de.nexus.agent.core.data.db.MemoryFactEntity
import de.nexus.agent.core.data.model.MemoryFact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemorySystem @Inject constructor(
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

@Singleton
class EmbeddingService @Inject constructor() {
    fun generateEmbedding(text: String): FloatArray {
        // Placeholder: In a real app, this would call an on-device ML model
        // or an API to generate embeddings. For now, return a hash-based vector.
        val vectorSize = 128
        val embedding = FloatArray(vectorSize)
        for (i in text.indices) {
            val idx = i % vectorSize
            embedding[idx] = (text.codePointAt(i) % 100) / 100f
        }
        // Normalize
        val magnitude = kotlin.math.sqrt(embedding.sumOf { it * it.toDouble() }).toFloat()
        if (magnitude > 0f) {
            for (i in embedding.indices) {
                embedding[i] /= magnitude
            }
        }
        return embedding
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }
        var dot = 0f
        var magA = 0f
        var magB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            magA += a[i] * a[i]
            magB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(magA.toDouble()).toFloat() * kotlin.math.sqrt(magB.toDouble()).toFloat()
        return if (denom > 0f) dot / denom else 0f
    }
}
