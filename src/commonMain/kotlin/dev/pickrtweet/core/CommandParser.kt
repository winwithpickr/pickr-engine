package dev.pickrtweet.core

import dev.pickrtweet.core.models.EntryConditions
import dev.pickrtweet.core.models.ParsedCommand
import dev.pickrtweet.core.models.TriggerMode

object CommandParser {

    private val winnersRegex        = Regex("""(?:pick|watch)\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val fromRegex           = Regex("""from\s+([\w+]+)""", RegexOption.IGNORE_CASE)
    private val followAccountsRegex = Regex("""follow(?:ing|er|ers)?\s+((?:@\w+\s*,?\s*)+)""", RegexOption.IGNORE_CASE)
    private val scheduledRegex      = Regex("""in\s+(\d+)(h|d)""", RegexOption.IGNORE_CASE)

    val TRIGGER_PHRASES = listOf(
        "pick a winner", "pick winner", "picking a winner", "picking winner",
        "draw a winner", "draw winner", "drawing a winner",
        "end giveaway", "giveaway over", "giveaway ended", "giveaway closed",
        "closing giveaway", "winner time", "time to pick",
    )

    fun parse(text: String, botHandle: String): ParsedCommand? {
        // Strip the bot handle so "pickr" inside "@winwithpickr" doesn't false-positive
        val lower = text.lowercase().replace("@${botHandle.lowercase()}", "")
        val hasPick  = lower.contains("pick")
        val hasWatch = lower.contains("watch")
        if (!hasPick && !hasWatch) return null

        val triggerMode = if (hasWatch) TriggerMode.WATCH else TriggerMode.IMMEDIATE
        val winners = winnersRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val fromClause = fromRegex.find(lower)?.groupValues?.get(1) ?: "replies"
        val sources = fromClause.split("+").map { it.trim() }
        val reply   = "replies"  in sources || ("retweets" !in sources && "likes" !in sources)
        val retweet = "retweets" in sources
        val like    = "likes"    in sources

        val followAccounts = followAccountsRegex.find(lower)
            ?.groupValues?.get(1)?.split(Regex("\\s+"))
            ?.filter { it.startsWith("@") && it.drop(1).lowercase() != botHandle.lowercase() }
            ?.map { it.drop(1) } ?: emptyList()

        // followHost = follow-language exists that ISN'T captured by followAccounts
        val hasFollowLanguage = Regex("""follow(?:er|ers|ing)?""").containsMatchIn(lower)
        val followHost = hasFollowLanguage && followAccounts.isEmpty()

        val scheduledDelayMs = if (triggerMode == TriggerMode.IMMEDIATE) {
            scheduledRegex.find(lower)?.let { m ->
                val n = m.groupValues[1].toLong()
                if (m.groupValues[2] == "h") n * 3_600_000L else n * 86_400_000L
            }
        } else null

        return ParsedCommand(
            winners = winners.coerceAtLeast(1),
            conditions = EntryConditions(reply, retweet, like, followHost, followAccounts),
            triggerMode = if (scheduledDelayMs != null) TriggerMode.SCHEDULED else triggerMode,
            scheduledDelayMs = scheduledDelayMs,
        )
    }

    fun isTriggerText(text: String): Boolean =
        TRIGGER_PHRASES.any { text.lowercase().contains(it) }
}
