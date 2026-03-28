package dev.pickrtweet.twitter.sources

import dev.pickrtweet.core.pipeline.PipelineContext
import dev.pickrtweet.core.pipeline.PoolSource
import dev.pickrtweet.twitter.XDataSource
import dev.pickrtweet.twitter.models.EntryConditions
import dev.pickrtweet.twitter.models.XUser

class QuoteTweetSource(
    private val dataSource: XDataSource,
    private val conditions: EntryConditions,
) : PoolSource<XUser> {
    override val name = "quotes"
    override suspend fun fetch(context: PipelineContext): List<XUser> =
        dataSource.fetchQuoteTweets(context.targetId, context.maxEntries)
    override fun intersects(context: PipelineContext) = conditions.reply || conditions.retweet
}
