package dev.pickrtweet.twitter.sources

import dev.pickrtweet.core.pipeline.PipelineContext
import dev.pickrtweet.core.pipeline.PoolSource
import dev.pickrtweet.twitter.XDataSource
import dev.pickrtweet.twitter.models.XUser

class ReplySource(private val dataSource: XDataSource) : PoolSource<XUser> {
    override val name = "replies"
    override suspend fun fetch(context: PipelineContext): List<XUser> =
        dataSource.fetchReplies(context.targetId, context.maxEntries)
    override fun intersects(context: PipelineContext) = false // replies seed the pool
}
