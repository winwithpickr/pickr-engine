package com.winwithpickr.core.pipeline

import com.winwithpickr.core.models.Candidate

/**
 * Filters candidates from the pool based on some condition.
 * Mutates the candidates map in-place, removing disqualified entries.
 */
interface PoolFilter<T : Candidate> {
    /** Human-readable name for audit logging (e.g., "follower_filter", "hashtag_filter"). */
    val name: String

    /** Apply the filter, removing ineligible candidates. */
    suspend fun apply(candidates: MutableMap<String, T>, context: PipelineContext)
}
