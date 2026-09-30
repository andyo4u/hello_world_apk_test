package app.quarterhour.core.news

import app.quarterhour.core.filter.AiContentDetector
import app.quarterhour.core.filter.ClickbaitDetector
import app.quarterhour.core.filter.FilterReason
import app.quarterhour.core.filter.ImageProvenance
import app.quarterhour.core.net.HttpResponse
import app.quarterhour.core.paywall.FreeSourceFinder
import app.quarterhour.core.paywall.PaywallDetector
import app.quarterhour.core.paywall.TitleSimilarity
import app.quarterhour.core.profile.InterestModel
import app.quarterhour.core.profile.Ranker
import app.quarterhour.core.reader.ArticleExtractor
import app.quarterhour.core.reader.ReaderArticle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

@Serializable
data class FeedItem(
    val article: Article,
    val reader: ReaderArticle,
    /** Set when the original was paywalled and this is the same story from a free outlet. */
    val substitutedFrom: String? = null,
)

@Serializable
data class FilteredItem(
    val title: String,
    val sourceName: String,
    val link: String,
    val reason: FilterReason,
    val details: List<String> = emptyList(),
    val feedKey: String = "",
)

@Serializable
data class FeedResult(val items: List<FeedItem>, val filtered: List<FilteredItem>, val builtAtMillis: Long)

/** Network access the pipeline needs; the app passes [app.quarterhour.core.net.Http] methods. */
class PipelineIo(
    val fetch: suspend (url: String) -> HttpResponse,
    /** First bytes of an image, for provenance metadata. Null to skip the check. */
    val fetchImageHead: (suspend (url: String) -> ByteArray?)? = null,
    val resolveLink: suspend (link: String) -> String?,
)

/**
 * Builds the news feed:
 *  1. fetch Google News feeds for followed topics and keywords
 *  2. merge duplicate stories
 *  3. drop blocked sources and clickbait headlines
 *  4. rank by interest
 *  5. for the top items, open the article, extract a clean reader copy, and
 *     verify it isn't paywalled or AI-generated. Paywalled stories are replaced
 *     with the same story from a free outlet when one exists.
 */
