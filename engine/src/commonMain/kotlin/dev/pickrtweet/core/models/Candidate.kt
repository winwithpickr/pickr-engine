package dev.pickrtweet.core.models

/**
 * A participant in a giveaway pool. Platform implementations
 * (Twitter, Discord, etc.) provide their own concrete types.
 */
interface Candidate {
    val id: String
    val displayName: String
}
