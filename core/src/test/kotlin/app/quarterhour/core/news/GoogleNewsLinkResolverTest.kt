package app.quarterhour.core.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GoogleNewsLinkResolverTest {

    private fun oldStyleId(url: String): String {
        val u = url.toByteArray()
        val bytes = byteArrayOf(0x08, 0x13, 0x22, u.size.toByte()) + u + byteArrayOf(0xD2.toByte(), 0x01, 0x00)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    @Test fun extractsArticleId() {
        assertEquals("CBMiXYZ", GoogleNewsLinkResolver.articleId("https://news.google.com/rss/articles/CBMiXYZ?oc=5"))
        assertEquals("CBMiXYZ", GoogleNewsLinkResolver.articleId("https://news.google.com/articles/CBMiXYZ"))
        assertNull(GoogleNewsLinkResolver.articleId("https://example.com/articles/abc"))
    }

    @Test fun decodesOldStyleIdsOffline() {
        val id = oldStyleId("https://apnews.com/article/park-expansion-123")
        assertEquals("https://apnews.com/article/park-expansion-123", GoogleNewsLinkResolver.decodeOffline(id))
    }

    @Test fun newStyleIdsNeedNetwork() {
        val payload = "AU_yqLabcdefghijklmnop".toByteArray()
        val bytes = byteArrayOf(0x08, 0x13, 0x22, payload.size.toByte()) + payload
        assertNull(GoogleNewsLinkResolver.decodeOffline(Base64.getUrlEncoder().encodeToString(bytes)))
        assertNull(GoogleNewsLinkResolver.decodeOffline("not base64!!"))
    }

    @Test fun readsSignatureFromInterstitial() {
        val html = """<html><body><c-wiz><div jscontroller="aLI87" data-n-a-sg="SIG123" data-n-a-ts="1727600000"></div></c-wiz></body></html>"""
        val sig = GoogleNewsLinkResolver.parseSignature(html)!!
        assertEquals("SIG123", sig.signature)
        assertEquals("1727600000", sig.timestamp)
    }

    @Test fun batchPayloadIsValidNestedJson() {
        val p = GoogleNewsLinkResolver.batchPayload("ID1", "123", "SIG")
        val outer = kotlinx.serialization.json.Json.parseToJsonElement(p)
        val inner = outer.toString()
        assertTrue(inner.contains("garturlreq"))
        assertTrue(inner.contains("ID1"))
    }

    @Test fun parsesBatchResponse() {
        val body = ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\",\\\"https://www.reuters.com/world/story-1/\\\",1]\",null,null,null,\"generic\"],[\"di\",10],[\"af.httprm\",10,\"-1\",25]]"
        assertEquals("https://www.reuters.com/world/story-1/", GoogleNewsLinkResolver.parseBatchResponse(body))
        assertNull(GoogleNewsLinkResolver.parseBatchResponse("garbage"))
    }
}
