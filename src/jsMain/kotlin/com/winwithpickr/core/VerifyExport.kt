@file:OptIn(ExperimentalJsExport::class)

package com.winwithpickr.core

@JsExport
data class VerifyResult(
    val winners: Array<String>,
    val poolHash: String,
)

/**
 * Verifies a giveaway pick client-side.
 *
 * Uses the same SeededRandom.shuffle and SeededRandom.poolHash as the server —
 * single source of truth in commonMain.
 *
 * @param seed 64-char hex seed string
 * @param poolIds sorted array of X user ID strings
 * @param winnerCount number of winners to select
 * @return VerifyResult with computed winners and pool hash
 */
@JsExport
fun verifyPick(seed: String, poolIds: Array<String>, winnerCount: Int): VerifyResult {
    val pool = poolIds.toList()
    val shuffled = SeededRandom.shuffle(pool, seed)
    val winners = shuffled.take(winnerCount).toTypedArray()
    val poolHash = SeededRandom.poolHash(pool)
    return VerifyResult(winners = winners, poolHash = poolHash)
}
