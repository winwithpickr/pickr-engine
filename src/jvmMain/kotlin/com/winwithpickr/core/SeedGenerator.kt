package com.winwithpickr.core

import java.security.SecureRandom

/**
 * JVM-only seed generation using SecureRandom.
 * Produces a 64-char hex string (32 random bytes).
 */
object SeedGenerator {

    fun generateSeed(): String =
        ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
}
