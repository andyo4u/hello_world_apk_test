package app.quarterhour.core.reader

import app.quarterhour.core.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {
    private val result = ArticleExtractor.extract("https://example.com/news/park", Fixtures.page())
    private val article = result.article

    @Test fun keepsMetadata() {
        assertEquals("City council approves riverside park expansion", article.title)
        assertEquals("Example Times", article.siteName)
        assertTrue(article.publishedAtMillis!! > 0)
    }

    @Test fun keepsBodyText() {
        assertTrue(article.wordCount > 150)
        assertTrue(article.text.contains("riverside park by twelve acres"))
    }

    @Test fun keepsPhotosWithCaptionsAndFixesLazyImages() {
        val images = article.blocks.filterIsInstance<Block.Image>()
        assertTrue(images.any { it.url == "https://cdn.example.com/park.jpg" && it.caption == "The riverside park today." })
        assertTrue(images.any { it.url == "https://cdn.example.com/map.png" })
    }

    @Test fun dropsAdsAndPromos() {
        val all = article.text.lowercase()
        assertFalse(all.contains("sign up for our newsletter"))
        assertFalse(all.contains("sponsored links"))
        assertFalse(all.contains("recommended for you"))
        assertFalse(article.blocks.any { it is Block.Paragraph && it.text == "Advertisement" })
        assertFalse(article.imageUrls.any { "doubleclick" in it })
    }

    @Test fun leadImageAddedWhenBodyHasNone() {
        val page = Fixtures.page().replace(Regex("<figure>.*?</figure>", RegexOption.DOT_MATCHES_ALL), "")
        val a = ArticleExtractor.extract("https://example.com/x", page).article
        assertEquals("https://cdn.example.com/lead.jpg", (a.blocks.first() as Block.Image).url)
    }

    @Test fun promoDetection() {
        assertTrue(ArticleExtractor.isPromo("Read more: Other story"))
        assertTrue(ArticleExtractor.isPromo("ADVERTISEMENT"))
        assertFalse(ArticleExtractor.isPromo("The mayor said more work was needed."))
    }

    @Test fun srcsetPicksSensibleSize() {
        assertEquals("b.jpg", ArticleExtractor.bestFromSrcset("a.jpg 320w, b.jpg 1280w, c.jpg 4000w"))
        assertEquals("b.jpg", ArticleExtractor.bestFromSrcset("a.jpg 1x, b.jpg 2x"))
    }
}
