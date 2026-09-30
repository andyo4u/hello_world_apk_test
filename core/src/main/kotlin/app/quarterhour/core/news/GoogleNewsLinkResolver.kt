package app.quarterhour.core.news

import app.quarterhour.core.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.util.Base64

/**
 * Turns `news.google.com/rss/articles/<id>` links into the publisher's URL.
 *
 * Older ids embed the URL directly in a base64 protobuf. Newer ids (payload
 * starting with "AU_yqL") must be exchanged through Google News' own
 * `batchexecute` endpoint using the signature and timestamp printed on the
 * article's interstitial page. That endpoint is unofficial and may change, so
 * callers should treat a null result as "can't open this article".
 */
class GoogleNewsLinkResolver(private val http: Http) {

    suspend fun resolve(link: String): String? {
        val id = articleId(link) ?: return link.takeUnless { isGoogleNews(it) }
        decodeOffline(id)?.let { return it }
        return runCatching { decodeOnline(id) }.getOrNull()
    }

    private suspend fun decodeOnline(id: String): String? {
        val page = http.get("${GoogleNewsFeeds.BASE}/rss/articles/$id")
        if (!page.isSuccessful) return null
        val params = parseSignature(page.body) ?: return null
        val payload = batchPayload(id, params.timestamp, params.signature)
        val resp = http.postForm(
            "${GoogleNewsFeeds.BASE}/_/DotsSplashUi/data/batchexecute",
            mapOf("f.req" to payload),
            mapOf("Referer" to "${GoogleNewsFeeds.BASE}/"),
        )
        if (!resp.isSuccessful) return null
        return parseBatchResponse(resp.body)
    }

    internal data class Signature(val signature: String, val timestamp: String)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun isGoogleNews(url: String): Boolean =
            Domains.of(url)?.let { Domains.matches(it, "news.google.com") } == true

        internal fun articleId(link: String): String? {
            if (!isGoogleNews(link)) return null
            val path = runCatching { java.net.URI(link).path }.getOrNull() ?: return null
            val parts = path.trim('/').split('/')
            val idx = parts.indexOf("articles")
            return parts.getOrNull(idx + 1)?.takeIf { idx >= 0 && it.isNotEmpty() }
        }

        /** Returns the embedded URL for old-style ids, or null for new-style/unknown ids. */
        internal fun decodeOffline(id: String): String? {
            val bytes = runCatching {
                Base64.getUrlDecoder().decode(id.padEnd((id.length + 3) / 4 * 4, '='))
            }.getOrNull() ?: return null
            // Layout: 0x08 0x13 0x22 <varint length> <url bytes> …
            if (bytes.size < 4 || bytes[0] != 0x08.toByte() || bytes[1] != 0x13.toByte() || bytes[2] != 0x22.toByte()) {
                return null
            }
            var pos = 3
            var len = 0
            var shift = 0
            while (pos < bytes.size) {
                val b = bytes[pos++].toInt() and 0xFF
                len = len or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > 28) return null
            }
            if (len <= 0 || pos + len > bytes.size) return null
            val text = String(bytes, pos, len, Charsets.UTF_8)
            return text.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }

        internal fun parseSignature(html: String): Signature? {
            val el = Jsoup.parse(html).selectFirst("[data-n-a-sg][data-n-a-ts]") ?: return null
            return Signature(el.attr("data-n-a-sg"), el.attr("data-n-a-ts"))
        }

        internal fun batchPayload(id: String, timestamp: String, signature: String): String {
            val inner = "[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1]," +
                "\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"$id\",$timestamp,\"$signature\"]"
            val escaped = inner.replace("\\", "\\\\").replace("\"", "\\\"")
            return "[[[\"Fbv4je\",\"$escaped\",null,\"generic\"]]]"
        }

        /** Response is `)]}'` + blank line + JSON; element [0][2] is itself JSON: ["garturlres","<url>",…]. */
        internal fun parseBatchResponse(body: String): String? = runCatching {
            val start = body.indexOf('[')
            val outer = json.parseToJsonElement(body.substring(start)).jsonArray
            val row = outer.first { it is JsonArray && it.jsonArray.getOrNull(0)?.jsonPrimitive?.content == "wrb.fr" }.jsonArray
            val inner = json.parseToJsonElement(row[2].jsonPrimitive.content).jsonArray
            inner[1].jsonPrimitive.content.takeIf { it.startsWith("http") }
        }.getOrNull()
    }
}
