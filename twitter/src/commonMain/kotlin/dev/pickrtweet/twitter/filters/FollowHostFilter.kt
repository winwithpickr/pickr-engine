package dev.pickrtweet.twitter.filters

import dev.pickrtweet.core.pipeline.PipelineContext
import dev.pickrtweet.core.pipeline.PoolFilter
import dev.pickrtweet.twitter.XDataSource
import dev.pickrtweet.twitter.models.XUser

class FollowHostFilter(private val dataSource: XDataSource) : PoolFilter<XUser> {
    override val name = "follower_filter"

    /** Set after apply() — true if the follower set was rate-limited. */
    var isPartial: Boolean = false
        private set

    override suspend fun apply(candidates: MutableMap<String, XUser>, context: PipelineContext) {
        val (followerSet, partial) = dataSource.buildFollowerSet(context.hostId)
        isPartial = partial
        candidates.keys.retainAll(followerSet)
    }
}
