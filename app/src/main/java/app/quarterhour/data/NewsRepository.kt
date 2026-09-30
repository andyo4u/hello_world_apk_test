package app.quarterhour.data

import android.content.Context
import app.quarterhour.core.filter.AiContentDetector
import app.quarterhour.core.filter.ClickbaitDetector
import app.quarterhour.core.filter.FilterReason
import app.quarterhour.core.news.Domains
import app.quarterhour.core.news.FeedItem
import app.quarterhour.core.news.FeedResult
import app.quarterhour.core.news.FilteredItem
import app.quarterhour.core.news.GoogleNewsFeeds
import app.quarterhour.core.news.GoogleNewsLinkResolver
import app.quarterhour.core.news.NewsPipeline
import app.quarterhour.core.news.PipelineIo
import app.quarterhour.core.net.Http
import app.quarterhour.core.paywall.PaywallDetector
import app.quarterhour.core.profile.InterestModel
import app.quarterhour.core.profile.ProfileWriter
import app.quarterhour.core.profile.Signal
import app.quarterhour.core.profile.UsageStats
import app.quarterhour.core.social.SocialPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Titles read today, for the "see you tomorrow" summary. */
@Serializable
data class DailyLog(val day: String = "", val read: List<String> = emptyList())

class NewsRepository(
    private val context: Context,
    private val http: Http,
    val settings: JsonStore<AppSettings>,
    private val accountsLabel: () -> List<String>,
) {
    private val dir = context.filesDir
    val model = JsonStore(File(dir, "interests.json"), InterestModel.serializer()) { InterestModel() }
    val stats = JsonStore(File(dir, "stats.json"), UsageStats.serializer()) { UsageStats() }
    val feed = JsonStore(File(dir, "feed.json"), FeedResult.serializer()) { FeedResult(emptyList(), emptyList(), 0) }
    val dailyLog = JsonStore(File(dir, "daily_log.json"), DailyLog.serializer()) { DailyLog() }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val profileFile: File get() = File(dir, "profile/user_profile.md")

    fun item(id: String): FeedItem? = feed.value.items.find { it.article.id == id }

    fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - feed.value.builtAtMillis > STALE_MS

    suspend fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        _error.value = null
        try {
            val s = settings.value
            val paywall = PaywallDetector(
                knownPaywallDomains = PaywallDetector.DEFAULT_PAYWALL_DOMAINS + s.extraPaywallDomains - s.allowedPaywallDomains,
                subscribedDomains = s.subscribedDomains,
            )
            val resolver = GoogleNewsLinkResolver(http)
            val pipeline = NewsPipeline(
                io = PipelineIo(fetch = { http.get(it) }, fetchImageHead = { http.head(it) }, resolveLink = { resolver.resolve(it) }),
                feeds = GoogleNewsFeeds(s.language, s.country),
                paywall = paywall,
                clickbait = ClickbaitDetector(adjustments = s.clickbaitAdjustments),
                ai = AiContentDetector(knownFarms = AiContentDetector.DEFAULT_AI_FARMS + s.aiFarmDomains),
            )
            val now = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) { pipeline.build(model.value, now) }
            if (result.items.isEmpty() && result.filtered.isEmpty()) {
                _error.value = "Couldn't reach Google News. Check your connection."
                return
            }
            feed.set(result)
            stats.update { st -> result.filtered.groupingBy { it.reason }.eachCount().entries.fold(st) { acc, (r, n) -> acc.withFiltered(r, n) } }
            writeProfile()
        } catch (e: Exception) {
            _error.value = "Refresh failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            _refreshing.value = false
        }
    }

    // --- Learning signals -------------------------------------------------

    suspend fun recordOpen(item: FeedItem) {
        signal(item, Signal.Opened)
        stats.update { it.withOpen(LocalTime.now().hour) }
        dailyLog.update { log ->
            val today = LocalDate.now().toString()
            val base = if (log.day == today) log else DailyLog(today)
            base.copy(read = (base.read + item.article.title).distinct())
        }
    }

    suspend fun recordDwell(item: FeedItem, seconds: Long) {
        if (seconds >= 5) signal(item, Signal.Dwell(seconds))
    }

    suspend fun thumb(item: FeedItem, up: Boolean) = signal(item, if (up) Signal.ThumbUp else Signal.ThumbDown)

    suspend fun lessLikeThis(item: FeedItem) {
        signal(item, Signal.LessLikeThis)
        feed.update { f -> f.copy(items = f.items.filterNot { it.article.id == item.article.id }) }
    }

    suspend fun blockSource(sourceName: String) {
        model.update { it.blockSource(sourceName) }
        feed.update { f ->
            val (blocked, kept) = f.items.partition { it.article.sourceName.equals(sourceName, ignoreCase = true) }
            f.copy(
                items = kept,
                filtered = f.filtered + blocked.map { FilteredItem(it.article.title, it.article.sourceName, it.article.link, FilterReason.BLOCKED_SOURCE, sourceDomain = it.article.sourceDomain) },
            )
        }
        writeProfile()
    }

    suspend fun socialSignal(post: SocialPost, signal: Signal) {
        model.update { it.record(signal, "social", post.authorHandle, post.text, System.currentTimeMillis()) }
        if (signal == Signal.Opened) stats.update { it.withSocialViewed() }
        writeProfile()
    }

    // --- Filtered-drawer feedback ------------------------------------------

    /** "This isn't clickbait": loosen each rule that fired, so similar headlines pass next time. */
    suspend fun notClickbait(item: FilteredItem) {
        settings.update { s ->
            val adj = s.clickbaitAdjustments.toMutableMap()
            item.details.forEach { rule -> adj[rule] = ((adj[rule] ?: 0.0) - 0.5).coerceAtLeast(-3.0) }
            s.copy(clickbaitAdjustments = adj)
        }
        removeFiltered(item)
    }

    /** "This source is AI-generated": hide it from now on. */
    suspend fun markAiFarm(item: FilteredItem) {
        val domain = item.sourceDomain ?: return
        settings.update { it.copy(aiFarmDomains = it.aiFarmDomains + domain) }
        removeFiltered(item)
    }

    suspend fun removeFiltered(item: FilteredItem) {
        feed.update { f -> f.copy(filtered = f.filtered - item) }
    }

    // --- Settings ----------------------------------------------------------

    suspend fun setFollowed(topics: Set<String>, keywords: Set<String>) {
        model.update { it.copy(followedTopics = topics, followedKeywords = keywords.map { k -> k.trim() }.filter { k -> k.isNotEmpty() }.toSet()) }
        writeProfile()
    }

    suspend fun addSubscription(domain: String) {
        val d = Domains.of(if ("://" in domain) domain else "https://$domain") ?: return
        settings.update { it.copy(subscribedDomains = it.subscribedDomains + d) }
    }

    suspend fun removeSubscription(domain: String) = settings.update { it.copy(subscribedDomains = it.subscribedDomains - domain) }

    suspend fun addPaywallDomain(domain: String) {
        val d = Domains.of(if ("://" in domain) domain else "https://$domain") ?: return
        settings.update { it.copy(extraPaywallDomains = it.extraPaywallDomains + d, allowedPaywallDomains = it.allowedPaywallDomains - d) }
    }

    suspend fun allowPaywallDomain(domain: String) =
        settings.update { it.copy(allowedPaywallDomains = it.allowedPaywallDomains + domain, extraPaywallDomains = it.extraPaywallDomains - domain) }

    // --- Profile -------------------------------------------------------------

    suspend fun writeProfile() = withContext(Dispatchers.IO) {
        val md = ProfileWriter.render(model.value, stats.value, accountsLabel(), System.currentTimeMillis())
        profileFile.parentFile?.mkdirs()
        profileFile.writeText(md)
    }

    /** Forget everything learned: interests, stats, the profile file and the cached feed. */
    suspend fun resetProfile() {
        val followed = model.value.let { it.followedTopics to it.followedKeywords }
        model.set(InterestModel(followedTopics = followed.first, followedKeywords = followed.second))
        stats.set(UsageStats())
        profileFile.delete()
        writeProfile()
    }

    suspend fun recordMinutesToday(minutes: Int) = stats.update { it.withMinutes(LocalDate.now().toString(), minutes) }

    private suspend fun signal(item: FeedItem, signal: Signal) {
        model.update { it.record(signal, item.article.feedKey, item.article.sourceName, item.article.title, System.currentTimeMillis()) }
        writeProfile()
    }

    companion object {
        private const val STALE_MS = 2L * 60 * 60 * 1000
    }
}
