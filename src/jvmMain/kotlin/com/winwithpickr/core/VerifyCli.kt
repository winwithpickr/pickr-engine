package com.winwithpickr.core

/**
 * CLI tool to verify a giveaway pick result.
 *
 * Usage:
 *   ./gradlew :pickr-core:verifyCli --args="--seed <hex> --pool <id,id,...> --winners <n>"
 *
 * Example:
 *   ./gradlew :pickr-core:verifyCli --args="--seed abc123...def --pool 111,222,333,444,555 --winners 2"
 */
fun main(args: Array<String>) {
    val parsed = parseArgs(args)

    val seed = parsed["seed"] ?: error("Missing --seed <64-char hex>")
    val poolCsv = parsed["pool"] ?: error("Missing --pool <id,id,...>")
    val winnerCount = parsed["winners"]?.toIntOrNull() ?: 1

    require(seed.length == 64 && seed.matches(Regex("[0-9a-f]+"))) {
        "Seed must be a 64-character hex string"
    }

    val poolIds = poolCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    require(poolIds.isNotEmpty()) { "Pool must contain at least one ID" }
    require(winnerCount in 1..poolIds.size) {
        "Winner count must be between 1 and pool size (${poolIds.size})"
    }

    val poolHash = SeededRandom.poolHash(poolIds)
    val shuffled = SeededRandom.shuffle(poolIds, seed)
    val winners = shuffled.take(winnerCount)

    println("── Pickr Verification ──")
    println()
    println("Seed:        $seed")
    println("Pool size:   ${poolIds.size}")
    println("Pool hash:   $poolHash")
    println("Winners:     ${winners.joinToString(", ")}")

    // If expected hash provided, compare
    parsed["expected-hash"]?.let { expected ->
        val match = expected == poolHash
        println()
        println("Expected:    $expected")
        println("Match:       ${if (match) "YES" else "NO — MISMATCH"}")
    }
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        if (args[i].startsWith("--") && i + 1 < args.size) {
            result[args[i].removePrefix("--")] = args[i + 1]
            i += 2
        } else {
            i++
        }
    }
    return result
}
