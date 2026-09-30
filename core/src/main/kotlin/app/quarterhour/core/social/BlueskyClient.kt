package app.quarterhour.core.social

import app.quarterhour.core.net.Http
import app.quarterhour.core.net.HttpException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reads the user's own Bluesky "Following" timeline through the public AT
 * Protocol API. Sign-in uses an *app password* (Settings → Privacy and
 * security → App passwords), never the main password, and only the returned
 * session tokens are stored.
 */
class BlueskyClient(private val http: Http) {

    suspend fun signIn(handle: String, appPassword: String, service: String = DEFAULT_SERVICE): SocialAccount.Bluesky {
        val body = buildJsonObject {
            put("identifier", handle.trim().removePrefix("@"))
            put("password", appPassword.trim())
        }.toString()
        val resp = http.postJson("$service/xrpc/com.atproto.server.createSession", body)
        if (resp.code == 401) throw SocialAuthException("Wrong handle or app password")
        if (!resp.isSuccessful) throw HttpException(resp.code, "createSession")
        return parseSession(json.parseToJsonElement(resp.body), service)
    }

    suspend fun refresh(account: SocialAccount.Bluesky): SocialAccount.Bluesky {
        val resp = http.postJson(
            "${account.service}/xrpc/com.atproto.server.refreshSession", "",
            mapOf("Authorization" to "Bearer ${account.refreshJwt}"),
        )
        if (!resp.isSuccessful) throw SocialAuthException("Bluesky session expired — sign in again")
        return parseSession(json.parseToJsonElement(resp.body), account.service)
    }

    /** Returns timeline posts and a possibly refreshed account (persist it if it changed). */
    suspend fun timeline(account: SocialAccount.Bluesky, limit: Int = 100): Pair<List<SocialPost>, SocialAccount.Bluesky> {
        var acct = account
        var resp = fetchTimeline(acct, limit)
        if (resp.code == 400 || resp.code == 401) {
            acct = refresh(acct)
            resp = fetchTimeline(acct, limit)
        }
        if (!resp.isSuccessful) throw HttpException(resp.code, "getTimeline")
        return parseTimeline(resp.body) to acct
    }

    private suspend fun fetchTimeline(account: SocialAccount.Bluesky, limit: Int) = http.get(
        "${account.service}/xrpc/app.bsky.feed.getTimeline?limit=$limit",
        mapOf("Authorization" to "Bearer ${account.accessJwt}"),
    )

    companion object {
        const val DEFAULT_SERVICE = "https://bsky.social"
        private val json = Json { ignoreUnknownKeys = true }

        internal fun parseSession(el: JsonElement, service: String): SocialAccount.Bluesky {
            val access = el.str("accessJwt") ?: throw SocialAuthException("Bluesky sign-in failed")
            return SocialAccount.Bluesky(
                service = service,
                handle = el.str("handle").orEmpty(),
                did = el.str("did").orEmpty(),
                accessJwt = access,
                refreshJwt = el.str("refreshJwt").orEmpty(),
            )
        }

        internal fun parseTimeline(body: String): List<SocialPost> {
            val root = json.parseToJsonElement(body)
            return root.arr("feed").orEmpty().mapNotNull { item ->
                // Skip replies to keep the reel to standalone posts; reposts are fine.
                if (item.obj("reply") != null) return@mapNotNull null
                parsePost(item.obj("post") ?: return@mapNotNull null)
            }
        }

        internal fun parsePost(post: JsonObject): SocialPost? {
            val uri = post.str("uri") ?: return null
            val author = post.obj("author")
            val handle = author.str("handle").orEmpty()
            val record = post.obj("record")
            val embed = post.obj("embed")
            val media = parseEmbed(embed) + parseEmbed(embed.obj("media"))
            val rkey = uri.substringAfterLast('/')
            return SocialPost(
                id = "bsky:$uri",
                network = Network.BLUESKY,
                authorName = author.str("displayName")?.ifBlank { null } ?: handle,
                authorHandle = "@$handle",
                avatarUrl = author.str("avatar"),
                text = record.str("text").orEmpty(),
                createdAtMillis = parseIsoMillis(record.str("createdAt") ?: post.str("indexedAt")),
                media = media,
                likeCount = post.int("likeCount") ?: 0,
                url = "https://bsky.app/profile/$handle/post/$rkey",
                labels = post.arr("labels").orEmpty().mapNotNull { it.str("val") },
            )
        }

        private fun parseEmbed(embed: JsonObject?): List<Media> {
            if (embed == null) return emptyList()
            return when (embed.str("\$type")) {
                "app.bsky.embed.images#view" -> embed.arr("images").orEmpty().mapNotNull { img ->
                    val full = img.str("fullsize") ?: return@mapNotNull null
                    Media(MediaType.IMAGE, full, img.str("thumb"), img.str("alt")?.ifBlank { null }, ratio(img.obj("aspectRatio")))
                }
                "app.bsky.embed.video#view" -> listOfNotNull(
                    embed.str("playlist")?.let {
                        Media(MediaType.VIDEO, it, embed.str("thumbnail"), embed.str("alt"), ratio(embed.obj("aspectRatio")))
                    },
                )
                else -> emptyList()
            }
        }

        private fun ratio(o: JsonObject?): Float? {
            val w = o.int("width") ?: return null
            val h = o.int("height")?.takeIf { it > 0 } ?: return null
            return w.toFloat() / h
        }
    }
}
