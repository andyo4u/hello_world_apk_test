package app.quarterhour.core.reader

import kotlinx.serialization.Serializable

/** One piece of a cleaned article, rendered natively by the app (no WebView, no scripts). */
@Serializable
sealed interface Block {
    @Serializable data class Heading(val text: String, val level: Int) : Block
    @Serializable data class Paragraph(val text: String) : Block
    @Serializable data class Quote(val text: String) : Block
    @Serializable data class ListItem(val text: String, val ordinal: Int?) : Block
    @Serializable data class Image(val url: String, val caption: String?) : Block
    /** Poster frame for an embedded video; tapping opens [link] with the publisher. */
    @Serializable data class VideoPoster(val imageUrl: String, val link: String) : Block
}

@Serializable
data class ReaderArticle(
    val url: String,
    val title: String,
    val byline: String?,
    val siteName: String?,
    val publishedAtMillis: Long?,
    val blocks: List<Block>,
) {
    val text: String
        get() = blocks.joinToString("\n") {
            when (it) {
                is Block.Heading -> it.text
                is Block.Paragraph -> it.text
                is Block.Quote -> it.text
                is Block.ListItem -> it.text
                else -> ""
            }
        }

    val wordCount: Int get() = text.split(Regex("\\s+")).count { it.isNotBlank() }

    val imageUrls: List<String>
        get() = blocks.mapNotNull {
            when (it) {
                is Block.Image -> it.url
                is Block.VideoPoster -> it.imageUrl
                else -> null
            }
        }
}
