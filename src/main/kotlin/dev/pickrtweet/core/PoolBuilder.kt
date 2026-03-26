package dev.pickrtweet.core

import dev.pickrtweet.core.models.EntryConditions
import dev.pickrtweet.core.models.PoolResult
import dev.pickrtweet.core.models.TierConfig
import dev.pickrtweet.core.models.XUser

interface PoolDataSource {
    suspend fun fetchReplies(tweetId: String, maxResults: Int): List<XUser>
    suspend fun fetchRetweeters(tweetId: String, maxResults: Int): List<XUser>
    suspend fun buildFollowerSet(hostId: String): Pair<Set<String>, Boolean>
}

interface PoolAuditLog {
    suspend fun logStep(giveawayId: String, step: String, entryCount: Int? = null, detail: Any? = null)
}

class PoolBuilder(
    private val dataSource: PoolDataSource,
    private val auditLog: PoolAuditLog? = null,
) {

    suspend fun build(
        parentTweetId: String,
        hostXId: String,
        conditions: EntryConditions,
        tierConfig: TierConfig,
        giveawayId: String,
    ): PoolResult {
        val maxEntries = tierConfig.maxEntries
        val candidates = mutableMapOf<String, XUser>()
        var followHostPartial = false

        // 1. Fetch reply authors (always on — reply is baseline condition)
        if (conditions.reply) {
            val replyUsers = dataSource.fetchReplies(parentTweetId, maxEntries)
            for (u in replyUsers) {
                if (u.id != hostXId) candidates[u.id] = u
            }
            auditLog?.logStep(giveawayId, "fetch_replies", candidates.size)
        }

        // 2. Intersect with retweeters if required
        if (conditions.retweet) {
            val retweeters = dataSource.fetchRetweeters(parentTweetId, maxEntries)
            val rtIds = retweeters.associateBy { it.id }
            auditLog?.logStep(giveawayId, "fetch_retweets", retweeters.size)

            if (conditions.reply) {
                // Intersect: keep only users who replied AND retweeted
                candidates.keys.retainAll(rtIds.keys)
            } else {
                // Retweet-only pool
                for (u in retweeters) {
                    if (u.id != hostXId) candidates[u.id] = u
                }
            }
        }

        // 3. Follower check (Pro+ only, enforced upstream but double-check)
        if (conditions.followHost && tierConfig.followerCheck) {
            val (followerSet, isPartial) = dataSource.buildFollowerSet(hostXId)
            followHostPartial = isPartial
            candidates.keys.retainAll(followerSet)
            auditLog?.logStep(
                giveawayId, "follower_filter",
                candidates.size,
                "partial=$isPartial, followerSetSize=${followerSet.size}"
            )
        }

        // 4. Cap at tier maxEntries
        val pool = if (candidates.size > maxEntries) {
            candidates.values.take(maxEntries)
        } else {
            candidates.values.toList()
        }

        auditLog?.logStep(giveawayId, "pool_final", pool.size)
        return PoolResult(users = pool, followHostPartial = followHostPartial)
    }
}
