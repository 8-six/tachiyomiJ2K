package eu.kanade.tachiyomi.data.track.comick

import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

/**
 * Adds the access token to the requests to ComicK and gets a new one when it expired. Only one request at a time does
 * that, as ComicK's refresh tokens are replaced when used, and both new tokens are kept together by [setAuth].
 */
class ComicKInterceptor(
    private val comicK: ComicK,
) : Interceptor {
    private val json: Json by injectLazy()

    @Volatile
    private var oauth: ComickOAuth? = comicK.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currentAuth = oauth ?: throw Exception("Not authenticated with ComicK")

        if (currentAuth.isExpired()) {
            currentAuth =
                synchronized(this) {
                    // Another request may have just done it
                    val latest = oauth ?: throw Exception("Not authenticated with ComicK")
                    if (!latest.isExpired()) return@synchronized latest

                    val refreshToken = latest.refreshToken
                    if (refreshToken.isNullOrBlank()) {
                        comicK.logout()
                        throw Exception("ComicK session expired")
                    }

                    val response = chain.proceed(ComicKApi.refreshTokenRequest(refreshToken))
                    if (response.isSuccessful) {
                        response
                            .use { res -> with(json) { res.parseAs<ComickOAuth>() } }
                            .also(::setAuth)
                    } else {
                        response.close()
                        // The user turned off offline access or revoked it, signing in again is all that helps
                        comicK.logout()
                        throw Exception("ComicK session expired")
                    }
                }
        }

        return originalRequest
            .newBuilder()
            .addHeader("Authorization", "Bearer ${currentAuth.accessToken}")
            .build()
            .let(chain::proceed)
    }

    fun setAuth(oauth: ComickOAuth?) {
        this.oauth = oauth
        comicK.saveToken(oauth)
    }
}
