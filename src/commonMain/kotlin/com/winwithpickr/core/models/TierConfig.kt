package com.winwithpickr.core.models

/**
 * Platform-agnostic tier configuration. Controls entry limits,
 * winner limits, billing, and which feature gates are enabled.
 *
 * Platform-specific feature flags (e.g., followerCheck, fraudFilter)
 * are stored in [features] as string keys so that each platform
 * integration can define its own set without coupling the core.
 */
data class TierConfig(
    val maxEntries: Int,
    val maxWinners: Int,
    val features: Set<String> = emptySet(),
    val permalink: Boolean = true,
    val watermark: Boolean = false,
    val monthlyPickLimit: Int,
    val overageRate: Int? = null,
) {
    fun hasFeature(feature: String): Boolean = feature in features
}
