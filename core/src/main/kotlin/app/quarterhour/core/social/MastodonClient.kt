package app.quarterhour.core.social

import app.quarterhour.core.net.Http
import app.quarterhour.core.net.HttpException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Reads the user's Mastodon home timeline. Sign-in is standard OAuth: the app
 * registers itself on the user's instance, opens the instance's authorize page
 * in the browser, and exchanges the returned code for a read-only token.
 */
class MastodonClient(private val http: Http) {

    @Serializable
    data class AppRegistration(val instance: String, val clientId: String, val clientSecret: String)

    suspend fun registerApp(instanceInput: String): AppRegistration {
        val instance = normalizeInstance(instanceInput)
        val resp = http.postForm(
            "$instance/api/v1/apps",
            mapOf("client_name" to "QuarterHour", "redirect_uris" to REDIRECT_URI, "scopes" to SCOPES, "website" to "https://github.com/andyo4u/hello_world_apk_test"),
        )
        if (!resp.isSuccessful) throw HttpException(resp.code, "register app on $instance")
        val el = json.parseToJsonElement(resp.body)
        return AppRegistration(
            instance,
            el.str("client_id") ?: throw SocialAuthException("Instance didn't return a client id"),
            el.str("client_secret") ?: throw SocialAuthException("Instance didn't return a client secret"),
        )
    }

    fun authorizeUrl(reg: AppRegistration): String =
        "${reg.instance}/oauth/authorize?response_type=code&client_id=${enc(reg.clientId)}" +
            "&redirect_uri=${enc(REDIRECT_URI)}&scope=${enc(SCOPES)}"

    suspend fun exchangeCode(reg: AppRegistration, code: String): SocialAccount.Mastodon {
        val resp = http.postForm(
            "${reg.instance}/oauth/token",
            mapOf(
                "grant_type" to "authorization_code", "code" to code, "client_id" to reg.clientId,
                "client_secret" to reg.clientSecret, "redirect_uri" to REDIRECT_URI, "scope" to SCOPES,
            ),
        )
        if (!resp.isSuccessful) throw SocialAuthException("Mastodon sign-in failed (${resp.code})")
        val token = json.parseToJsonElement(resp.body).str("access_token") ?: throw SocialAuthException("No access token")
        val me = http.get("${reg.instance}/api/v1/accounts/verify_credentials", mapOf("Authorization" to "Bearer $token"))
        val acct = if (me.isSuccessful) json.parseToJsonElement(me.body).str("acct").orEmpty() else ""
        return SocialAccount.Mastodon(reg.instance, acct, token)
    }

    suspend fun homeTimeline(account: SocialAccount.Mastodon, limit: Int = 40): List<SocialPost> {
        val resp = http.get(
            "${account.instance}/api/v1/timelines/home?limit=$limit",
            mapOf("Authorization" to "Bearer ${account.accessToken}"),
        )
        if (resp.code == 401) throw SocialAuthException("Mastodon session expired — sign in again")
        if (!resp.isSuccessful) throw HttpException(resp.code, "home timeline")
        return parseTimeline(resp.body)
    }

    companion object {
        const val REDIRECT_URI = "quarterhour://oauth/mastodon"
        const val SCOPES = "read"
        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        fun normalizeInstance(input: String): String {
            val trimmed = input.trim().trimEnd('/').substringAfterLast('@')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed.replace("http://", "https://") else "https://$trimmed"
        }

        internal fun parseTimeline(body: String): List<SocialPost> =
            json.parseToJsonElement(body).jsonArray.mapNotNull { it as? JsonObject }.mapNotNull(::parseStatus)

        internal fun parseStatus(status: JsonObject): SocialPost? {
            // Boosts carry the original post in "reblog".
            val s: JsonElement = status.obj("reblog") ?: status
            if (s.str("in_reply_to_id") != null) return null
            val id = s.str("id") ?: return null
            val account = s.obj("account")
            val media = s.arr("media_attachments").orEmpty().mapNotNull { m ->
                val type = when (m.str("type")) {
                    "image" -> MediaType.IMAGE
                    "video", "gifv" -> MediaType.VIDEO
                    else -> return@mapNotNull null
                }
                val url = m.str("url") ?: return@mapNotNull null
                val meta = m.obj("meta").obj("original")
                val ratio = meta.int("width")?.let { w -> meta.int("height")?.takeIf { it > 0 }?.let { h -> w.toFloat() / h } }
                Media(type, url, m.str("preview_url"), m.str("description")?.ifBlank { null }, ratio)
            }
            val labels = buildList {
                if (s.bool("sensitive")) add("sensitive")
                s.str("spoiler_text")?.takeIf { it.isNotBlank() }?.let { add("cw:$it") }
            }
            return SocialPost(
                id = "masto:${s.str("uri") ?: id}",
                network = Network.MASTODON,
                authorName = account.str("display_name")?.ifBlank { null } ?: account.str("username").orEmpty(),
                authorHandle = "@${account.str("acct").orEmpty()}",
                avatarUrl = account.str("avatar"),
                text = Jsoup.parse(s.str("content").orEmpty()).text(),
                createdAtMillis = parseIsoMillis(s.str("created_at")),
                media = media,
                likeCount = s.int("favourites_count") ?: 0,
                url = s.str("url") ?: s.str("uri").orEmpty(),
                labels = labels,
            )
        }
    }
}
