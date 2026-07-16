package com.streambert.tv.data.trakt

import com.streambert.tv.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Owns the Trakt.tv OAuth device-code flow and token lifecycle.
 *
 * Flow (matches NuvioTV / the Trakt docs):
 *  1. [startDeviceAuth] → user code + verification URL to display.
 *  2. The UI polls [pollDeviceToken] every `interval` seconds until the user
 *     approves the code at trakt.tv/activate.
 *  3. On approval the access/refresh tokens are persisted and the username is
 *     fetched from `users/settings`.
 *
 * Access tokens are refreshed transparently by [validAuthHeader], so callers
 * (e.g. the watchlist) never deal with expiry directly.
 */
class TraktAuthRepository(
    private val api: TraktApi,
    private val settings: SettingsRepository
) {
    private val refreshMutex = Mutex()
    private val refreshLeewaySeconds = 120L

    /** Reactive auth state for the Settings screen. */
    val authState: Flow<TraktAuthState> = combine(
        settings.traktClientId,
        settings.traktClientSecret,
        settings.traktAuthenticated,
        settings.traktUsername
    ) { id, secret, authed, username ->
        TraktAuthState(
            hasCredentials = id.isNotBlank() && secret.isNotBlank(),
            isAuthenticated = authed,
            username = username.ifBlank { null }
        )
    }.distinctUntilChanged() // DataStore re-emits on every write; only react to real changes.

    suspend fun hasCredentials(): Boolean =
        settings.currentTraktClientId().isNotBlank() && settings.currentTraktClientSecret().isNotBlank()

    /** Request a fresh device code to show the user. */
    suspend fun startDeviceAuth(): Result<TraktDeviceCode> {
        val clientId = settings.currentTraktClientId()
        if (clientId.isBlank() || settings.currentTraktClientSecret().isBlank()) {
            return Result.failure(IllegalStateException("Enter your Trakt Client ID and Secret first."))
        }
        return try {
            val resp = api.requestDeviceCode(TraktDeviceCodeRequest(clientId = clientId))
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                Result.success(body)
            } else if (resp.code() == 429) {
                Result.failure(IllegalStateException("Trakt is rate-limiting requests. Try again in a few minutes."))
            } else {
                Result.failure(IllegalStateException("Couldn't start Trakt sign-in (HTTP ${resp.code()})."))
            }
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Network error contacting Trakt. Check your connection."))
        }
    }

    /** Poll once for the token. The caller loops with the returned interval. */
    suspend fun pollDeviceToken(deviceCode: String): TraktPollResult {
        val clientId = settings.currentTraktClientId()
        val clientSecret = settings.currentTraktClientSecret()
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return TraktPollResult.Failed("Missing Trakt credentials.")
        }

        val resp = try {
            api.requestDeviceToken(
                TraktDeviceTokenRequest(code = deviceCode, clientId = clientId, clientSecret = clientSecret)
            )
        } catch (e: IOException) {
            return TraktPollResult.Pending // transient; keep polling
        }

        val token = resp.body()
        if (resp.isSuccessful && token != null && token.accessToken.isNotBlank()) {
            persistToken(token)
            val username = runCatching { fetchAndStoreUsername() }.getOrNull()
            return TraktPollResult.Approved(username)
        }

        return when (resp.code()) {
            400 -> TraktPollResult.Pending          // waiting for the user
            409 -> TraktPollResult.AlreadyUsed       // code already approved
            404 -> TraktPollResult.Failed("Invalid device code.")
            410 -> TraktPollResult.Expired
            418 -> TraktPollResult.Denied
            429 -> TraktPollResult.SlowDown(newInterval = 10)
            else -> TraktPollResult.Failed("Sign-in failed (HTTP ${resp.code()}).")
        }
    }

    /** Revoke the token server-side (best-effort) and clear local auth. */
    suspend fun signOut() {
        val token = settings.currentTraktAccessToken()
        val clientId = settings.currentTraktClientId()
        val clientSecret = settings.currentTraktClientSecret()
        if (token.isNotBlank() && clientId.isNotBlank() && clientSecret.isNotBlank()) {
            runCatching {
                api.revokeToken(TraktRevokeRequest(token = token, clientId = clientId, clientSecret = clientSecret))
            }
        }
        settings.clearTraktAuth()
    }

    /**
     * Returns a valid `Bearer <token>` header, refreshing first if the token is
     * expired/expiring. Returns null when not authenticated or on failure.
     */
    suspend fun validAuthHeader(): String? {
        if (!settings.currentTraktAuthenticated()) return null
        if (isTokenExpiringSoon() && !refreshToken()) return null
        val token = settings.currentTraktAccessToken()
        return if (token.isBlank()) null else "Bearer $token"
    }

    private suspend fun isTokenExpiringSoon(): Boolean {
        val createdAt = settings.currentTraktCreatedAt()
        val expiresIn = settings.currentTraktExpiresIn()
        if (createdAt <= 0L || expiresIn <= 0L) return true
        val nowSeconds = System.currentTimeMillis() / 1000L
        return nowSeconds >= (createdAt + expiresIn - refreshLeewaySeconds)
    }

    private suspend fun refreshToken(): Boolean = refreshMutex.withLock {
        val refresh = settings.currentTraktRefreshToken()
        val clientId = settings.currentTraktClientId()
        val clientSecret = settings.currentTraktClientSecret()
        if (refresh.isBlank() || clientId.isBlank() || clientSecret.isBlank()) return@withLock false

        // Re-check under the lock: another caller may have just refreshed.
        if (!isTokenExpiringSoon()) return@withLock true

        val resp = try {
            api.refreshToken(
                TraktRefreshTokenRequest(refreshToken = refresh, clientId = clientId, clientSecret = clientSecret)
            )
        } catch (e: IOException) {
            return@withLock false
        }

        val token = resp.body()
        if (resp.isSuccessful && token != null && token.accessToken.isNotBlank()) {
            persistToken(token)
            true
        } else {
            // Refresh token is dead (401/403) → force re-auth.
            if (resp.code() == 401 || resp.code() == 403) settings.clearTraktAuth()
            false
        }
    }

    private suspend fun persistToken(token: TraktToken) {
        val createdAt = if (token.createdAt > 0) token.createdAt else System.currentTimeMillis() / 1000L
        val expiresIn = if (token.expiresIn > 0) token.expiresIn else 90L * 24 * 3600
        settings.saveTraktToken(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            createdAt = createdAt,
            expiresIn = expiresIn
        )
    }

    private suspend fun fetchAndStoreUsername(): String? {
        val header = "Bearer ${settings.currentTraktAccessToken()}"
        val resp = api.getUserSettings(header)
        val username = resp.body()?.user?.username
        if (!username.isNullOrBlank()) settings.setTraktUsername(username)
        return username
    }
}
