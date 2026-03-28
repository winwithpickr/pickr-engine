package dev.pickrtweet.twitter

import dev.pickrtweet.core.models.Tier
import dev.pickrtweet.core.models.TierConfig
import dev.pickrtweet.twitter.models.XUser
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ReplyFormatter {

    fun format(
        winners: List<XUser>,
        poolSize: Int,
        seed: String,
        tierConfig: TierConfig,
        giveawayId: String,
        followHostPartial: Boolean,
        appBaseUrl: String,
    ): String = buildString {
        if (winners.size == 1) {
            appendLine("\uD83C\uDF89 Winner: @${winners[0].username}")
        } else {
            appendLine("\uD83C\uDF89 Winners (${winners.size}):")
            winners.forEachIndexed { i, w -> appendLine("${i + 1}. @${w.username}") }
        }
        appendLine()
        val partialNote = if (followHostPartial) " \u00B7 \u26A0\uFE0F follower check partial" else ""
        appendLine("Pool: ${poolSize.fmt()} \u00B7 Seed: $seed$partialNote")
        append("\uD83D\uDD17 $appBaseUrl/r/$giveawayId")
        if (tierConfig.watermark) {
            appendLine()
            append("Powered by @winwithpickr")
        }
    }

    fun watchConfirmReply(handle: String) =
        "Got it @$handle! Watching this giveaway \uD83D\uDC40\n\n" +
        "Reply to your tweet (or quote-RT) with \"picking a winner\" " +
        "when ready \u2014 I'll draw automatically."

    fun alreadyWatchingReply(handle: String) =
        "Hey @$handle \u2014 already watching this giveaway! " +
        "Reply with \"picking a winner\" when you're ready."

    fun watchExpiredReply(handle: String) =
        "Hey @$handle \u2014 this giveaway's 7-day window closed (X API limit). " +
        "I can no longer fetch replies.\n\nFor future giveaways tag me within 7 days."

    fun scheduledConfirmReply(handle: String, delayMs: Long?): String {
        val dur = delayMs?.let { if (it < 86_400_000) "${it / 3_600_000}h" else "${it / 86_400_000}d" } ?: "the scheduled time"
        return "Got it @$handle! I'll pick a winner in $dur \u23F1"
    }

    fun noEntriesReply(handle: String) =
        "Hey @$handle \u2014 no eligible entries found. " +
        "Check that the conditions match how participants entered."

    fun monthlyLimitReply(handle: String, limit: Int, tier: Tier, overageRate: Int?, xId: String, tweetId: String, secret: String, baseUrl: String): String {
        if (tier == Tier.FREE) {
            return "Hey @$handle \u2014 you've used all $limit free picks this month.\n\n" +
                "Upgrade to Pro ($15/mo, 5 picks included):\n${upgradeUrl(xId, tweetId, "pro", secret, baseUrl)}"
        }
        val rateFmt = overageRate?.let { "$${it / 100}.${"%02d".format(it % 100)}" } ?: "$0.50"
        return "Hey @$handle \u2014 you've used your $limit included picks this month.\n\n" +
            "Extra picks are $rateFmt each. Pick will proceed and the overage " +
            "will appear on your next invoice."
    }

    fun followerGateReply(handle: String, tweetId: String, xId: String, secret: String, baseUrl: String) =
        "Hey @$handle \u2014 follower verification requires Pro.\n\n" +
        "Upgrade: ${upgradeUrl(xId, tweetId, "pro", secret, baseUrl)}\n\n" +
        "To pick without it: \"@winwithpickr pick from replies\""

    fun upgradeUrl(xId: String, tweetId: String, plan: String, secret: String, baseUrl: String): String {
        val payload = "$xId:$tweetId:$plan"
        val sig = hmacSha256(payload, secret)
        return "$baseUrl/upgrade?xid=$xId&ref=$tweetId&plan=$plan&sig=$sig"
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    fun followAccountsGateReply(handle: String, tweetId: String, xId: String, secret: String, baseUrl: String) =
        "Hey @$handle \u2014 \"must follow\" account checks require Pro.\n\n" +
        "Upgrade: ${upgradeUrl(xId, tweetId, "pro", secret, baseUrl)}\n\n" +
        "To pick without it: \"@winwithpickr pick from replies\""

    fun fraudFilterGateReply(handle: String, tweetId: String, xId: String, secret: String, baseUrl: String) =
        "Hey @$handle \u2014 fraud filtering (account age / follower minimum) requires Business.\n\n" +
        "Upgrade: ${upgradeUrl(xId, tweetId, "business", secret, baseUrl)}\n\n" +
        "To pick without it: \"@winwithpickr pick from replies\""

    fun scheduledPicksGateReply(handle: String, tweetId: String, xId: String, secret: String, baseUrl: String) =
        "Hey @$handle \u2014 scheduled picks require Business.\n\n" +
        "Upgrade: ${upgradeUrl(xId, tweetId, "business", secret, baseUrl)}\n\n" +
        "Tip: use \"@winwithpickr watch\" to pick when you're ready instead."

    fun tweetTooOldReply(handle: String, ageDays: Int) =
        "Hey @$handle \u2014 replies only available within 7 days of the original tweet " +
        "(X API limit). This tweet is $ageDays days old."

    fun partialFollowerNote(appBaseUrl: String, giveawayId: String) =
        "Note: follower check covered first 15,000 followers due to API limits. " +
        "Details: $appBaseUrl/r/$giveawayId"

    private fun Int.fmt() = "%,d".format(this)
}
