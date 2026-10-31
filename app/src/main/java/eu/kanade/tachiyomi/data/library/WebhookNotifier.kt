package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

// KMK -->

/**
 * Sends a structured JSON message to the user's webhook (a Discord webhook gets a Discord payload)
 * when a library update finds new chapters. Delivery runs on one background worker fed by a small
 * bounded queue, so it never blocks the update and never fans out into unbounded requests. A
 * failed delivery is logged and dropped.
 */
class WebhookNotifier(
    private val preferences: PreferencesHelper,
    private val network: NetworkHelper,
) {
    data class NewChapters(
        val mangaTitle: String,
        val chapterNames: List<String>,
    )

    private val queue = Channel<NewChapters>(capacity = 20, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (event in queue) {
                try {
                    deliver(event)
                } catch (e: Exception) {
                    Timber.w(e, "Webhook delivery failed")
                }
            }
        }
    }

    fun notifyNewChapters(updates: List<NewChapters>) {
        if (!preferences.webhookEnabled().get() || preferences.webhookUrl().get().isBlank()) return
        updates.forEach { queue.trySend(it) }
    }

    private fun deliver(event: NewChapters) {
        val url = preferences.webhookUrl().get().trim()
        val body = if (isDiscord(url)) discordPayload(event) else genericPayload(event)
        val request =
            Request
                .Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
        network.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) Timber.w("Webhook responded with ${response.code}")
        }
    }

    private fun isDiscord(url: String) = DISCORD_HOSTS.any { url.contains("$it/api/webhooks/") }

    private fun genericPayload(event: NewChapters): JsonObject =
        buildJsonObject {
            put("event", "new_chapters")
            put("manga", event.mangaTitle)
            put("chapter_count", event.chapterNames.size)
            putJsonArray("chapters") { event.chapterNames.forEach { add(JsonPrimitive(it)) } }
        }

    private fun discordPayload(event: NewChapters): JsonObject =
        buildJsonObject {
            putJsonArray("embeds") {
                add(
                    buildJsonObject {
                        put("title", event.mangaTitle.take(DISCORD_TITLE_LIMIT))
                        put("description", event.chapterNames.joinToString("\n").take(DISCORD_DESCRIPTION_LIMIT))
                        putJsonObject("footer") { put("text", "${event.chapterNames.size} new chapter(s)") }
                    },
                )
            }
        }

    companion object {
        private val DISCORD_HOSTS = listOf("discord.com", "discordapp.com")
        private const val DISCORD_TITLE_LIMIT = 256
        private const val DISCORD_DESCRIPTION_LIMIT = 4000
    }
}
// KMK <--