class NewsPipeline(
    private val io: PipelineIo,
    private val feeds: GoogleNewsFeeds,
    private val paywall: PaywallDetector,
    private val clickbait: ClickbaitDetector,
    private val ai: AiContentDetector,
    private val targetCount: Int = 25,
    private val concurrency: Int = 4,
) {
    private val finder = FreeSourceFinder(paywall)

    suspend fun build(model: InterestModel, now: Long): FeedResult = coroutineScope {
        val feedUrls = buildMap {
            put("TOP", feeds.topStories())
            model.followedTopics.mapNotNull { key -> Topic.entries.find { it.id == key } }.forEach { put(it.id, feeds.topic(it)) }
            model.followedKeywords.forEach { put("q:$it", feeds.search(it)) }
        }
        val fetched = feedUrls.map { (key, url) ->
            async {
                runCatching { io.fetch(url) }.getOrNull()?.takeIf { it.isSuccessful }?.let { RssParser.parse(it.body, key) }.orEmpty()
            }
        }.awaitAll().flatten()

        val filtered = mutableListOf<FilteredItem>()
        val candidates = mergeDuplicates(fetched).filter { a ->
            when {
                model.isBlocked(a.sourceName) -> { filtered += a.filtered(FilterReason.BLOCKED_SOURCE); false }
                clickbait.isClickbait(a.title) -> {
                    filtered += a.filtered(FilterReason.CLICKBAIT, clickbait.score(a.title).reasons); false
                }
                else -> true
            }
        }

        val ranked = Ranker(model, now).rank(candidates)
        val gate = Semaphore(concurrency)
        val results = ranked.take(targetCount * 2).map { a -> async { gate.withPermit { verify(a) } } }.awaitAll()

        val items = mutableListOf<FeedItem>()
        val seenUrls = mutableSetOf<String>()
        for (r in results) {
            when (r) {
                is Verified.Ok -> if (items.size < targetCount && seenUrls.add(r.item.reader.url)) items += r.item
                is Verified.Rejected -> filtered += r.item
            }
        }
        FeedResult(items, filtered, now)
    }

    /** Opens one article: resolve → paywall pre-check → fetch → extract → full checks. */
    internal suspend fun verify(a: Article): Verified {
        val domainVerdict = paywall.checkDomain(a.sourceDomain)
        if (!domainVerdict.isPaywalled) {
            val opened = open(a.link)
            when (opened) {
                is Opened.Ok -> {
                    val aiScore = ai.score(opened.host, opened.reader.byline, opened.reader.text, aiImages(opened.reader))
                    if (aiScore.value >= AiContentDetector.DEFAULT_THRESHOLD) {
                        return Verified.Rejected(a.filtered(FilterReason.AI_CONTENT, aiScore.reasons))
                    }
                    if (clickbait.isClickbait(a.title, opened.reader.text)) {
                        return Verified.Rejected(a.filtered(FilterReason.CLICKBAIT, clickbait.score(a.title, opened.reader.text).reasons))
                    }
                    return Verified.Ok(FeedItem(a, opened.reader))
                }
                is Opened.Paywalled -> Unit // fall through to substitute
                Opened.Failed -> return Verified.Rejected(a.filtered(FilterReason.UNREADABLE))
            }
        }
        // Method 2: the same story from a free outlet.
        substitute(a)?.let { return Verified.Ok(it) }
        return Verified.Rejected(a.filtered(FilterReason.PAYWALL))
    }

    private suspend fun substitute(a: Article): FeedItem? {
        var candidates = finder.rank(a, finder.fromCluster(a.related))
        if (candidates.isEmpty()) {
            val search = runCatching { io.fetch(feeds.search(a.title)) }.getOrNull()
            if (search?.isSuccessful == true) {
                candidates = finder.rank(a, finder.fromSearch(RssParser.parse(search.body, a.feedKey)))
            }
        }
        for (c in candidates.take(MAX_SUBSTITUTE_TRIES)) {
            val opened = open(c.link) as? Opened.Ok ?: continue
            if (ai.isAi(opened.host, opened.reader.byline, opened.reader.text)) continue
            val replacement = a.copy(title = c.title.ifBlank { a.title }, link = c.link, sourceName = c.sourceName, sourceUrl = null)
            return FeedItem(replacement, opened.reader, substitutedFrom = a.sourceName)
        }
        return null
    }

    private sealed interface Opened {
        data class Ok(val reader: ReaderArticle, val host: String?) : Opened
        data object Paywalled : Opened
        data object Failed : Opened
    }

    private suspend fun open(link: String): Opened {
        val url = runCatching { io.resolveLink(link) }.getOrNull() ?: return Opened.Failed
        val host = Domains.of(url)
        if (paywall.checkDomain(host).isPaywalled) return Opened.Paywalled
        val resp = runCatching { io.fetch(url) }.getOrNull() ?: return Opened.Failed
        if (resp.code == 402) return Opened.Paywalled
        if (!resp.isSuccessful) return Opened.Failed
        val finalHost = Domains.of(resp.finalUrl) ?: host
        val extracted = runCatching { ArticleExtractor.extract(resp.finalUrl, resp.body) }.getOrNull() ?: return Opened.Failed
        val verdict = paywall.checkPage(finalHost, extracted.document, extracted.article.text)
        if (verdict.isPaywalled) return Opened.Paywalled
        return Opened.Ok(extracted.article, finalHost)
    }

    private suspend fun aiImages(reader: ReaderArticle): Int {
        val head = io.fetchImageHead ?: return 0
        val lead = reader.imageUrls.firstOrNull() ?: return 0
        val bytes = runCatching { head(lead) }.getOrNull() ?: return 0
        return if (ImageProvenance.isAiGenerated(bytes)) 1 else 0
    }

    internal sealed interface Verified {
        data class Ok(val item: FeedItem) : Verified
        data class Rejected(val item: FilteredItem) : Verified
    }

    companion object {
        private const val MAX_SUBSTITUTE_TRIES = 3
        private const val SAME_STORY = 0.6

        /** Keeps the first copy of a story and folds the others into its related coverage. */
        internal fun mergeDuplicates(articles: List<Article>): List<Article> {
            val out = mutableListOf<Article>()
            for (a in articles) {
                val idx = out.indexOfFirst { it.id == a.id || TitleSimilarity.score(it.title, a.title) >= SAME_STORY }
                if (idx < 0) {
                    out += a
                } else {
                    val kept = out[idx]
                    val extra = RelatedCoverage(a.title, a.link, a.sourceName)
                    val related = (kept.related + a.related + extra).distinctBy { it.link }.filterNot { it.link == kept.link }
                    out[idx] = kept.copy(related = related)
                }
            }
            return out
        }

        private fun Article.filtered(reason: FilterReason, details: List<String> = emptyList()) =
            FilteredItem(title, sourceName, link, reason, details, feedKey)
    }
}
