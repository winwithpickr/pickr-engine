package dev.pickrtweet.core

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
}
