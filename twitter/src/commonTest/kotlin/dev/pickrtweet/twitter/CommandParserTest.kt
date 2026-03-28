package dev.pickrtweet.twitter

import dev.pickrtweet.core.models.TriggerMode
import kotlin.test.*

class CommandParserTest {

    @Test
    fun parseBasicPickCommand() {
        val cmd = CommandParser.parse("@winwithpickr pick", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(1, cmd.winners)
        assertTrue(cmd.conditions.reply)
        assertEquals(TriggerMode.IMMEDIATE, cmd.triggerMode)
    }

    @Test
    fun parsePickWithWinnerCount() {
        val cmd = CommandParser.parse("@winwithpickr pick 3", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(3, cmd.winners)
    }

    @Test
    fun parsePickFromRetweets() {
        val cmd = CommandParser.parse("@winwithpickr pick from retweets", "winwithpickr")
        assertNotNull(cmd)
        assertTrue(cmd.conditions.retweet)
        assertFalse(cmd.conditions.reply)
    }

    @Test
    fun parsePickFromQuotes() {
        val cmd = CommandParser.parse("@winwithpickr pick from quotes", "winwithpickr")
        assertNotNull(cmd)
        assertTrue(cmd.conditions.quoteTweet)
        assertFalse(cmd.conditions.reply)
        assertFalse(cmd.conditions.retweet)
    }

    @Test
    fun parsePickFromRepliesPlusQuotes() {
        val cmd = CommandParser.parse("@winwithpickr pick from replies+quotes", "winwithpickr")
        assertNotNull(cmd)
        assertTrue(cmd.conditions.reply)
        assertTrue(cmd.conditions.quoteTweet)
    }

    @Test
    fun quoteTweetFalseByDefault() {
        val cmd = CommandParser.parse("@winwithpickr pick", "winwithpickr")
        assertNotNull(cmd)
        assertFalse(cmd.conditions.quoteTweet)
    }

    @Test
    fun parseWatchCommand() {
        val cmd = CommandParser.parse("@winwithpickr watch", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(TriggerMode.WATCH, cmd.triggerMode)
    }

    @Test
    fun isTriggerTextDetectsTriggerPhrases() {
        assertTrue(CommandParser.isTriggerText("Time to pick a winner!"))
        assertTrue(CommandParser.isTriggerText("Giveaway over!"))
        assertFalse(CommandParser.isTriggerText("Thanks everyone!"))
    }

    @Test
    fun parseFollowHostFromNaturalPhrases() {
        val phrases = listOf(
            "@winwithpickr pick 7 from replies who follow me",
            "@winwithpickr pick from replies must follow",
            "@winwithpickr pick must be following",
            "@winwithpickr pick followers only",
            "@winwithpickr pick from replies following me",
            "@winwithpickr pick 4 from replies+retweets of followers",
            "@winwithpickr pick from my followers",
            "@winwithpickr pick 1 follower only",
        )
        for (phrase in phrases) {
            val cmd = CommandParser.parse(phrase, "winwithpickr")
            assertNotNull(cmd, "Expected followHost for: $phrase")
            assertTrue(cmd.conditions.followHost, "followHost should be true for: $phrase")
        }
    }

    @Test
    fun followHostIsFalseWhenNotMentioned() {
        val cmd = CommandParser.parse("@winwithpickr pick 3 from replies", "winwithpickr")
        assertNotNull(cmd)
        assertFalse(cmd.conditions.followHost)
    }

    @Test
    fun followAccountsDoesNotTriggerFollowHost() {
        val cmd = CommandParser.parse("@winwithpickr pick from replies follow @sponsor", "winwithpickr")
        assertNotNull(cmd)
        assertFalse(cmd.conditions.followHost)
        assertEquals(listOf("sponsor"), cmd.conditions.followAccounts)
    }

    @Test
    fun followingThirdPartyAccountNaturalLanguage() {
        val phrases = listOf(
            "@winwithpickr pick you must be following @sponsor",
            "@winwithpickr pick following @brand",
            "@winwithpickr pick from replies follow @sponsor @brand",
        )
        for (phrase in phrases) {
            val cmd = CommandParser.parse(phrase, "winwithpickr")
            assertNotNull(cmd, "Expected parse for: $phrase")
            assertFalse(cmd.conditions.followHost, "followHost should be false for: $phrase")
            assertTrue(cmd.conditions.followAccounts.isNotEmpty(), "followAccounts should be set for: $phrase")
        }
    }

    @Test
    fun followingThirdPartyCapturesCorrectHandles() {
        val cmd = CommandParser.parse("@winwithpickr pick must be following @sponsor @brand", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(listOf("sponsor", "brand"), cmd.conditions.followAccounts)
    }

    @Test
    fun returnsNullForUnrelatedText() {
        assertNull(CommandParser.parse("Hello world", "winwithpickr"))
    }

    // ── Fraud filter parsing ─────────────────────────────────────────────────

    @Test
    fun parseMinAccountAge() {
        val cmd = CommandParser.parse("@winwithpickr pick age 30d", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(30, cmd.conditions.minAccountAgeDays)
    }

    @Test
    fun parseMinAgeWithPrefix() {
        val cmd = CommandParser.parse("@winwithpickr pick min age 14d", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(14, cmd.conditions.minAccountAgeDays)
    }

    @Test
    fun parseMinFollowersPostfix() {
        val cmd = CommandParser.parse("@winwithpickr pick min followers 100", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(100, cmd.conditions.minFollowers)
    }

    @Test
    fun parseMinFollowersInfix() {
        val cmd = CommandParser.parse("@winwithpickr pick min 50 followers", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(50, cmd.conditions.minFollowers)
    }

    @Test
    fun parseFraudFilterCombined() {
        val cmd = CommandParser.parse("@winwithpickr pick age 7d min followers 10", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(7, cmd.conditions.minAccountAgeDays)
        assertEquals(10, cmd.conditions.minFollowers)
    }

    @Test
    fun noFraudFilterByDefault() {
        val cmd = CommandParser.parse("@winwithpickr pick", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(0, cmd.conditions.minAccountAgeDays)
        assertEquals(0, cmd.conditions.minFollowers)
    }

    // ── Hashtag parsing ────────────────────────────────────────────────────

    @Test
    fun parseHashtagCondition() {
        val cmd = CommandParser.parse("@winwithpickr pick #giveaway", "winwithpickr")
        assertNotNull(cmd)
        assertEquals("giveaway", cmd.conditions.requiredHashtag)
    }

    @Test
    fun parseHashtagWithExplicitKeyword() {
        val cmd = CommandParser.parse("@winwithpickr pick hashtag #contest", "winwithpickr")
        assertNotNull(cmd)
        assertEquals("contest", cmd.conditions.requiredHashtag)
    }

    @Test
    fun noHashtagByDefault() {
        val cmd = CommandParser.parse("@winwithpickr pick", "winwithpickr")
        assertNotNull(cmd)
        assertNull(cmd.conditions.requiredHashtag)
    }

    // ── Tag friends parsing ──────────────────────────────────────────────────

    @Test
    fun parseMinTags() {
        val cmd = CommandParser.parse("@winwithpickr pick tag 2", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(2, cmd.conditions.minTags)
    }

    @Test
    fun parseMinTagsAlternate() {
        val cmd = CommandParser.parse("@winwithpickr pick min tags 3", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(3, cmd.conditions.minTags)
    }

    @Test
    fun noMinTagsByDefault() {
        val cmd = CommandParser.parse("@winwithpickr pick", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(0, cmd.conditions.minTags)
    }

    @Test
    fun parseCombinedHashtagAndTags() {
        val cmd = CommandParser.parse("@winwithpickr pick #giveaway tag 2", "winwithpickr")
        assertNotNull(cmd)
        assertEquals("giveaway", cmd.conditions.requiredHashtag)
        assertEquals(2, cmd.conditions.minTags)
    }

    // ── Scheduled delay clamping ─────────────────────────────────────────────

    @Test
    fun scheduledDelayClampedAt7Days() {
        val cmd = CommandParser.parse("@winwithpickr pick in 30d", "winwithpickr")
        assertNotNull(cmd)
        assertEquals(TriggerMode.SCHEDULED, cmd.triggerMode)
        assertEquals(7 * 86_400_000L, cmd.scheduledDelayMs)
    }
}
