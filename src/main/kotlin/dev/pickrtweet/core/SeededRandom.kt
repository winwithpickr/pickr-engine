package dev.pickrtweet.core

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

object SeededRandom {

    fun generateSeed(): String =
        ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }

    fun <T> shuffle(items: List<T>, seed: String): List<T> {
        val seedBytes = seed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val rng = java.util.Random(ByteBuffer.wrap(seedBytes.take(8).toByteArray()).long)
        val result = items.toMutableList()
        for (i in result.indices.reversed()) {
            val j = (rng.nextLong().and(0x7FFFFFFF) % (i + 1)).toInt()
            val tmp = result[i]; result[i] = result[j]; result[j] = tmp
        }
        return result
    }

    fun poolHash(userIds: List<String>): String {
        val sorted = userIds.sorted().joinToString(",")
        return MessageDigest.getInstance("SHA-256")
            .digest(sorted.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
