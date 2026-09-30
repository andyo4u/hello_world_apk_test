package app.quarterhour.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HttpResponse(
    val code: Int,
    val body: String,
    /** URL after redirects. */
    val finalUrl: String,
    /** Server `Date` header in epoch millis, used as a trusted clock by the time budget. */
    val serverDateMillis: Long?,
) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Supplies extra cookies for a URL, e.g. publisher sessions the user signed in to. */
fun interface CookieSource {
    fun cookiesFor(url: String): String?
}

/** Called with each server `Date` so the budget can detect a tampered device clock. */
fun interface ServerTimeListener {
    fun onServerTime(epochMillis: Long)
}

/**
 * Thin coroutine wrapper around OkHttp. Requests never carry third-party
 * tracking and identify as a normal mobile browser so publishers serve the
 * standard page (not an AMP/app-install interstitial).
 */
class Http(
    private val client: OkHttpClient = defaultClient(),
    private val cookies: CookieSource = CookieSource { null },
    private val serverTime: ServerTimeListener = ServerTimeListener { },
) {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse =
        execute(Request.Builder().url(url).get(), url, headers)

    /** First [maxBytes] of a resource (e.g. image metadata), or null on failure. */
    suspend fun head(url: String, maxBytes: Int = 128 * 1024): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Range", "bytes=0-${maxBytes - 1}")
            .build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.source()?.let { src ->
                    src.request(maxBytes.toLong())
                    val n = minOf(src.buffer.size, maxBytes.toLong())
                    src.buffer.readByteArray(n)
                }
            }
        }.getOrNull()
    }

    suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpResponse {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url).post(body), url, headers)
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): HttpResponse =
        execute(Request.Builder().url(url).post(json.toRequestBody(JSON)), url, headers)

    private suspend fun execute(builder: Request.Builder, url: String, headers: Map<String, String>): HttpResponse =
        withContext(Dispatchers.IO) {
            builder.header("User-Agent", USER_AGENT)
            builder.header("Accept-Language", "en-US,en;q=0.9")
            cookies.cookiesFor(url)?.let { builder.header("Cookie", it) }
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                val date = resp.headers.getDate("Date")?.time
                date?.let(serverTime::onServerTime)
                HttpResponse(
                    code = resp.code,
                    body = resp.body?.string().orEmpty(),
                    finalUrl = resp.request.url.toString(),
                    serverDateMillis = date,
                )
            }
        }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

class HttpException(val code: Int, url: String) : IOException("HTTP $code for $url")
