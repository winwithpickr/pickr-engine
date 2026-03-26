package dev.pickrtweet.core.models

data class TierConfig(
    val maxEntries: Int,
    val maxWinners: Int,
    val allowedConditions: Set<String>,
    val followerCheck: Boolean,
    val followAccountsCheck: Boolean,
    val fraudFilter: Boolean,
    val scheduledPicks: Boolean,
    val permalink: Boolean,
    val watermark: Boolean,
    val monthlyPickLimit: Int,           // included picks per month
    val overageRate: Int?,               // cents per extra pick, null = hard cap (free tier)
)
