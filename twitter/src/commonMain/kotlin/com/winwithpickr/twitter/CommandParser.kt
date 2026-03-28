package com.winwithpickr.twitter

import com.winwithpickr.core.models.TriggerMode
import com.winwithpickr.twitter.models.EntryConditions
import com.winwithpickr.twitter.models.ParsedCommand

object CommandParser {

    private val winnersRegex        = Regex("""(?:pick|watch)\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val fromRegex           = Regex("""from\s+([\w+]+)""", RegexOption.IGNORE_CASE)
    private val followAccountsRegex = Regex("""follow(?:ing|er|ers)?\s+((?:@\w+\s*,?\s*)+)""", RegexOption.IGNORE_CASE)
    private val scheduledRegex      = Regex("""in\s+(\d+)(h|d)""", RegexOption.IGNORE_CASE)
    private val minAgeRegex         = Regex("""(?:min\s+)?age\s+(\d+)d""", RegexOption.IGNORE_CASE)
    private val minFollowersRegex   = Regex("""min\s+(?:(\d+)\s+)?followers?\s*(\d+)?""", RegexOption.IGNORE_CASE)
    private val hashtagRegex        = Regex("""(?:hashtag\s+)?#(\w+)""", RegexOption.IGNORE_CASE)
    private val minTagsRegex        = Regex("""(?:tag|min\s+tags?)\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val quoteTextRegex      = Regex("quote\\s+[\"\\u201c\\u201d](.+?)[\"\\u201c\\u201d]", RegexOption.IGNORE_CASE)

    val TRIGGER_PHRASES = listOf(
        "pick a winner", "pick winner", "picking a winner", "picking winner",
        "draw a winner", "draw winner", "drawing a winner",
        "end giveaway", "giveaway over", "giveaway ended", "giveaway closed",
        "closing giveaway", "winner time", "time to pick",
    )

    fun parse(text: String, botHandle: String): ParsedCommand? {
        val lower = text.lowercase().replace("@${botHandle.lowercase()}", "")
        val hasPick  = lower.contains("pick")
        val hasWatch = lower.contains("watch")
        if (!hasPick && !hasWatch) return null

        val triggerMode = if (hasWatch) TriggerMode.WATCH else TriggerMode.IMMEDIATE
        val winners = winnersRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val fromClause = fromRegex.find(lower)?.groupValues?.get(1) ?: "replies"
        val sources = fromClause.split("+").map { it.trim() }
        val reply   = "replies"  in sources || ("retweets" !in sources && "likes" !in sources && "quotes" !in sources)
        val retweet = "retweets" in sources
        val like    = "likes"    in sources
        val quoteTweet = "quotes" in sources

        val followAccounts = followAccountsRegex.find(lower)
            ?.groupValues?.get(1)?.split(Regex("\\s+"))
            ?.filter { it.startsWith("@") && it.drop(1).lowercase() != botHandle.lowercase() }
            ?.map { it.drop(1) } ?: emptyList()

        val hasFollowLanguage = Regex("""follow(?:er|ers|ing)?""").containsMatchIn(lower)
        val followHost = hasFollowLanguage && followAccounts.isEmpty()

        val maxDelayMs = 7 * 86_400_000L
        val scheduledDelayMs = if (triggerMode == TriggerMode.IMMEDIATE) {
            scheduledRegex.find(lower)?.let { m ->
                val n = m.groupValues[1].toLong()
                val raw = if (m.groupValues[2] == "h") n * 3_600_000L else n * 86_400_000L
                raw.coerceAtMost(maxDelayMs)
            }
        } else null

        val minAccountAgeDays = minAgeRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minFollowers = minFollowersRegex.find(lower)?.let { m ->
            m.groupValues[1].toIntOrNull() ?: m.groupValues[2].toIntOrNull() ?: 0
        } ?: 0

        val requiredHashtag = hashtagRegex.find(lower)?.groupValues?.get(1)
        val minTags = minTagsRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val requiredQuoteText = quoteTextRegex.find(text)?.groupValues?.get(1)

        return ParsedCommand(
            winners = winners.coerceAtLeast(1),
            conditions = EntryConditions(
                reply = reply,
                retweet = retweet,
                like = like,
                quoteTweet = quoteTweet,
                followHost = followHost,
                followAccounts = followAccounts,
                minAccountAgeDays = minAccountAgeDays,
                minFollowers = minFollowers,
                requiredHashtag = requiredHashtag,
                requiredQuoteText = requiredQuoteText,
                minTags = minTags,
            ),
            triggerMode = if (scheduledDelayMs != null) TriggerMode.SCHEDULED else triggerMode,
            scheduledDelayMs = scheduledDelayMs,
        )
    }

    fun isTriggerText(text: String): Boolean =
        TRIGGER_PHRASES.any { text.lowercase().contains(it) }
}
