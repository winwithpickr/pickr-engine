package com.winwithpickr.core.pipeline

/**
 * Shared context passed through the pipeline. Carries IDs, limits,
 * and the audit log. Platform-specific data is passed via the
 * [extras] map to avoid coupling core to any platform.
 */
data class PipelineContext(
    val giveawayId: String,
    val hostId: String,
    val targetId: String,
    val maxEntries: Int,
    val auditLog: PoolAuditLog? = null,
    val extras: Map<String, Any> = emptyMap(),
)

interface PoolAuditLog {
    suspend fun logStep(giveawayId: String, step: String, entryCount: Int? = null, detail: Any? = null)
}
