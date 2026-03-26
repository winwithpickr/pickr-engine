package dev.pickrtweet.core

import kotlin.test.*

class SeededRandomTest {

    @Test
    fun `same seed and list always produces same winner order`() {
        val items = listOf("alice", "bob", "carol", "dave", "eve")
        val seed = "a".repeat(64)
        val result1 = SeededRandom.shuffle(items, seed)
        val result2 = SeededRandom.shuffle(items, seed)
        assertEquals(result1, result2)
    }

    @Test
    fun `different seeds produce different orders`() {
        val items = listOf("alice", "bob", "carol", "dave", "eve")
        val seed1 = "a".repeat(64)
        val seed2 = "b".repeat(64)
        val result1 = SeededRandom.shuffle(items, seed1)
        val result2 = SeededRandom.shuffle(items, seed2)
        assertNotEquals(result1, result2)
    }

    @Test
    fun `pool hash is deterministic`() {
        val ids = listOf("u3", "u1", "u2")
        val hash1 = SeededRandom.poolHash(ids)
        val hash2 = SeededRandom.poolHash(ids)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `pool hash is order-independent`() {
        val hash1 = SeededRandom.poolHash(listOf("u1", "u2", "u3"))
        val hash2 = SeededRandom.poolHash(listOf("u3", "u1", "u2"))
        assertEquals(hash1, hash2, "Pool hash should sort inputs so order doesn't matter")
    }

    @Test
    fun `pool hash is SHA-256 hex string`() {
        val hash = SeededRandom.poolHash(listOf("u1"))
        assertEquals(64, hash.length, "SHA-256 hex is 64 chars")
        assertTrue(hash.matches(Regex("[0-9a-f]+")), "Must be lowercase hex")
    }

    @Test
    fun `seed is 64 hex characters`() {
        val seed = SeededRandom.generateSeed()
        assertEquals(64, seed.length)
        assertTrue(seed.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun `shuffle handles empty list`() {
        val result = SeededRandom.shuffle(emptyList<String>(), "a".repeat(64))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `shuffle handles single-item list`() {
        val result = SeededRandom.shuffle(listOf("only"), "a".repeat(64))
        assertEquals(listOf("only"), result)
    }

    @Test
    fun `shuffle preserves all elements`() {
        val items = listOf("a", "b", "c", "d", "e")
        val seed = SeededRandom.generateSeed()
        val result = SeededRandom.shuffle(items, seed)
        assertEquals(items.sorted(), result.sorted())
    }
}
