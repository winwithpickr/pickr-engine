package com.winwithpickr.core.pipeline

import com.winwithpickr.core.models.Candidate

/**
 * Fetches candidates from an external source (replies, retweets, reactions, etc.).
 * Returns a list of candidates and whether to merge as intersection or union
 * with the existing pool.
 */
interface PoolSource<T : Candidate> {
    /** Human-readable name for audit logging (e.g., "replies", "retweets"). */
    val name: String

    /** Fetch candidates from this source. */
    suspend fun fetch(context: PipelineContext): List<T>

    /**
     * Whether this source intersects with the existing pool (true)
     * or seeds/unions into it (false). Determined by whether prior
     * sources have been requested.
     */
    fun intersects(context: PipelineContext): Boolean
}
