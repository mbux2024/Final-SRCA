package com.streambert.tv.data.trakt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── OAuth device-code flow DTOs ───────────────────────────────────────────────

@Serializable
data class TraktDeviceCodeRequest(
    @SerialName("client_id") val clientId: String
)

@Serializable
data class TraktDeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int = 600,
    @SerialName("interval") val interval: Int = 5
)

@Serializable
data class TraktDeviceTokenRequest(
    @SerialName("code") val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String
)

@Serializable
data class TraktRefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("redirect_uri") val redirectUri: String = "urn:ietf:wg:oauth:2.0:oob",
    @SerialName("grant_type") val grantType: String = "refresh_token"
)

@Serializable
data class TraktRevokeRequest(
    @SerialName("token") val token: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String
)

@Serializable
data class TraktToken(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("scope") val scope: String? = null
)

// ── User + watchlist DTOs ─────────────────────────────────────────────────────

@Serializable
data class TraktUserSettings(
    val user: TraktUser? = null
)

@Serializable
data class TraktUser(
    val username: String? = null,
    val ids: TraktIds? = null
)

@Serializable
data class TraktIds(
    val trakt: Int? = null,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Int? = null
)

/** One entry from `sync/watchlist/{type}` — either a movie or a show. */
@Serializable
data class TraktWatchlistItem(
    val rank: Int? = null,
    @SerialName("listed_at") val listedAt: String? = null,
    val type: String? = null,
    val movie: TraktMedia? = null,
    val show: TraktMedia? = null
)

@Serializable
data class TraktMedia(
    val title: String? = null,
    val year: Int? = null,
    val ids: TraktIds? = null
)

// ── Auth state + poll results (domain types) ──────────────────────────────────

/** Snapshot of the current Trakt auth for the UI. */
data class TraktAuthState(
    val hasCredentials: Boolean = false,
    val isAuthenticated: Boolean = false,
    val username: String? = null
)

/** Result of a single poll of `oauth/device/token`. */
sealed interface TraktPollResult {
    data object Pending : TraktPollResult
    data class SlowDown(val newInterval: Int) : TraktPollResult
    data class Approved(val username: String?) : TraktPollResult
    data object Expired : TraktPollResult
    data object Denied : TraktPollResult
    data object AlreadyUsed : TraktPollResult
    data class Failed(val reason: String) : TraktPollResult
}
