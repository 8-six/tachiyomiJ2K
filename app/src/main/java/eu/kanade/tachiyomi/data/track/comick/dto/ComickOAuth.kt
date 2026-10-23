package eu.kanade.tachiyomi.data.track.comick.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ComickOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String? = null,
    /** Seconds until it expires, as in the token response (`expires_in: 900`). */
    @SerialName("expires_in")
    val expiresIn: Long? = null,
    /** When it expires in epoch seconds, if the response has that instead. Preferred when both are there. */
    @SerialName("expires_at")
    val expiresAt: Long? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    val scope: String = "",
    /**
     * When this was received, as `expires_in` counts from it. It isn't part of the response, and has to be written out
     * with the token, or a restored token would count from the moment it was restored.
     */
    @EncodeDefault
    val receivedAt: Long = System.currentTimeMillis(),
) {
    private fun expiryMillis(): Long {
        expiresAt?.takeIf { it > 0 }?.let { return it * 1000 }
        return receivedAt + (expiresIn?.takeIf { it > 0 } ?: DEFAULT_EXPIRY_SECONDS) * 1000
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now + REFRESH_MARGIN_MS >= expiryMillis()

    companion object {
        private const val DEFAULT_EXPIRY_SECONDS = 900L
        private const val REFRESH_MARGIN_MS = 60_000L
    }
}
