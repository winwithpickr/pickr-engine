package com.winwithpickr.core

import kotlin.test.*

class VerifyExportTest {

    @Test
    fun verifyPick_computes_winners_and_hash() {
        val seed = "a".repeat(64)
        val poolIds = arrayOf("u1", "u2", "u3", "u4", "u5")

        val result = verifyPick(seed, poolIds, winnerCount = 1)
        assertEquals(1, result.winners.size)
        assertEquals(64, result.poolHash.length)
        assertTrue(result.poolHash.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun verifyPick_single_entry() {
        val seed = "b".repeat(64)
        val poolIds = arrayOf("only_user")

        val result = verifyPick(seed, poolIds, winnerCount = 1)
        assertEquals("only_user", result.winners[0])
    }

    @Test
    fun verifyPick_deterministic() {
        val seed = "a".repeat(64)
        val poolIds = arrayOf("u1", "u2", "u3", "u4", "u5")

        val r1 = verifyPick(seed, poolIds, winnerCount = 2)
        val r2 = verifyPick(seed, poolIds, winnerCount = 2)
        assertContentEquals(r1.winners, r2.winners)
        assertEquals(r1.poolHash, r2.poolHash)
    }
}
