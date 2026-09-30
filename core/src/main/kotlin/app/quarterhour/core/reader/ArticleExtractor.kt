package app.quarterhour.core.reader

import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.OffsetDateTime

/**
 * Turns a publisher page into a [ReaderArticle]: body text, headings, quotes
 * and photos with captions. Ads, trackers, pop-ups, newsletter boxes,
 * "recommended" rails and comments are dropped.
 */
object ArticleExtractor {

    data class Result(val article: ReaderArticle, val document: Document)

    fun extract(url: String, html: String): Result {
        val original = Jsoup.parse(html, url)
        val prepared = original.clone()
        prepare(prepared)

        val parsed = Readability4JExtended(url, prepared.outerHtml()).parse()
        val content = Jsoup.parseBodyFragment(parsed.content ?: "", url)
        val blocks = toBlocks(content.body(), url).toMutableList()

        // Keep the lead photo even when the extractor dropped it from the body.
        val lead = meta(original, "og:image") ?: meta(original, "twitter:image")
        if (lead != null && blocks.none { it is Block.Image } && !isAdImage(lead)) {
            blocks.add(0, Block.Image(absolute(url, lead), null))
        }

        val article = ReaderArticle(
            url = url,
            title = parsed.title?.trim()?.ifEmpty { null } ?: meta(original, "og:title") ?: original.title(),
            byline = parsed.byline?.trim()?.ifEmpty { null } ?: meta(original, "author"),
            siteName = meta(original, "og:site_name"),
            publishedAtMillis = meta(original, "article:published_time")?.let(::parseIso),
            blocks = clean(blocks),
        )
        return Result(article, original)
    }

    /** Removes junk and un-lazies images before extraction. */
    internal fun prepare(doc: Document) {
        doc.select(JUNK_SELECTORS).remove()
        doc.select("img").forEach { img ->
            val lazy = LAZY_ATTRS.firstNotNullOfOrNull { a -> img.attr(a).ifEmpty { null } }
            if (lazy != null && (img.attr("src").isEmpty() || img.attr("src").startsWith("data:"))) {
                img.attr("src", lazy)
            }
            bestFromSrcset(img.attr("srcset").ifEmpty { img.attr("data-srcset") })?.let { img.attr("src", it) }
        }
        doc.select("picture").forEach { picture ->
            val img = picture.selectFirst("img") ?: return@forEach
            if (img.attr("src").isEmpty() || img.attr("src").startsWith("data:")) {
                picture.select("source").firstNotNullOfOrNull { bestFromSrcset(it.attr("srcset")) }
                    ?.let { img.attr("src", it) }
            }
        }
    }

    internal fun toBlocks(root: Element, baseUrl: String): List<Block> {
        val out = mutableListOf<Block>()
        fun walk(el: Element) {
            when (el.tagName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> text(el)?.let { out += Block.Heading(it, el.tagName()[1].digitToInt()) }
                "p" -> {
                    el.select("img").forEach { img -> image(img, baseUrl, null)?.let(out::add) }
                    text(el)?.let { out += Block.Paragraph(it) }
                }
                "blockquote" -> text(el)?.let { out += Block.Quote(it) }
                "ul", "ol" -> el.children().filter { it.tagName() == "li" }.forEachIndexed { i, li ->
                    text(li)?.let { out += Block.ListItem(it, if (el.tagName() == "ol") i + 1 else null) }
                }
                "figure" -> {
                    val caption = el.selectFirst("figcaption")?.let(::text)
                    val video = el.selectFirst("video[poster], iframe[src]")
                    val img = el.selectFirst("img")
                    when {
                        img != null -> image(img, baseUrl, caption)?.let(out::add)
                        video != null -> video(video, baseUrl)?.let(out::add)
                    }
                }
                "img" -> image(el, baseUrl, null)?.let(out::add)
                "video" -> video(el, baseUrl)?.let(out::add)
                else -> el.children().forEach(::walk)
            }
        }
        root.children().forEach(::walk)
        return out
    }

    private fun image(img: Element, baseUrl: String, caption: String?): Block.Image? {
        val src = img.attr("src").trim()
        if (src.isEmpty() || src.startsWith("data:") || isAdImage(src) || isTracker(img)) return null
        return Block.Image(absolute(baseUrl, src), caption)
    }

