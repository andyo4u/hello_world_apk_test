package app.quarterhour.core.paywall

import app.quarterhour.core.news.Domains
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.nodes.Document

enum class PaywallSignal {
    /** Publisher domain is on the known-paywall list. */
    KNOWN_DOMAIN,
    /** schema.org `isAccessibleForFree: false` in JSON-LD or microdata. */
    SCHEMA_NOT_FREE,
    /** Page loads a paywall vendor script or uses paywall markup. */
    PAYWALL_MARKUP,
    /** Extracted text is too short or ends in a subscribe prompt. */
    TRUNCATED,
}

data class PaywallVerdict(val signals: Set<PaywallSignal>) {
    val isPaywalled: Boolean get() = signals.isNotEmpty()
}

/**
 * Method 1 of 3 for keeping paywalls out of the feed: detect them and hide the
 * article. Nothing here tries to get around a paywall.
 *
 * @param knownPaywallDomains publisher domains that are always treated as paywalled
 * @param subscribedDomains domains the user pays for and has signed in to (method 3);
 *   these skip the domain check, but a page that is still paywalled is hidden
 */
class PaywallDetector(
    private val knownPaywallDomains: Set<String> = DEFAULT_PAYWALL_DOMAINS,
    private val subscribedDomains: Set<String> = emptySet(),
) {
    fun isSubscribed(host: String): Boolean = subscribedDomains.any { Domains.matches(host, it) }

    /** Cheap pre-check on the publisher domain, before anything is downloaded. */
    fun checkDomain(host: String?): PaywallVerdict {
        if (host == null || isSubscribed(host)) return PaywallVerdict(emptySet())
        val known = knownPaywallDomains.any { Domains.matches(host, it) }
        return PaywallVerdict(if (known) setOf(PaywallSignal.KNOWN_DOMAIN) else emptySet())
    }

    /**
     * Full check on a downloaded page.
     * @param extractedText the article body after reader extraction
     */
    fun checkPage(host: String?, doc: Document, extractedText: String): PaywallVerdict {
        val signals = checkDomain(host).signals.toMutableSet()
        if (schemaSaysNotFree(doc)) signals += PaywallSignal.SCHEMA_NOT_FREE
        if (hasPaywallMarkup(doc)) signals += PaywallSignal.PAYWALL_MARKUP
        if (looksTruncated(extractedText)) signals += PaywallSignal.TRUNCATED
        return PaywallVerdict(signals)
    }

    internal fun schemaSaysNotFree(doc: Document): Boolean {
        val microdata = doc.select("[itemprop=isAccessibleForFree]").any {
            val v = it.attr("content").ifEmpty { it.text() }
            v.trim().equals("false", ignoreCase = true)
        }
        if (microdata) return true
        return doc.select("script[type=application/ld+json]").any { script ->
            val element = runCatching { json.parseToJsonElement(script.data()) }.getOrNull()
            element != null && containsNotFree(element)
        }
    }

    private fun containsNotFree(element: JsonElement): Boolean = when (element) {
        is JsonObject -> element.entries.any { (key, value) ->
            (key == "isAccessibleForFree" && value is JsonPrimitive &&
                value.content.trim().equals("false", ignoreCase = true)) || containsNotFree(value)
        }
        is JsonArray -> element.any(::containsNotFree)
        else -> false
    }

    internal fun hasPaywallMarkup(doc: Document): Boolean {
        val scripts = doc.select("script[src]").map { it.attr("src").lowercase() }
        if (scripts.any { src -> PAYWALL_SCRIPT_HINTS.any { it in src } }) return true
        val inline = doc.select("script:not([src])").joinToString(" ") { it.data() }.lowercase()
        if (INLINE_PAYWALL_HINTS.any { it in inline }) return true
        return doc.select(PAYWALL_SELECTORS).isNotEmpty()
    }

    internal fun looksTruncated(text: String): Boolean {
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        if (words < MIN_ARTICLE_WORDS) return true
        val tail = text.takeLast(400).lowercase()
        return SUBSCRIBE_PROMPTS.any { it in tail }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        const val MIN_ARTICLE_WORDS = 150

        /** Users can add or remove entries in settings; this is the starting list. */
        val DEFAULT_PAYWALL_DOMAINS: Set<String> = setOf(
            "wsj.com", "ft.com", "nytimes.com", "washingtonpost.com", "bloomberg.com",
            "economist.com", "barrons.com", "theathletic.com", "newyorker.com", "theatlantic.com",
            "wired.com", "businessinsider.com", "hbr.org", "latimes.com", "bostonglobe.com",
            "telegraph.co.uk", "thetimes.co.uk", "thetimes.com", "newsweek.com", "foreignpolicy.com",
            "foreignaffairs.com", "seattletimes.com", "chicagotribune.com", "sfchronicle.com",
            "theinformation.com", "nature.com", "scientificamerican.com",
            "haaretz.com", "lemonde.fr", "spiegel.de", "nzz.ch", "smh.com.au", "theaustralian.com.au",
            "afr.com", "globeandmail.com", "nationalpost.com", "japantimes.co.jp", "scmp.com",
            "medium.com",
        )

        internal val PAYWALL_SCRIPT_HINTS = listOf(
            "tinypass.com", "piano.io", "cxense.com", "poool.fr", "pelcro.com", "zephr",
            "laterpay", "memberful", "paywall", "leaky-paywall", "tp.media", "evolok",
        )

        internal val INLINE_PAYWALL_HINTS = listOf(
            "tp.push", "tinypass", "\"paywall\":true", "haspaywall", "meteredcontent", "piano.init",
        )

        internal const val PAYWALL_SELECTORS =
            "[class*=paywall], [id*=paywall], [data-paywall], " +
                "[class*=subscriber-only], [class*=premium-content], [class*=meteredContent], " +
                "[class*=regwall], [class*=piano-offer], .tp-modal, #piano-inline"

        internal val SUBSCRIBE_PROMPTS = listOf(
            "subscribe to continue", "subscribe to read", "to continue reading", "already a subscriber",
            "subscribers only", "for subscribers", "log in to continue", "sign in to continue",
            "this article is for subscribers", "start your free trial", "unlock this article",
            "become a member to read",
        )
    }
}
