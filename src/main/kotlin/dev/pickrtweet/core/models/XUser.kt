package dev.pickrtweet.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XUser(
    val id: String,
    val username: String,
    @SerialName("public_metrics")    val publicMetrics: PublicMetrics? = null,
    @SerialName("created_at")        val createdAt: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    val description: String? = null,
)

@Serializable
data class PublicMetrics(
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("tweet_count")     val tweetCount: Int = 0,
)