    private fun video(el: Element, baseUrl: String): Block.VideoPoster? {
        val poster = el.attr("poster").ifEmpty { null } ?: return null
        val link = el.attr("src").ifEmpty { baseUrl }
        return Block.VideoPoster(absolute(baseUrl, poster), absolute(baseUrl, link))
    }

    private fun text(el: Element): String? = el.text().replace(' ', ' ').trim().ifEmpty { null }

    /** Final pass: drop leftover ad/promo lines, duplicate images and empty runs. */
    internal fun clean(blocks: List<Block>): List<Block> {
        val seenImages = mutableSetOf<String>()
        return blocks.filter { b ->
            when (b) {
                is Block.Paragraph -> !isPromo(b.text)
                is Block.Heading -> !isPromo(b.text)
                is Block.ListItem -> !isPromo(b.text)
                is Block.Image -> seenImages.add(b.url)
                is Block.VideoPoster -> seenImages.add(b.imageUrl)
                is Block.Quote -> true
            }
        }
    }

    internal fun isPromo(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t in PROMO_EXACT) return true
        return t.length < 160 && PROMO_PREFIXES.any { t.startsWith(it) }
    }

    private fun isAdImage(src: String): Boolean {
        val s = src.lowercase()
        return AD_HOST_HINTS.any { it in s }
    }

    private fun isTracker(img: Element): Boolean {
        val w = img.attr("width").toIntOrNull()
        val h = img.attr("height").toIntOrNull()
        return (w != null && w <= 2) || (h != null && h <= 2)
    }

    internal fun bestFromSrcset(srcset: String): String? {
        if (srcset.isBlank()) return null
        val candidates = srcset.split(',').mapNotNull { part ->
            val bits = part.trim().split(Regex("\\s+"))
            val u = bits.getOrNull(0)?.ifEmpty { null } ?: return@mapNotNull null
            // Density descriptors ("2x") are treated as ~800px per x.
            val w = bits.getOrNull(1)?.let { d -> d.dropLast(1).toDoubleOrNull()?.let { if (d.endsWith("x")) it * 800 else it } } ?: 0.0
            u to w
        }
        // Largest up to 1600px wide (sharp on phones, no huge downloads); else the smallest above that.
        return (candidates.filter { it.second <= MAX_IMAGE_WIDTH }.maxByOrNull { it.second }
            ?: candidates.minByOrNull { it.second })?.first
    }

    private fun meta(doc: Document, name: String): String? =
        doc.selectFirst("meta[property=$name], meta[name=$name]")?.attr("content")?.trim()?.ifEmpty { null }

    private fun absolute(base: String, url: String): String =
        runCatching { java.net.URI(base).resolve(url.trim()).toString() }.getOrDefault(url)

    private fun parseIso(value: String): Long? =
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()

    private const val MAX_IMAGE_WIDTH = 1600.0

    private val LAZY_ATTRS = listOf("data-src", "data-lazy-src", "data-original", "data-url", "data-hi-res-src")

    internal const val JUNK_SELECTORS =
        "script, style, noscript, iframe:not([src*=youtube]):not([src*=vimeo]), form, button, dialog, " +
            "aside, nav, footer, [aria-hidden=true], [role=complementary], [role=dialog], " +
            "[class*=advert], [id*=advert], [class*=ad-slot], [class*=ad-container], [class*=adunit], " +
            "[class*=sponsor], [class*=promo], [class*=newsletter], [class*=subscribe], [class*=signup], " +
            "[class*=related], [class*=recommend], [class*=outbrain], [class*=taboola], [id*=taboola], " +
            "[class*=comments], [id*=comments], [class*=social-share], [class*=share-bar], [class*=popup], " +
            "[class*=modal], [class*=cookie], [id*=cookie], [class*=paywall], [class*=read-more], " +
            "[data-ad], [data-ad-slot], ins.adsbygoogle"

    private val AD_HOST_HINTS = listOf(
        "doubleclick.net", "googlesyndication", "adservice", "adsystem", "taboola", "outbrain",
        "scorecardresearch", "/ads/", "/ad/", "pixel.", "amazon-adsystem", "criteo",
    )

    private val PROMO_EXACT = setOf("advertisement", "ad", "sponsored", "story continues below advertisement", "skip advertisement")

    private val PROMO_PREFIXES = listOf(
        "read more:", "related:", "recommended:", "sign up for", "subscribe to our", "get our newsletter",
        "follow us on", "click here to", "download our app", "listen to this article", "advertisement",
        "sponsored content", "this story continues below", "read next:", "more from",
    )
}
