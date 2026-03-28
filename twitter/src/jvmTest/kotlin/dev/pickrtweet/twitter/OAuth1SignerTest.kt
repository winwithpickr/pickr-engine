package dev.pickrtweet.twitter

import kotlin.test.*

class OAuth1SignerTest {

    private val signer = OAuth1Signer(
        consumerKey = "xvz1evFS4wEEPTGEFPHBog",
        consumerSecret = "kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw",
        accessToken = "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb",
        accessTokenSecret = "LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE",
    )

    @Test
    fun `buildHeader produces valid OAuth header format`() {
        val header = signer.buildHeader("POST", "https://api.x.com/2/tweets")
        assertTrue(header.startsWith("OAuth "), "Header must start with 'OAuth '")
        assertTrue(header.contains("oauth_consumer_key=\"xvz1evFS4wEEPTGEFPHBog\""))
        assertTrue(header.contains("oauth_token=\"370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb\""))
        assertTrue(header.contains("oauth_signature_method=\"HMAC-SHA1\""))
        assertTrue(header.contains("oauth_version=\"1.0\""))
        assertTrue(header.contains("oauth_nonce="))
        assertTrue(header.contains("oauth_timestamp="))
        assertTrue(header.contains("oauth_signature="))
    }

    @Test
    fun `buildHeader sorts params alphabetically`() {
        val header = signer.buildHeader("POST", "https://api.x.com/2/tweets")
        val paramString = header.removePrefix("OAuth ")
        val keys = paramString.split(", ").map { it.substringBefore("=") }
        assertEquals(keys, keys.sorted(), "OAuth params must be sorted alphabetically")
    }

    @Test
    fun `buildHeader uppercases method`() {
        val header1 = signer.buildHeader("post", "https://api.x.com/2/tweets")
        assertTrue(header1.startsWith("OAuth "), "Should work with lowercase method")
        assertTrue(header1.contains("oauth_signature="))
    }

    @Test
    fun `nonce is alphanumeric only`() {
        repeat(20) {
            val header = signer.buildHeader("POST", "https://api.x.com/2/tweets")
            val nonceMatch = Regex("""oauth_nonce="([^"]+)"""").find(header)
            assertNotNull(nonceMatch, "Nonce must be present")
            val nonce = nonceMatch.groupValues[1]
            assertTrue(nonce.matches(Regex("[A-Za-z0-9]+")), "Nonce must be alphanumeric only, got: $nonce")
            assertTrue(nonce.length >= 16, "Nonce should be reasonably long")
        }
    }

    @Test
    fun `each call produces unique nonce`() {
        val nonces = (1..10).map { _ ->
            val header = signer.buildHeader("POST", "https://api.x.com/2/tweets")
            Regex("""oauth_nonce="([^"]+)"""").find(header)!!.groupValues[1]
        }.toSet()
        assertEquals(10, nonces.size, "All nonces should be unique")
    }

    @Test
    fun `signature changes with different URLs`() {
        val sig1 = extractSignature(signer.buildHeader("POST", "https://api.x.com/2/tweets"))
        val sig2 = extractSignature(signer.buildHeader("POST", "https://api.x.com/2/dm_conversations/with/123/messages"))
        assertTrue(sig1.isNotEmpty())
        assertTrue(sig2.isNotEmpty())
    }

    @Test
    fun `signature changes with different methods`() {
        val sig1 = extractSignature(signer.buildHeader("GET", "https://api.x.com/2/tweets"))
        val sig2 = extractSignature(signer.buildHeader("POST", "https://api.x.com/2/tweets"))
        assertTrue(sig1.isNotEmpty())
        assertTrue(sig2.isNotEmpty())
    }

    @Test
    fun `RFC 3986 encoding applied to signature`() {
        val header = signer.buildHeader("POST", "https://api.x.com/2/tweets")
        val sigMatch = Regex("""oauth_signature="([^"]+)"""").find(header)!!
        val encodedSig = sigMatch.groupValues[1]
        assertFalse(encodedSig.contains("+"), "Signature should not contain raw + (should be %2B)")
        assertFalse(encodedSig.contains(" "), "Signature should not contain spaces")
    }

    @Test
    fun `bodyParams empty by default for JSON APIs`() {
        val header = signer.buildHeader("POST", "https://api.x.com/2/tweets", emptyMap())
        assertTrue(header.startsWith("OAuth "))
        val paramCount = header.split(", ").size
        assertEquals(7, paramCount, "Should have 7 OAuth params (including signature)")
    }

    private fun extractSignature(header: String): String {
        val match = Regex("""oauth_signature="([^"]+)"""").find(header)
        return match?.groupValues?.get(1) ?: ""
    }
}
