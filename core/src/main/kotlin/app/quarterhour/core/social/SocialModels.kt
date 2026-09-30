package app.quarterhour.core.social

import kotlinx.serialization.Serializable

@Serializable
enum class Network { BLUESKY, MASTODON }

@Serializable
enum class MediaType { IMAGE, VIDEO }

@Serializable
data class Media(
    val type: MediaType,
    /** Full-size image, or the video stream (MP4 or HLS .m3u8). */
    val url: String,
    val previewUrl: String?,
    val alt: String?,
    /** width / height when known. */
    val aspectRatio: Float? = null,
)

@Serializable
data class SocialPost(
    /** Unique across networks: "<network>:<native id>". */
    val id: String,
    val network: Network,
    val authorName: String,
    /** @handle or user@instance */
    val authorHandle: String,
    val avatarUrl: String?,
    val text: String,
    val createdAtMillis: Long,
    val media: List<Media>,
    val likeCount: Int,
    /** Link to the post on its network. */
    val url: String,
    /** Content labels/warnings (e.g. "nudity", "graphic-media", spoiler text). */
    val labels: List<String> = emptyList(),
)

/** Tokens for a connected account. Stored encrypted by the app. */
@Serializable
sealed interface SocialAccount {
    val network: Network
    val displayHandle: String

    @Serializable
    data class Bluesky(
        val service: String,
        val handle: String,
        val did: String,
        val accessJwt: String,
        val refreshJwt: String,
    ) : SocialAccount {
        override val network get() = Network.BLUESKY
        override val displayHandle get() = "@$handle"
    }

    @Serializable
    data class Mastodon(
        val instance: String,
        val acct: String,
        val accessToken: String,
    ) : SocialAccount {
        override val network get() = Network.MASTODON
        override val displayHandle get() = "@$acct@${instance.removePrefix("https://")}"
    }
}

class SocialAuthException(message: String) : Exception(message)
