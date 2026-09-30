package app.quarterhour.core.social

import app.quarterhour.core.profile.InterestModel
import app.quarterhour.core.profile.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueskyParseTest {
    private val body = """
    {"feed":[
      {"post":{"uri":"at://did:plc:abc/app.bsky.feed.post/3k1","cid":"x",
        "author":{"did":"did:plc:abc","handle":"alice.bsky.social","displayName":"Alice","avatar":"https://cdn/av.jpg"},
        "record":{"${'$'}type":"app.bsky.feed.post","text":"Sunset over the bay","createdAt":"2026-09-29T18:00:00.000Z"},
        "embed":{"${'$'}type":"app.bsky.embed.images#view","images":[{"thumb":"https://cdn/t.jpg","fullsize":"https://cdn/f.jpg","alt":"Orange sky","aspectRatio":{"width":4,"height":3}}]},
        "likeCount":42,"labels":[]}},
      {"post":{"uri":"at://did:plc:def/app.bsky.feed.post/3k2","author":{"handle":"bob.test"},
        "record":{"text":"Skate clip","createdAt":"2026-09-29T17:00:00Z"},
        "embed":{"${'$'}type":"app.bsky.embed.recordWithMedia#view","media":{"${'$'}type":"app.bsky.embed.video#view","playlist":"https://video/p.m3u8","thumbnail":"https://video/t.jpg"}},
        "likeCount":3,"labels":[{"val":"graphic-media"}]}},
      {"post":{"uri":"at://did:plc:ghi/app.bsky.feed.post/3k3","author":{"handle":"carol.test"},"record":{"text":"reply"}},"reply":{"root":{}}}
    ],"cursor":"c"}
    """.trimIndent()

    @Test fun parsesImagesVideosAndSkipsReplies() {
        val posts = BlueskyClient.parseTimeline(body)
        assertEquals(2, posts.size)
        val a = posts[0]
        assertEquals("Alice", a.authorName)
        assertEquals("@alice.bsky.social", a.authorHandle)
        assertEquals("https://bsky.app/profile/alice.bsky.social/post/3k1", a.url)
        assertEquals(MediaType.IMAGE, a.media.single().type)
        assertEquals(4f / 3f, a.media.single().aspectRatio!!, 1e-6f)
        assertEquals(42, a.likeCount)
        val b = posts[1]
        assertEquals(MediaType.VIDEO, b.media.single().type)
        assertEquals("https://video/p.m3u8", b.media.single().url)
        assertEquals(listOf("graphic-media"), b.labels)
    }
}

class MastodonParseTest {
    private val body = """
    [
      {"id":"1","uri":"https://m.example/users/dan/statuses/1","url":"https://m.example/@dan/1","created_at":"2026-09-29T12:00:00.000Z",
       "content":"<p>Morning <b>hike</b></p>","sensitive":false,"spoiler_text":"","in_reply_to_id":null,"favourites_count":7,
       "account":{"acct":"dan","display_name":"Dan","avatar":"https://m.example/a.png"},
       "media_attachments":[{"type":"image","url":"https://m.example/i.jpg","preview_url":"https://m.example/p.jpg","description":"Trail","meta":{"original":{"width":1000,"height":500}}}],
       "reblog":null},
      {"id":"2","created_at":"2026-09-29T11:00:00Z","content":"boost","account":{"acct":"eve@other"},"media_attachments":[],
       "reblog":{"id":"99","uri":"https://other/s/99","url":"https://other/@x/99","created_at":"2026-09-29T10:00:00Z","content":"<p>Cat video</p>",
         "account":{"acct":"x@other","display_name":""," username":"x"},"in_reply_to_id":null,"favourites_count":100,"sensitive":true,"spoiler_text":"",
         "media_attachments":[{"type":"gifv","url":"https://other/v.mp4","preview_url":null,"description":null}]}}
    ]
    """.trimIndent()

    @Test fun parsesStatusesAndBoosts() {
        val posts = MastodonClient.parseTimeline(body)
        assertEquals(2, posts.size)
        assertEquals("Morning hike", posts[0].text)
        assertEquals(2f, posts[0].media.single().aspectRatio!!, 1e-6f)
        assertEquals("masto:https://other/s/99", posts[1].id)
        assertEquals(MediaType.VIDEO, posts[1].media.single().type)
        assertEquals(listOf("sensitive"), posts[1].labels)
    }

    @Test fun normalizesInstance() {
        assertEquals("https://mastodon.social", MastodonClient.normalizeInstance("mastodon.social/"))
        assertEquals("https://fosstodon.org", MastodonClient.normalizeInstance("@me@fosstodon.org"))
        assertEquals("https://x.example", MastodonClient.normalizeInstance("http://x.example"))
    }
}

class SocialPickerTest {
    private val now = 1_790_000_000_000L

    private fun post(i: Int, author: String = "@a$i", text: String = "Photo $i", media: Boolean = true, likes: Int = 0, labels: List<String> = emptyList()) =
        SocialPost(
            "p$i", Network.BLUESKY, author, author, null, text, now - i * 60_000L,
            if (media) listOf(Media(MediaType.IMAGE, "https://i/$i.jpg", null, null)) else emptyList(),
            likes, "https://x/$i", labels,
        )

    @Test fun picksFifteenWithMediaOnly() {
        val posts = (1..40).map { post(it, media = it % 2 == 0) }
        val result = SocialPicker(InterestModel()).pick(posts, emptySet(), now)
        assertEquals(15, result.picks.size)
        assertTrue(result.picks.all { it.media.isNotEmpty() })
        assertEquals(20, result.rejected[SocialPicker.Rejection.NO_MEDIA])
    }

    @Test fun filtersAdsSensitiveBaitAndRepeats() {
        val posts = listOf(
            post(1, text = "New shoes! #ad"),
            post(2, text = "Use code SAVE20 for 20% off"),
            post(3, labels = listOf("nudity")),
            post(4, text = "You won't believe what happened next!!"),
            post(5),
            post(6),
        )
        val result = SocialPicker(InterestModel()).pick(posts, setOf("p6"), now)
        assertEquals(listOf("p5"), result.picks.map { it.id })
        assertEquals(2, result.rejected[SocialPicker.Rejection.SPONSORED])
    }

    @Test fun limitsPostsPerAuthorAndFollowsInterests() {
        val m = InterestModel().record(Signal.ThumbUp, "social", "@fav", "mountain hiking trail", now)
        val posts = (1..6).map { post(it, author = "@fav", text = "mountain hiking $it") } + (7..30).map { post(it) }
        val picks = SocialPicker(m).pick(posts, emptySet(), now).picks
        assertEquals(SocialPicker.MAX_PER_AUTHOR, picks.count { it.authorHandle == "@fav" })
        assertEquals("@fav", picks.first().authorHandle)
    }

    @Test fun sponsoredPatterns() {
        assertTrue(SocialPicker.isSponsored("Loving this #sponsored"))
        assertTrue(SocialPicker.isSponsored("paid partnership with brand"))
        assertTrue(!SocialPicker.isSponsored("Adding a photo from #adirondacks"))
    }
}
