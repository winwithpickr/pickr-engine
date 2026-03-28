package dev.pickrtweet.twitter.sources

import dev.pickrtweet.core.pipeline.PipelineContext
import dev.pickrtweet.core.pipeline.PoolSource
import dev.pickrtweet.twitter.XDataSource
import dev.pickrtweet.twitter.models.EntryConditions
import dev.pickrtweet.twitter.models.XUser

class RetweetSource(
    private val dataSource: XDataSource,
    private val conditions: EntryConditions,
) : PoolSource<XUser> {
    override val name = "retweets"
    override suspend fun fetch(context: PipelineContext): List<XUser> =
        dataSource.fetchRetweeters(context.targetId, context.maxEntries)
    override fun intersects(context: PipelineContext) = conditions.reply
}
