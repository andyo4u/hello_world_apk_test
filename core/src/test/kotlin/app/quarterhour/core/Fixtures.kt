package app.quarterhour.core

object Fixtures {
    fun resource(name: String): String =
        requireNotNull(javaClass.classLoader.getResource(name)) { "missing $name" }.readText()

    private val sentences = listOf(
        "The city council voted on Tuesday to expand the riverside park by twelve acres.",
        "Officials said the project would add walking trails, a playground and a small boat launch.",
        "Residents who spoke at the meeting were largely supportive, though some raised concerns about parking.",
        "Construction is expected to begin next spring and take roughly eighteen months to complete.",
        "The expansion will be funded through a mix of state grants and a local bond approved last year.",
        "Environmental groups praised the plan for restoring wetland habitat along the eastern bank.",
    )

    /** Realistic article body with [paragraphs] paragraphs (~15 words each sentence, 3 per paragraph). */
    fun body(paragraphs: Int = 8): String = (0 until paragraphs).joinToString("\n") { i ->
        "<p>" + (0 until 3).joinToString(" ") { j -> sentences[(i * 3 + j) % sentences.size] } + "</p>"
    }

    fun page(
        title: String = "City council approves riverside park expansion",
        head: String = "",
        body: String = body(),
        extra: String = "",
    ): String = """
        <html><head><title>$title</title>
        <meta property="og:title" content="$title">
        <meta property="og:site_name" content="Example Times">
        <meta property="og:image" content="https://cdn.example.com/lead.jpg">
        <meta name="author" content="Jane Reporter">
        <meta property="article:published_time" content="2026-09-29T10:00:00Z">
        $head
        </head><body>
        <nav><a href="/">Home</a><a href="/news">News</a></nav>
        <div class="ad-slot"><img src="https://ads.doubleclick.net/banner.gif"></div>
        <article>
          <h1>$title</h1>
          <p class="byline">By Jane Reporter</p>
          <figure><img src="https://cdn.example.com/park.jpg" width="1200" height="800"><figcaption>The riverside park today.</figcaption></figure>
          $body
          <div class="newsletter-signup"><p>Sign up for our newsletter to get the latest news.</p></div>
          <p>Advertisement</p>
          <figure><img data-src="https://cdn.example.com/map.png" src="data:image/gif;base64,R0lGOD"><figcaption>Map of the expansion.</figcaption></figure>
        </article>
        <aside class="related-stories"><a href="/x">Recommended for you</a></aside>
        <div id="taboola-below-article">Sponsored links</div>
        $extra
        </body></html>
    """.trimIndent()
}
