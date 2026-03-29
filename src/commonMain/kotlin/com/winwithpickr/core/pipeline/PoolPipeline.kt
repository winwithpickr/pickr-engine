package com.winwithpickr.core.pipeline

import com.winwithpickr.core.models.Candidate

/**
 * Generic pool-building pipeline. Fetches candidates from [sources],
 * applies [filters] in order, then caps at [PipelineContext.maxEntries].
 *
 * Platform integrations (Twitter, Discord, etc.) provide their own
 * PoolSource and PoolFilter implementations.
 */
class PoolPipeline<T : Candidate>(
    private val sources: List<PoolSource<T>>,
    private val filters: List<PoolFilter<T>>,
) {

    suspend fun build(context: PipelineContext): PipelineResult<T> {
        val candidates = mutableMapOf<String, T>()

        // Phase 1: Fetch from sources, intersect/union as configured
        for (source in sources) {
            val fetched = source.fetch(context)
            context.auditLog?.logStep(context.giveawayId, "fetch_${source.name}", fetched.size)

            if (source.intersects(context)) {
                candidates.keys.retainAll(fetched.map { it.id }.toSet())
            } else {
                for (c in fetched) {
                    if (c.id != context.hostId) candidates[c.id] = c
                }
            }
        }

        // Phase 2: Apply filters in order
        for (filter in filters) {
            filter.apply(candidates, context)
            context.auditLog?.logStep(context.giveawayId, filter.name, candidates.size)
        }

        // Phase 3: Cap at max entries
        val pool = if (candidates.size > context.maxEntries) {
            candidates.values.take(context.maxEntries)
        } else {
            candidates.values.toList()
        }

        context.auditLog?.logStep(context.giveawayId, "pool_final", pool.size)
        return PipelineResult(users = pool)
    }
}

data class PipelineResult<T : Candidate>(
    val users: List<T>,
    val metadata: Map<String, Any> = emptyMap(),
)
