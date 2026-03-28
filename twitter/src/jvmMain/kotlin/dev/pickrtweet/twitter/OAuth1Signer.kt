package dev.pickrtweet.twitter

import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class OAuth1Signer(
    private val consumerKey: String,
    private val consumerSecret: String,
    private val accessToken: String,
    private val accessTokenSecret: String,
) {
    private val rng = SecureRandom()

    fun buildHeader(method: String, url: String, bodyParams: Map<String, String> = emptyMap()): String {
        val timestamp = System.currentTimeMillis() / 1000L
        val nonce = generateNonce()

        val oauthParams = mapOf(
            "oauth_consumer_key"     to consumerKey,
            "oauth_nonce"            to nonce,
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp"        to timestamp.toString(),
            "oauth_token"            to accessToken,
            "oauth_version"          to "1.0",
        )

        val allParams = (oauthParams + bodyParams)
            .entries
            .sortedWith(compareBy({ it.key }, { it.value }))
            .joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

        val baseString = "${method.uppercase()}&${encode(url)}&${encode(allParams)}"
        val signingKey = "${encode(consumerSecret)}&${encode(accessTokenSecret)}"
        val signature  = hmacSha1(signingKey, baseString)

        val headerParams = (oauthParams + mapOf("oauth_signature" to signature))
            .entries.sortedBy { it.key }
            .joinToString(", ") { """${it.key}="${encode(it.value)}"""" }

        return "OAuth $headerParams"
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32).also { rng.nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes).replace(Regex("[^A-Za-z0-9]"), "")
    }

    private fun hmacSha1(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
}
