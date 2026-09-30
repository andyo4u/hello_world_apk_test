package app.quarterhour.core.social

import app.quarterhour.core.net.Http
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClientFlowTest {
    private val server = MockWebServer()
    private val base get() = server.url("/").toString().trimEnd('/')
    private val serverTimes = mutableListOf<Long>()
    private val http = Http(serverTime = { serverTimes += it })

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun route(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = handler(request)
        }
    }

    @Test fun blueskyRefreshesExpiredSessionAndRetries() = runBlocking {
        route { req ->
            val auth = req.getHeader("Authorization")
            when {
                req.path!!.startsWith("/xrpc/com.atproto.server.refreshSession") && auth == "Bearer R1" ->
                    MockResponse().setBody("""{"accessJwt":"A2","refreshJwt":"R2","handle":"me.test","did":"did:plc:me"}""")
                req.path!!.startsWith("/xrpc/app.bsky.feed.getTimeline") && auth == "Bearer A1" ->
                    MockResponse().setResponseCode(400).setBody("""{"error":"ExpiredToken"}""")
                req.path!!.startsWith("/xrpc/app.bsky.feed.getTimeline") && auth == "Bearer A2" ->
                    MockResponse().setBody("""{"feed":[]}""").addHeader("Date", "Wed, 30 Sep 2026 12:00:00 GMT")
                else -> MockResponse().setResponseCode(401)
            }
        }
        val account = SocialAccount.Bluesky(base, "me.test", "did:plc:me", "A1", "R1")
        val (posts, refreshed) = BlueskyClient(http).timeline(account)
        assertTrue(posts.isEmpty())
        assertEquals("A2", refreshed.accessJwt)
        assertEquals("R2", refreshed.refreshJwt)
        assertTrue("server Date reaches the budget clock", serverTimes.isNotEmpty())
    }

    @Test fun blueskyWrongPasswordIsReadable() = runBlocking {
        route { MockResponse().setResponseCode(401).setBody("""{"error":"AuthenticationRequired"}""") }
        val e = runCatching { BlueskyClient(http).signIn("me.test", "bad", base) }.exceptionOrNull()
        assertTrue(e is SocialAuthException)
    }

    @Test fun mastodonOAuthFlow() = runBlocking {
        route { req ->
            when (req.path!!.substringBefore('?')) {
                "/oauth/token" -> {
                    val body = req.body.readUtf8()
                    if ("code=THECODE" in body && "client_secret=SEC" in body) MockResponse().setBody("""{"access_token":"TOK"}""")
                    else MockResponse().setResponseCode(400)
                }
                "/api/v1/accounts/verify_credentials" -> MockResponse().setBody("""{"acct":"me"}""")
                "/api/v1/timelines/home" ->
                    if (req.getHeader("Authorization") == "Bearer TOK") MockResponse().setBody("[]") else MockResponse().setResponseCode(401)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val client = MastodonClient(http)
        // MockWebServer is plain http and registerApp forces https, so build the registration directly.
        val reg = MastodonClient.AppRegistration(base, "CID", "SEC")
        assertTrue(client.authorizeUrl(reg).contains("redirect_uri=quarterhour%3A%2F%2Foauth%2Fmastodon"))
        val account = client.exchangeCode(reg, "THECODE")
        assertEquals("TOK", account.accessToken)
        assertEquals("me", account.acct)
        assertTrue(client.homeTimeline(account).isEmpty())
    }
}
