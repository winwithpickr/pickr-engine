package dev.pickrtweet.twitter.filters

import dev.pickrtweet.core.pipeline.PipelineContext
import dev.pickrtweet.core.pipeline.PoolFilter
import dev.pickrtweet.twitter.models.XUser

class HashtagFilter(private val hashtag: String) : PoolFilter<XUser> {
    override val name = "hashtag_filter"

    private val tagRegex = Regex("""#${Regex.escape(hashtag)}""", RegexOption.IGNORE_CASE)

    override suspend fun apply(candidates: MutableMap<String, XUser>, context: PipelineContext) {
        candidates.entries.removeAll { (_, user) ->
            user.replyText?.let { tagRegex.containsMatchIn(it) } != true
        }
    }
}
