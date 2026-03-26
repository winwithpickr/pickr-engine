package dev.pickrtweet.core

import dev.pickrtweet.core.models.TriggerMode
import kotlin.test.*

class CommandParserTest {

    @Test
    fun `parse basic pick command`() {
        val cmd = CommandParser.parse("@pickrbot pick", "pickrbot")
        assertNotNull(cmd)
        assertEquals(1, cmd.winners)
        assertTrue(cmd.conditions.reply)
        assertEquals(TriggerMode.IMMEDIATE, cmd.triggerMode)
    }

    @Test
    fun `parse pick with winner count`() {
        val cmd = CommandParser.parse("@pickrbot pick 3", "pickrbot")
        assertNotNull(cmd)
        assertEquals(3, cmd.winners)
    }

    @Test
    fun `parse pick from retweets`() {
        val cmd = CommandParser.parse("@pickrbot pick from retweets", "pickrbot")
        assertNotNull(cmd)
        assertTrue(cmd.conditions.retweet)
        assertFalse(cmd.conditions.reply)
    }

    @Test
    fun `parse watch command`() {
        val cmd = CommandParser.parse("@pickrbot watch", "pickrbot")
        assertNotNull(cmd)
        assertEquals(TriggerMode.WATCH, cmd.triggerMode)
    }

    @Test
    fun `isTriggerText detects trigger phrases`() {
        assertTrue(CommandParser.isTriggerText("Time to pick a winner!"))
        assertTrue(CommandParser.isTriggerText("Giveaway over!"))
        assertFalse(CommandParser.isTriggerText("Thanks everyone!"))
    }

    @Test
    fun `returns null for unrelated text`() {
        assertNull(CommandParser.parse("Hello world", "pickrbot"))
    }
}
