package eu.kanade.tachiyomi.data.track.comick

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferenceStore
import eu.kanade.tachiyomi.data.track.comick.dto.ComickLibraryEntry
import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import eu.kanade.tachiyomi.data.track.comick.dto.ComickSearchResult
import eu.kanade.tachiyomi.data.track.comick.dto.ComickTitleLookupResult
import eu.kanade.tachiyomi.data.track.comick.dto.ComickUser
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.PATCH
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.minutes

/**
 * ComicK's Library API (https://comick.dev/docs), OAuth 2.0 authorization code with S256 PKCE against
 * `https://api.comick.dev/integrations/v1`, and its public catalog API for the details of a title.
 *
 * Modeled after the ComicK tracker of Houri and Mihon (mihonapp/mihon#4056), and like Houri it signs in with Houri's
 * registered app, see [CLIENT_ID].
 */
class ComicKApi(
    private val trackId: Int,
    baseClient: OkHttpClient,
    interceptor: ComicKInterceptor,
) {
    private val json: Json by injectLazy()

    private val client =
        baseClient
            .newBuilder()
            // ComicK allows 60 requests a minute for an app and user, this leaves some room
            .rateLimit(permits = 55, period = 1.minutes)
            .addInterceptor {
                it
                    .request()
                    .newBuilder()
                    .header(
                        "User-Agent",
                        "Tachiyomi J2K/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID} ${BuildConfig.COMMIT_SHA}) " +
                            "(Android) (https://github.com/8-six/tachiyomiJ2K)",
                    ).header("Accept", "application/json")
                    .build()
                    .let(it::proceed)
            }.build()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    /** Adds (or replaces) a library entry. A status is required, fields that aren't sent are kept by ComicK. */
    suspend fun addLibManga(track: Track): Track =
        withIOContext {
            val body =
                buildJsonObject {
                    put("status", track.status.toApiListStatus())
                    // Ratings are 1 to 10, null clears it. 0 is "not rated" here
                    put("rating", track.score.toInt().takeIf { it > 0 })
                    // The number is a string in the API, null clears it
                    if (track.last_chapter_read > 0) {
                        putJsonObject("progress") { put("number", track.last_chapter_read.toString()) }
                    } else {
                        put("progress", null)
                    }
                }.toString()
                    .toRequestBody(jsonMime)

            authClient.newCall(PUT(libraryUrl(track.media_id.toHid()), body = body)).awaitSuccess().close()

            // Nothing more than what was sent comes back
            track
        }

    suspend fun deleteLibManga(track: Track) {
        withIOContext {
            authClient
                .newCall(DELETE(libraryUrl(track.media_id.toHid()), body = RequestBody.EMPTY))
                .awaitSuccess()
                .close()
        }
    }

    /** The library entry of a title, or null if the user doesn't follow it. */
    suspend fun findLibManga(track: Track): Track? =
        withIOContext {
            with(json) {
                try {
                    authClient
                        .newCall(GET(libraryUrl(track.media_id.toHid())))
                        .awaitSuccess()
                        .parseAs<ComickLibraryEntry>()
                        .toTrack(trackId)
                } catch (e: HttpException) {
                    if (e.code == 404) null else throw e
                }
            }
        }

    /** Only sends what's given, and fails with a 404 if the entry doesn't exist. */
    suspend fun updateLibManga(track: Track): Track =
        withIOContext {
            val body =
                buildJsonObject {
                    put("rating", track.score.toInt().takeIf { it > 0 })
                    put("status", track.status.toApiListStatus())
                    putJsonObject("progress") { put("number", track.last_chapter_read.toString()) }
                }.toString()
                    .toRequestBody(jsonMime)

            val response = authClient.newCall(PATCH(libraryUrl(track.media_id.toHid()), body = body)).awaitSuccess()

            // The response is the entry as ComicK has it after the write. Taking the three fields from it means a value
            // the server didn't accept doesn't stay shown as saved. Everything else (dates, the private flag) isn't in
            // it, so it's only these that are replaced, on the track the caller keeps.
            runCatching {
                with(json) { response.parseAs<ComickLibraryEntry>().toTrack(trackId) }
            }.getOrNull()?.let { stored ->
                track.status = stored.status
                track.last_chapter_read = stored.last_chapter_read
                track.score = stored.score
            }
            track
        }

    /** Searches for a title to track. */
    suspend fun search(search: String): List<TrackSearch> =
        withIOContext {
            val url =
                "$RESOURCE_URL/titles"
                    .toUri()
                    .buildUpon()
                    // ComicK answers a 400 to a query over 100 characters
                    .appendQueryParameter("q", search.take(100))
                    .appendQueryParameter("media_type", "manga")
                    .appendQueryParameter("limit", "20")
                    .build()

            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<ComickSearchResult>()
                    .data
                    .map { it.toTrackSearch(trackId) }
            }
        }

    /** A title by its HID, to bind to it. */
    suspend fun getMangaDetails(hid: String): TrackSearch? =
        withIOContext {
            with(json) {
                try {
                    authClient
                        .newCall(GET("$RESOURCE_URL/titles/$hid"))
                        .awaitSuccess()
                        .parseAs<ComickTitleLookupResult>()
                        .data
                        .toTrackSearch(trackId)
                } catch (e: HttpException) {
                    if (e.code == 404) null else throw e
                }
            }
        }

    /**
     * Everything the public catalog has about a title, for [eu.kanade.tachiyomi.data.track.metadata.ComicKMetadata]. The
     * catalog doesn't need an account, and unlike the library API it lists the covers with their size.
     */
    suspend fun getCatalogComic(hid: String): JsonObject =
        withIOContext {
            with(json) {
                client
                    .newCall(GET("$API_BASE_URL/v1.0/comic/$hid/"))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
            }
        }

    /** The public profile of the connected user. */
    suspend fun getCurrentUser(): ComickUser =
        withIOContext {
            with(json) {
                authClient
                    .newCall(GET("$RESOURCE_URL/me"))
                    .awaitSuccess()
                    .parseAs<ComickUser>()
            }
        }

    /**
     * Trades the code of the sign in for tokens.
     *
     * @throws ComickMissingScopesException if the user didn't allow `offline_access` or `library:write`.
     */
    suspend fun getAccessToken(code: String): ComickOAuth =
        withIOContext {
            val formBody =
                FormBody
                    .Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", CLIENT_ID)
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("code_verifier", persistedCodeVerifier())
                    .add("resource", RESOURCE_URL)
                    .build()

            with(json) {
                val oauth =
                    client
                        .newCall(POST("$OAUTH_URL/token", body = formBody))
                        .awaitSuccess()
                        .parseAs<ComickOAuth>()

                if (oauth.refreshToken.isNullOrBlank()) {
                    throw ComickMissingScopesException("Permission for ongoing synchronisation is required.")
                }
                if (!oauth.scope.contains(LIBRARY_WRITE_SCOPE)) {
                    // It's off by default so it's easily forgotten
                    throw ComickMissingScopesException("Permission to add and remove manga from your library is required.")
                }
                oauth
            }
        }

    fun verifyOAuthState(state: String): Boolean {
        val expected = persistedState()
        return expected.isNotBlank() && state == expected
    }

    companion object {
        /**
         * The public client ID of Houri (https://github.com/PineappleTwilight/houri), as that is what is used there.
         * Houri registered it at https://comick.dev/developers/apps as a public client for native apps (it has no
         * secret) with library:write, and its callbacks are the only ones ComicK accepts for it:
         * `app.houri:/comick-auth`, `app.houri.dev:/comick-auth`, `app.houri.rt:/comick-auth` and
         * `app.houri.nomtl:/comick-auth`. So the app answers to [REDIRECT_URI], see the manifest, and ComicK shows
         * Houri's name when asking for permission.
         */
        private const val CLIENT_ID = "TNHNwmJiTpJcEmDvvBhkqjtSXMEtEFjU"

        // ComicK wants a reverse domain scheme and the form of RFC 8252: a scheme and a path, no authority
        const val REDIRECT_URI = "app.houri:/comick-auth"

        private const val LIBRARY_WRITE_SCOPE = "library:write"
        private const val SCOPES = "library:read $LIBRARY_WRITE_SCOPE offline_access"

        private const val OAUTH_URL = "https://comick.dev/api/auth/oauth2"
        private const val API_BASE_URL = "https://api.comick.dev"
        private const val RESOURCE_URL = "$API_BASE_URL/integrations/v1"

        /** Where the images of covers are served from, see the `b2key` of a cover. */
        const val COVER_URL = "https://meo.comick.pictures"

        private var codeVerifier: String = ""
        private var oauthStateParam: String = ""

        // The browser can take long enough for the app to be closed in the meantime, so they're kept
        private const val PREF_CODE_VERIFIER = "comick_code_verifier"
        private const val PREF_OAUTH_STATE = "comick_oauth_state"

        private fun preferenceStore(): PreferenceStore? =
            try {
                Injekt.get<PreferenceStore>()
            } catch (e: Exception) {
                null
            }

        private fun libraryUrl(hid: String): String =
            "$RESOURCE_URL/me/library"
                .toUri()
                .buildUpon()
                .appendPath(hid)
                .build()
                .toString()

        fun authUrl(): Uri =
            "$OAUTH_URL/authorize"
                .toUri()
                .buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("resource", RESOURCE_URL)
                .appendQueryParameter("state", getOAuthStateParam())
                .appendQueryParameter("code_challenge", getPkceS256ChallengeCode())
                .appendQueryParameter("code_challenge_method", "S256")
                .build()

        fun refreshTokenRequest(refreshToken: String) =
            POST(
                "$OAUTH_URL/token",
                body =
                    FormBody
                        .Builder()
                        .add("grant_type", "refresh_token")
                        .add("client_id", CLIENT_ID)
                        .add("refresh_token", refreshToken)
                        .add("resource", RESOURCE_URL)
                        .build(),
            )

        private fun getOAuthStateParam(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            oauthStateParam = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            preferenceStore()?.getString(PREF_OAUTH_STATE, "")?.set(oauthStateParam)
            return oauthStateParam
        }

        private fun persistedState(): String {
            if (oauthStateParam.isNotBlank()) return oauthStateParam
            return preferenceStore()?.getString(PREF_OAUTH_STATE, "")?.get().orEmpty()
        }

        private fun persistedCodeVerifier(): String {
            if (codeVerifier.isNotBlank()) return codeVerifier
            return preferenceStore()?.getString(PREF_CODE_VERIFIER, "")?.get().orEmpty()
        }

        /**
         * ComicK wants a proper PKCE sign in, unlike MyAnimeList: make a verifier, send the S256 challenge made from it
         * to /authorize and the verifier itself to /token.
         */
        private fun getPkceS256ChallengeCode(): String {
            val codes = PkceUtil.generateS256Codes()
            codeVerifier = codes.codeVerifier
            preferenceStore()?.getString(PREF_CODE_VERIFIER, "")?.set(codeVerifier)
            return codes.codeChallenge
        }
    }
}
