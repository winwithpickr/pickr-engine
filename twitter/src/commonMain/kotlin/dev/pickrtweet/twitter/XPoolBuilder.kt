package dev.pickrtweet.twitter

import dev.pickrtweet.core.models.TierConfig
import dev.pickrtweet.core.pipeline.*
import dev.pickrtweet.twitter.filters.*
import dev.pickrtweet.twitter.models.EntryConditions
import dev.pickrtweet.twitter.models.XUser
import dev.pickrtweet.twitter.sources.*

/**
 * Twitter-specific feature flags used in [TierConfig.features].
 */
object XFeatures {
    const val FOLLOWER_CHECK = "followerCheck"
    const val FOLLOW_ACCOUNTS_CHECK = "followAccountsCheck"
    const val FRAUD_FILTER = "fraudFilter"
    const val SCHEDULED_PICKS = "scheduledPicks"
}

/**
 * Assembles a [PoolPipeline] from Twitter-specific entry conditions
 * and tier configuration. This is the bridge between the generic
 * pipeline engine and Twitter's domain model.
 */
class XPoolBuilder(private val dataSource: XDataSource) {

    fun buildPipeline(
        conditions: EntryConditions,
        tierConfig: TierConfig,
        excludeHandles: Set<String> = emptySet(),
        /** Default fraud filter thresholds from tier config. */
        defaultMinAgeDays: Int = 0,
        defaultMinFollowers: Int = 0,
    ): Pair<PoolPipeline<XUser>, FollowHostFilter?> {
        val sources = mutableListOf<PoolSource<XUser>>()
        val filters = mutableListOf<PoolFilter<XUser>>()

        // Sources — order matters for intersection logic
        if (conditions.reply) {
            sources.add(ReplySource(dataSource))
        }
        if (conditions.retweet) {
            sources.add(RetweetSource(dataSource, conditions))
        }
        if (conditions.quoteTweet) {
            sources.add(QuoteTweetSource(dataSource, conditions))
        }

        // Filters — applied in order after pool is assembled
        var followHostFilter: FollowHostFilter? = null
        if (conditions.followHost && tierConfig.hasFeature(XFeatures.FOLLOWER_CHECK)) {
            followHostFilter = FollowHostFilter(dataSource)
            filters.add(followHostFilter)
        }

        if (conditions.followAccounts.isNotEmpty() && tierConfig.hasFeature(XFeatures.FOLLOW_ACCOUNTS_CHECK)) {
            filters.add(FollowAccountsFilter(dataSource, conditions.followAccounts))
        }

        if (conditions.requiredHashtag != null) {
            filters.add(HashtagFilter(conditions.requiredHashtag!!))
        }

        if (conditions.minTags > 0) {
            filters.add(MinTagsFilter(conditions.minTags, excludeHandles))
        }

        if (tierConfig.hasFeature(XFeatures.FRAUD_FILTER)) {
            val minAge = conditions.minAccountAgeDays.takeIf { it > 0 } ?: defaultMinAgeDays
            val minFol = conditions.minFollowers.takeIf { it > 0 } ?: defaultMinFollowers
            filters.add(FraudFilter(minAge, minFol))
        }

        return Pair(PoolPipeline(sources, filters), followHostFilter)
    }
}
