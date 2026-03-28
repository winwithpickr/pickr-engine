@file:OptIn(ExperimentalJsExport::class)

import com.winwithpickr.twitter.CommandParser

// ── Verify (delegating to engine) ───────────────────────────────────────────

@JsExport
data class VerifyResult(
    val winners: Array<String>,
    val poolHash: String,
)

@JsExport
fun verifyPick(seed: String, poolIds: Array<String>, winnerCount: Int): VerifyResult {
    val result = com.winwithpickr.core.verifyPick(seed, poolIds, winnerCount)
    return VerifyResult(winners = result.winners, poolHash = result.poolHash)
}

// ── Parser ──────────────────────────────────────────────────────────────────

@JsExport
data class ParseResult(
    val valid: Boolean,
    val winners: Int = 1,
    val mode: String = "IMMEDIATE",
    val reply: Boolean = true,
    val retweet: Boolean = false,
    val like: Boolean = false,
    val quoteTweet: Boolean = false,
    val followHost: Boolean = false,
    val followAccounts: Array<String> = emptyArray(),
    val scheduledDelay: String? = null,
    val minAccountAgeDays: Int = 0,
    val minFollowers: Int = 0,
    val requiredHashtag: String? = null,
    val minTags: Int = 0,
)

@JsExport
fun parseCommand(text: String, botHandle: String = "winwithpickr"): ParseResult {
    val cmd = CommandParser.parse(text, botHandle)
        ?: return ParseResult(valid = false)

    val delay = cmd.scheduledDelayMs?.let { ms ->
        if (ms < 86_400_000) "${ms / 3_600_000}h" else "${ms / 86_400_000}d"
    }

    return ParseResult(
        valid = true,
        winners = cmd.winners,
        mode = cmd.triggerMode.name,
        reply = cmd.conditions.reply,
        retweet = cmd.conditions.retweet,
        like = cmd.conditions.like,
        quoteTweet = cmd.conditions.quoteTweet,
        followHost = cmd.conditions.followHost,
        followAccounts = cmd.conditions.followAccounts.toTypedArray(),
        scheduledDelay = delay,
        minAccountAgeDays = cmd.conditions.minAccountAgeDays,
        minFollowers = cmd.conditions.minFollowers,
        requiredHashtag = cmd.conditions.requiredHashtag,
        minTags = cmd.conditions.minTags,
    )
}
