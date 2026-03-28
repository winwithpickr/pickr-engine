package dev.pickrtweet.twitter.models

import dev.pickrtweet.core.models.Candidate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XUser(
    override val id: String,
    @SerialName("username") override val displayName: String,
    @SerialName("public_metrics")    val publicMetrics: PublicMetrics? = null,
    @SerialName("created_at")        val createdAt: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    val description: String? = null,
    @kotlinx.serialization.Transient val replyText: String? = null,
) : Candidate {
    /** Alias for backward compatibility with existing code expecting .username */
    val username: String get() = displayName
}

@Serializable
data class PublicMetrics(
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("tweet_count")     val tweetCount: Int = 0,
)
