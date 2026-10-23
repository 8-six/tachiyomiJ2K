package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.AndroidCookieJar
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Solves Cloudflare challenges through a user provided FlareSolverr instance.
 *
 * It sits closer to the network than [CloudflareInterceptor], so when FlareSolverr is disabled, unreachable or
 * fails to solve the challenge the original response is passed through untouched and the built-in WebView
 * solver takes over. Modeled after Houri's / TachiyomiSY's FlareSolverr support.
 */
class FlareSolverrInterceptor(
    private val cookieManager: AndroidCookieJar,
    private val enabled: () -> Boolean,
    private val serverUrl: () -> String,
    private val timeoutSeconds: () -> Int,
) : Interceptor {
    private val client = newClient(readTimeoutSeconds = 150)

    // cf_clearance is bound to the user agent that solved it, so keep it per host instead of changing the global one
    private val solvedUserAgents = ConcurrentHashMap<String, String>()

    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (!enabled()) return chain.proceed(request)

        solvedUserAgents[request.url.host]?.let { ua ->
            request = request.newBuilder().header("User-Agent", ua).build()
        }

        val response = chain.proceed(request)
        if (!isChallenge(response)) return response

        val oldClearance = clearance(request.url)
        val solvedUserAgent =
            try {
                solveOnce(request, oldClearance)
            } catch (e: Exception) {
                Timber.w(e, "FlareSolverr failed, falling back to the built-in solver")
                // The WebView fallback must run under the original user agent
                solvedUserAgents.remove(request.url.host)
                null
            }
        // Pass the challenge on to the built-in solver, which retries with its own request
        if (solvedUserAgent == null) return response

        response.close()
        return chain.proceed(request.newBuilder().header("User-Agent", solvedUserAgent).build())
    }

    /**
     * Only one solve runs at a time. A request that was waiting behind another solve reuses its clearance cookie
     * instead of asking FlareSolverr again.
     */
    private fun solveOnce(
        request: Request,
        oldClearance: Cookie?,
    ): String? =
        synchronized(lock) {
            val current = clearance(request.url)
            val knownUserAgent = solvedUserAgents[request.url.host]
            if (current != null && current.value != oldClearance?.value && knownUserAgent != null) {
                return knownUserAgent
            }

            val solution =
                solve(
                    client,
                    serverUrl(),
                    request.url.toString(),
                    timeoutSeconds(),
                    cookieManager.get(request.url).filterNot { it.name in COOKIE_NAMES },
                ) ?: return null
            if (solution.cookies.none { it.name in COOKIE_NAMES }) {
                throw IOException("FlareSolverr returned no clearance cookie")
            }

            cookieManager.saveFromResponse(request.url, solution.cookies)
            solvedUserAgents[request.url.host] = solution.userAgent
            solution.userAgent
        }

    private fun clearance(url: HttpUrl) = cookieManager.get(url).firstOrNull { it.name in COOKIE_NAMES }

    // Only real challenges, not geo/IP blocks that a solver can't fix
    private fun isChallenge(response: Response): Boolean {
        if (response.code !in ERROR_CODES || response.header("Server") !in SERVER_CHECK) return false
        val body = response.peekBody(1024 * 1024).string()
        val document = Jsoup.parse(body, response.request.url.toString())
        return document.getElementById("challenge-error-title") != null ||
            document.getElementById("challenge-error-text") != null
    }

    class Solution(
        val userAgent: String,
        val cookies: List<Cookie>,
    )

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private val COOKIE_NAMES = listOf("cf_clearance")
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        private fun newClient(readTimeoutSeconds: Long) =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(readTimeoutSeconds + 30, TimeUnit.SECONDS)
                .build()

        private fun endpoint(serverUrl: String): String = serverUrl.trim().trimEnd('/').let { if (it.endsWith("/v1")) it else "$it/v1" }

        /**
         * Asks FlareSolverr to open [targetUrl]. Returns null if the response carries no solution, throws if the
         * server is unreachable or reports an error.
         */
        fun solve(
            client: OkHttpClient,
            serverUrl: String,
            targetUrl: String,
            timeoutSeconds: Int,
            cookies: List<Cookie> = emptyList(),
        ): Solution? {
            val base = endpoint(serverUrl).toHttpUrlOrNull() ?: throw IOException("Invalid FlareSolverr URL")
            val body =
                buildJsonObject {
                    put("cmd", "request.get")
                    put("url", targetUrl)
                    put("maxTimeout", timeoutSeconds * 1000)
                    put("returnOnlyCookies", true)
                    if (cookies.isNotEmpty()) {
                        put(
                            "cookies",
                            buildJsonArray {
                                cookies.forEach { c ->
                                    add(
                                        buildJsonObject {
                                            put("name", c.name)
                                            put("value", c.value)
                                        },
                                    )
                                }
                            },
                        )
                    }
                }.toString().toRequestBody(JSON)

            client
                .newCall(
                    Request
                        .Builder()
                        .url(base)
                        .post(body)
                        .build(),
                ).execute()
                .use { res ->
                    val root =
                        try {
                            json.parseToJsonElement(res.body.string()).jsonObject
                        } catch (e: Exception) {
                            throw IOException("FlareSolverr returned HTTP ${res.code}")
                        }
                    if (root["status"]?.jsonPrimitive?.contentOrNull != "ok") {
                        throw IOException(root["message"]?.jsonPrimitive?.contentOrNull ?: "FlareSolverr error")
                    }
                    val solution = root["solution"]?.jsonObject ?: return null
                    val userAgent = solution["userAgent"]?.jsonPrimitive?.contentOrNull ?: return null
                    val host = targetUrl.toHttpUrlOrNull()?.host.orEmpty()
                    val parsed =
                        (solution["cookies"] as? JsonArray)
                            .orEmpty()
                            .mapNotNull { parseCookie(it.jsonObject, host) }
                    return Solution(userAgent, parsed)
                }
        }

        private fun parseCookie(
            obj: JsonObject,
            fallbackHost: String,
        ): Cookie? {
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
            val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: return null
            val domain =
                obj["domain"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trimStart('.')
                    ?.ifEmpty { null } ?: fallbackHost
            // FlareSolverr v3 reports "expiry", v2 reports "expires" (epoch seconds)
            val expiry =
                (obj["expiry"] ?: obj["expires"])
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toDoubleOrNull()
                    ?.toLong()
            return Cookie
                .Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path(obj["path"]?.jsonPrimitive?.contentOrNull ?: "/")
                .apply {
                    if (expiry != null && expiry > 0) expiresAt(expiry * 1000)
                    if (obj["secure"]?.jsonPrimitive?.contentOrNull == "true") secure()
                    if (obj["httpOnly"]?.jsonPrimitive?.contentOrNull == "true") httpOnly()
                }.build()
        }

        /**
         * Runs a real solve against a Cloudflare protected page and returns the user agent FlareSolverr used.
         */
        fun test(
            serverUrl: String,
            timeoutSeconds: Int,
        ): String {
            val solution =
                solve(newClient(readTimeoutSeconds = timeoutSeconds + 30L), serverUrl, "https://www.google.com/", timeoutSeconds)
                    ?: throw IOException("No solution returned")
            return solution.userAgent
        }
    }
}
