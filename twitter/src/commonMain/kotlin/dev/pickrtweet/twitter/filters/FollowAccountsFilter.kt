package dev.pickrtweet.twitter.filters

import dev.pickrtweet.core.pipeline.PipelineContext
import dev.pickrtweet.core.pipeline.PoolFilter
import dev.pickrtweet.twitter.XDataSource
import dev.pickrtweet.twitter.models.XUser

class FollowAccountsFilter(
    private val dataSource: XDataSource,
    private val accounts: List<String>,
) : PoolFilter<XUser> {
    override val name = "follow_accounts_filter"

    override suspend fun apply(candidates: MutableMap<String, XUser>, context: PipelineContext) {
        val accountIds = accounts.mapNotNull { dataSource.resolveHandle(it) }
        if (accountIds.isEmpty()) return

        val toRemove = mutableSetOf<String>()
        for ((userId, _) in candidates) {
            val following = dataSource.fetchFollowing(userId)
            if (!following.containsAll(accountIds)) toRemove.add(userId)
        }
        candidates.keys.removeAll(toRemove)
    }
}
