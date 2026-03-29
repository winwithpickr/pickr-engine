package com.winwithpickr.core

/**
 * Deterministic shuffle and pool hashing for verifiable giveaway picks.
 *
 * The shuffle uses the same LCG algorithm as java.util.Random to maintain
 * compatibility with results already stored in the database. The pool hash
 * uses SHA-256 via a pure Kotlin implementation for cross-platform support.
 *
 * `generateSeed()` is JVM-only (requires SecureRandom) — see SeedGenerator.
 */
object SeededRandom {

    /**
     * Fisher-Yates shuffle using java.util.Random-compatible LCG.
     *
     * Parses the first 8 bytes of the hex seed as a big-endian Long,
     * seeds a java.util.Random-compatible LCG, and shuffles in-place.
     */
    fun <T> shuffle(items: List<T>, seed: String): List<T> {
        val seedLong = hexToSeedLong(seed)
        val rng = JavaCompatRandom(seedLong)
        val result = items.toMutableList()
        for (i in result.indices.reversed()) {
            val j = (rng.nextLong().and(0x7FFFFFFF) % (i + 1)).toInt()
            val tmp = result[i]; result[i] = result[j]; result[j] = tmp
        }
        return result
    }

    /**
     * SHA-256 hash of sorted, comma-joined user IDs. Order-independent.
     */
    fun poolHash(userIds: List<String>): String {
        val sorted = userIds.sorted().joinToString(",")
        return Sha256.digest(sorted.encodeToByteArray())
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun hexToSeedLong(hex: String): Long {
        var value = 0L
        for (i in 0 until 16 step 2) {
            value = (value shl 8) or hex.substring(i, i + 2).toInt(16).toLong()
        }
        return value
    }
}

/**
 * Pure Kotlin reimplementation of java.util.Random's LCG.
 *
 * Constructor: state = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1)
 * next(bits): state = (state * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1); return (state >>> (48 - bits)).toInt()
 * nextLong(): (next(32).toLong() << 32) + next(32).toLong()
 */
internal class JavaCompatRandom(seed: Long) {
    private val mask = (1L shl 48) - 1
    private val multiplier = 0x5DEECE66DL
    private val increment = 0xBL
    private var state = (seed xor multiplier) and mask

    fun next(bits: Int): Int {
        state = (state * multiplier + increment) and mask
        return (state ushr (48 - bits)).toInt()
    }

    fun nextLong(): Long =
        (next(32).toLong() shl 32) + next(32).toLong()
}
