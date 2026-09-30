package app.quarterhour.core.news

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkResolverFlowTest {
    @Test fun nonGoogleLinksPassThrough() = kotlinx.coroutines.runBlocking {
        val resolver = GoogleNewsLinkResolver(app.quarterhour.core.net.Http())
        assertEquals("https://apnews.com/article/x", resolver.resolve("https://apnews.com/article/x"))
    }
}
