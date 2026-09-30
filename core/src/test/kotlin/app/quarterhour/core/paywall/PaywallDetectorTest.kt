package app.quarterhour.core.paywall

import app.quarterhour.core.Fixtures
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaywallDetectorTest {
    private val detector = PaywallDetector()
    private val fullText = Fixtures.body(8).replace(Regex("<[^>]+>"), " ")

    @Test fun knownDomainsIncludingSubdomains() {
        assertTrue(detector.checkDomain("wsj.com").isPaywalled)
        assertTrue(detector.checkDomain("www.nytimes.com").isPaywalled)
        assertTrue(detector.checkDomain("cooking.nytimes.com").isPaywalled)
        assertFalse(detector.checkDomain("apnews.com").isPaywalled)
        assertFalse(detector.checkDomain("notnytimes.com").isPaywalled)
    }

    @Test fun subscribedDomainsPassDomainCheck() {
        val d = PaywallDetector(subscribedDomains = setOf("nytimes.com"))
        assertFalse(d.checkDomain("www.nytimes.com").isPaywalled)
        assertTrue(d.checkDomain("wsj.com").isPaywalled)
    }

    @Test fun freeArticleIsNotPaywalled() {
        val doc = Jsoup.parse(Fixtures.page())
        assertEquals(emptySet<PaywallSignal>(), detector.checkPage("example.com", doc, fullText).signals)
    }

    @Test fun jsonLdNotFree() {
        val head = """<script type="application/ld+json">{"@context":"https://schema.org","@type":"NewsArticle",
            "hasPart":{"@type":"WebPageElement","isAccessibleForFree":"False","cssSelector":".paid"}}</script>"""
        val doc = Jsoup.parse(Fixtures.page(head = head))
        assertTrue(PaywallSignal.SCHEMA_NOT_FREE in detector.checkPage("example.com", doc, fullText).signals)
    }

    @Test fun jsonLdBooleanInGraph() {
        val head = """<script type="application/ld+json">{"@graph":[{"@type":"NewsArticle","isAccessibleForFree":false}]}</script>"""
        assertTrue(detector.schemaSaysNotFree(Jsoup.parse(Fixtures.page(head = head))))
    }

    @Test fun jsonLdFreeIsFine() {
        val head = """<script type="application/ld+json">{"@type":"NewsArticle","isAccessibleForFree":true}</script>"""
        assertFalse(detector.schemaSaysNotFree(Jsoup.parse(Fixtures.page(head = head))))
    }

    @Test fun paywallVendorScript() {
        val doc = Jsoup.parse(Fixtures.page(head = """<script src="https://cdn.tinypass.com/api/tinypass.min.js"></script>"""))
        assertTrue(PaywallSignal.PAYWALL_MARKUP in detector.checkPage("example.com", doc, fullText).signals)
    }

    @Test fun paywallMarkup() {
        val doc = Jsoup.parse(Fixtures.page(extra = """<div class="article-paywall-overlay">Subscribe</div>"""))
        assertTrue(detector.hasPaywallMarkup(doc))
    }

    @Test fun truncatedText() {
        assertTrue(detector.looksTruncated("Only a teaser paragraph here."))
        assertTrue(detector.looksTruncated(fullText + " Already a subscriber? Log in to continue reading."))
        assertFalse(detector.looksTruncated(fullText))
    }
}
