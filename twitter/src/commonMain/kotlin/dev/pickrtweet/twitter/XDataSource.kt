package dev.pickrtweet.twitter

import dev.pickrtweet.twitter.models.XUser

/**
 * Platform-specific data source for Twitter/X API operations.
 * Implemented by XClient on the server side.
 */
interface XDataSource {
    suspend fun fetchReplies(tweetId: String, maxResults: Int): List<XUser>
    suspend fun fetchRetweeters(tweetId: String, maxResults: Int): List<XUser>
    suspend fun fetchQuoteTweets(tweetId: String, maxResults: Int): List<XUser>
    suspend fun buildFollowerSet(hostId: String): Pair<Set<String>, Boolean>
    suspend fun resolveHandle(handle: String): String?
    suspend fun fetchFollowing(userId: String): Set<String>
}
