package com.winwithpickr.twitter.filters

import com.winwithpickr.core.pipeline.PipelineContext
import com.winwithpickr.core.pipeline.PoolFilter
import com.winwithpickr.twitter.models.XUser

class QuoteTextFilter(private val requiredText: String) : PoolFilter<XUser> {
    override val name = "quote_text_filter"

    private val textRegex = Regex(Regex.escape(requiredText), RegexOption.IGNORE_CASE)

    override suspend fun apply(candidates: MutableMap<String, XUser>, context: PipelineContext) {
        candidates.entries.removeAll { (_, user) ->
            user.replyText?.let { textRegex.containsMatchIn(it) } != true
        }
    }
}
