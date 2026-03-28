package dev.pickrtweet.twitter.filters

import dev.pickrtweet.core.pipeline.PipelineContext
import dev.pickrtweet.core.pipeline.PoolFilter
import dev.pickrtweet.twitter.models.XUser

class MinTagsFilter(
    private val minTags: Int,
    private val excludeHandles: Set<String>,
) : PoolFilter<XUser> {
    override val name = "min_tags_filter"

    companion object {
        private val MENTION_REGEX = Regex("""@(\w+)""")
    }

    override suspend fun apply(candidates: MutableMap<String, XUser>, context: PipelineContext) {
        candidates.entries.removeAll { (_, user) ->
            val text = user.replyText ?: return@removeAll true
            val tagCount = MENTION_REGEX.findAll(text)
                .map { it.groupValues[1].lowercase() }
                .filter { it !in excludeHandles }
                .distinct()
                .count()
            tagCount < minTags
        }
    }
}
