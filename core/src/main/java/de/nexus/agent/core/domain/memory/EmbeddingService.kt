package de.nexus.agent.core.domain.memory

import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.provider.LlmProviderInterface
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRU cache for embeddings with thread-safe access.
 */
class LruEmbeddingCache(private val maxSize: Int = 100) {
    private val cache = LinkedHashMap<String, FloatArray>(maxSize, 0.75f, true)

    @Synchronized
    fun get(key: String): FloatArray? = cache[key]

    @Synchronized
    fun put(key: String, value: FloatArray) {
        if (cache.size >= maxSize) {
            val oldest = cache.keys.first()
            cache.remove(oldest)
        }
        cache[key] = value
    }

    @Synchronized
    fun clear() = cache.clear()

    @Synchronized
    fun size(): Int = cache.size
}

/**
 * Service responsible for generating and caching text embeddings.
 */
@Singleton
class EmbeddingService @Inject constructor(
    private val llmProvider: LlmProviderInterface
) {
    private val cache = LruEmbeddingCache(maxSize = 100)
    private val mutex = Mutex()

    /**
     * Generates an embedding vector for the given text, using cache when available.
     */
    suspend fun embed(text: String, config: LlmConfig? = null): FloatArray {
        val normalized = text.trim().lowercase()

        // Check cache first
        val cached = mutex.withLock { cache.get(normalized) }
        if (cached != null) return cached

        // Generate via provider
        val vector = llmProvider.embed(text, config)

        // Store in cache
        mutex.withLock { cache.put(normalized, vector) }

        return vector
    }

    /**
     * Generates embeddings for multiple texts.
     * Attempts to use cache for already-embedded texts.
     */
    suspend fun embedBatch(texts: List<String>, config: LlmConfig? = null): List<FloatArray> {
        return texts.map { embed(it, config) }
    }

    /**
     * Computes cosine similarity between two vectors.
     * Returns a value between -1 and 1.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }
        if (a.isEmpty()) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom == 0f) 0f else dotProduct / denom
    }

    /**
     * Finds the most similar vector from a list of candidates.
     * Returns pairs of (candidate index, similarity score) sorted by similarity descending.
     */
    fun findMostSimilar(
        query: FloatArray,
        candidates: List<FloatArray>,
        topK: Int = 5
    ): List<Pair<Int, Float>> {
        return candidates
            .mapIndexed { index, candidate -> index to cosineSimilarity(query, candidate) }
            .sortedByDescending { it.second }
            .take(topK)
    }

    /**
     * Clears the embedding cache.
     */
    suspend fun clearCache() = mutex.withLock { cache.clear() }
}
