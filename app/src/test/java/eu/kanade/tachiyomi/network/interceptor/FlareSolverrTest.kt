package eu.kanade.tachiyomi.network.interceptor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cookie
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class FlareSolverrTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private val okBody =
        """
        {"status":"ok","message":"Challenge solved!","solution":{"url":"https://example.org/","status":200,
        "userAgent":"Mozilla/5.0 Test",
        "cookies":[
          {"name":"cf_clearance","value":"abc","domain":".example.org","path":"/","expiry":2000000000,"httpOnly":true,"secure":true},
          {"name":"other","value":"1","domain":"example.org","path":"/","expires":-1}
        ]}}
        """.trimIndent()

    @Test
    fun `sends a request get with the cookies and parses the solution`() {
        server.enqueue(MockResponse.Builder().body(okBody).build())

        val cookie =
            Cookie
                .Builder()
                .name("session")
                .value("s1")
                .domain("example.org")
                .build()
        val solution =
            FlareSolverrInterceptor.solve(client, server.url("/").toString(), "https://example.org/", 60, listOf(cookie))!!

        assertEquals("Mozilla/5.0 Test", solution.userAgent)
        val clearance = solution.cookies.first { it.name == "cf_clearance" }
        assertEquals("abc", clearance.value)
        assertEquals("example.org", clearance.domain)
        assertTrue(clearance.secure)
        assertTrue(clearance.httpOnly)
        assertEquals(2_000_000_000_000L, clearance.expiresAt)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("request.get", body["cmd"]!!.jsonPrimitive.content)
        assertEquals("https://example.org/", body["url"]!!.jsonPrimitive.content)
        assertEquals("60000", body["maxTimeout"]!!.jsonPrimitive.content)
        assertEquals(
            "session",
            body["cookies"]!!
                .jsonArray
                .first()
                .jsonObject["name"]!!
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a url that already ends in v1 is kept`() {
        server.enqueue(MockResponse.Builder().body(okBody).build())
        FlareSolverrInterceptor.solve(client, server.url("/v1/").toString(), "https://example.org/", 30)
        assertEquals("/v1", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `an error status is reported with the message`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(500)
                .body("""{"status":"error","message":"Timeout"}""")
                .build(),
        )
        try {
            FlareSolverrInterceptor.solve(client, server.url("/").toString(), "https://example.org/", 30)
            fail("expected an exception")
        } catch (e: IOException) {
            assertEquals("Timeout", e.message)
        }
    }

    @Test
    fun `garbage from the server is an io error`() {
        server.enqueue(MockResponse.Builder().body("<html>nope</html>").build())
        try {
            FlareSolverrInterceptor.solve(client, server.url("/").toString(), "https://example.org/", 30)
            fail("expected an exception")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test
    fun `invalid url is rejected`() {
        try {
            FlareSolverrInterceptor.solve(client, "not a url", "https://example.org/", 30)
            fail("expected an exception")
        } catch (e: IOException) {
            // expected
        }
    }
}
