package dev.pickrtweet.core

import dev.pickrtweet.core.models.EntryConditions
import dev.pickrtweet.core.models.PoolResult
import dev.pickrtweet.core.models.TierConfig
import dev.pickrtweet.core.models.XUser
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

interface PoolDataSource {
    suspend fun fetchReplies(tweetId: String, maxResults: Int): List<XUser>
    suspend fun fetchRetweeters(tweetId: String, maxResults: Int): List<XUser>
    suspend fun fetchQuoteTweets(tweetId: String, maxResults: Int): List<XUser>
    suspend fun buildFollowerSet(hostId: String): Pair<Set<String>, Boolean>
    suspend fun resolveHandle(handle: String): String?
    suspend fun fetchFollowing(userId: String): Set<String>
}

interface PoolAuditLog {
    suspend fun logStep(giveawayId: String, step: String, entryCount: Int? = null, detail: Any? = null)
}

class PoolBuilder(
    private val dataSource: PoolDataSource,
    private val auditLog: PoolAuditLog? = null,
) {

    companion object {
        private val MENTION_REGEX = Regex("""@(\w+)""")
    }

    suspend fun build(
        parentTweetId: String,
        hostXId: String,
        conditions: EntryConditions,
        tierConfig: TierConfig,
        giveawayId: String,
        excludeHandles: Set<String> = emptySet(),
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
            auditLog?.logStep(giveawayId, "fetch_retweets", retweeters.size)
            mergePool(candidates, retweeters, hostXId, hasExistingPool = conditions.reply)
        }

        // 3. Intersect/union with quote tweets if required
        if (conditions.quoteTweet) {
            val quoters = dataSource.fetchQuoteTweets(parentTweetId, maxEntries)
            auditLog?.logStep(giveawayId, "fetch_quotes", quoters.size)
            mergePool(candidates, quoters, hostXId, hasExistingPool = conditions.reply || conditions.retweet)
        }

        // 4. Follower check (Pro+ only)
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

        // 5. Follow accounts check (Pro+ only) — inverted lookup per candidate
        if (conditions.followAccounts.isNotEmpty() && tierConfig.followAccountsCheck) {
            val accountIds = conditions.followAccounts.mapNotNull { dataSource.resolveHandle(it) }
            if (accountIds.isNotEmpty()) {
                val toRemove = mutableSetOf<String>()
                for ((userId, _) in candidates) {
                    val following = dataSource.fetchFollowing(userId)
                    if (!following.containsAll(accountIds)) toRemove.add(userId)
                }
                candidates.keys.removeAll(toRemove)
                auditLog?.logStep(
                    giveawayId, "follow_accounts_filter",
                    candidates.size,
                    "accounts=${conditions.followAccounts}, resolved=${accountIds.size}"
                )
            }
        }

        // 6. Required hashtag filter (all tiers)
        if (conditions.requiredHashtag != null) {
            val tagRegex = Regex("""#${Regex.escape(conditions.requiredHashtag!!)}""", RegexOption.IGNORE_CASE)
            candidates.entries.removeAll { (_, user) ->
                user.replyText?.let { tagRegex.containsMatchIn(it) } != true
            }
            auditLog?.logStep(giveawayId, "hashtag_filter", candidates.size, "required=#${conditions.requiredHashtag}")
        }

        // 7. Min tags filter (all tiers)
        if (conditions.minTags > 0) {
            val excluded = excludeHandles
            candidates.entries.removeAll { (_, user) ->
                val text = user.replyText ?: return@removeAll true
                val tagCount = MENTION_REGEX.findAll(text)
                    .map { it.groupValues[1].lowercase() }
                    .filter { it !in excluded }
                    .distinct()
                    .count()
                tagCount < conditions.minTags
            }
            auditLog?.logStep(giveawayId, "min_tags_filter", candidates.size, "minTags=${conditions.minTags}")
        }

        // 8. Fraud filter (Business only)
        if (tierConfig.fraudFilter) {
            val minAge = conditions.minAccountAgeDays.takeIf { it > 0 } ?: tierConfig.minAccountAgeDays
            val minFol = conditions.minFollowers.takeIf { it > 0 } ?: tierConfig.minFollowers
            if (minAge > 0 || minFol > 0) {
                val cutoff = Clock.System.now().minus(minAge.days)
                candidates.entries.removeAll { (_, user) ->
                    val tooYoung = minAge > 0 && user.createdAt != null &&
                        Instant.parse(user.createdAt!!) > cutoff
                    val tooFewFollowers = minFol > 0 &&
                        (user.publicMetrics?.followersCount ?: 0) < minFol
                    tooYoung || tooFewFollowers
                }
                auditLog?.logStep(
                    giveawayId, "fraud_filter", candidates.size,
                    "minAge=${minAge}d, minFollowers=$minFol"
                )
            }
        }

        // 9. Cap at tier maxEntries
        val pool = if (candidates.size > maxEntries) {
            candidates.values.take(maxEntries)
        } else {
            candidates.values.toList()
        }

        auditLog?.logStep(giveawayId, "pool_final", pool.size)
        return PoolResult(users = pool, followHostPartial = followHostPartial)
    }

    /** Intersect with existing pool, or seed pool if empty. */
    private fun mergePool(
        candidates: MutableMap<String, XUser>,
        users: List<XUser>,
        hostXId: String,
        hasExistingPool: Boolean,
    ) {
        if (hasExistingPool) {
            candidates.keys.retainAll(users.map { it.id }.toSet())
        } else {
            for (u in users) {
                if (u.id != hostXId) candidates[u.id] = u
            }
        }
    }
}
