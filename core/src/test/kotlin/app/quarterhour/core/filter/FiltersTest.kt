package app.quarterhour.core.filter

import app.quarterhour.core.Fixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickbaitDetectorTest {
    private val d = ClickbaitDetector()

    @Test fun flagsClickbait() {
        listOf(
            "You Won't Believe What This Celebrity Did Next",
            "10 Things Doctors Don't Want You To Know About Coffee",
            "This SHOCKING Video Is Going Viral!!",
            "Fans are furious after star's stunning announcement",
            "Here's why everyone is talking about this insane new gadget",
            "She opened the box and what happened next...",
        ).forEach { assertTrue(it, d.isClickbait(it)) }
    }

    @Test fun passesNormalHeadlines() {
        listOf(
            "Central bank holds rates steady as inflation cools",
            "Senate passes infrastructure bill 68-32",
            "What to know about the new NASA Mars mission",
            "Here's what to know about Tuesday's storm",
            "Why the housing market is slowing",
            "NATO leaders meet in Brussels to discuss Ukraine aid",
            "Is remote work here to stay? Companies weigh options",
        ).forEach { assertFalse(it, d.isClickbait(it)) }
    }

    @Test fun headlineBodyMismatch() {
        val body = Fixtures.body(8)
        assertTrue(ClickbaitDetector.headlineBodyMismatch("Celebrity divorce scandal rocks Hollywood insiders", body))
        assertFalse(ClickbaitDetector.headlineBodyMismatch("Council approves riverside park expansion plan", body))
    }

    @Test fun userAdjustmentsCanRelaxARule() {
        val relaxed = ClickbaitDetector(adjustments = mapOf("withholding-phrase" to -2.0))
        assertFalse(relaxed.isClickbait("You won't believe the new city budget"))
    }
}

class AiContentDetectorTest {
    private val d = AiContentDetector(knownFarms = setOf("farm.example"))
    private val human = Fixtures.body(8).replace(Regex("<[^>]+>"), " ")

    @Test fun humanArticlePasses() {
        assertFalse(d.isAi("example.com", "Jane Reporter", human))
    }

    @Test fun leftoverPhraseIsObvious() {
        assertTrue(d.isAi("example.com", "Jane Reporter", "$human As an AI language model, I cannot browse the internet."))
    }

    @Test fun knownFarm() {
        assertTrue(d.isAi("www.farm.example", "Someone", human))
    }

    @Test fun clichesAndNoAuthorAndRepetition() {
        val text = List(12) {
            "In today's fast-paced world, it's important to note that we delve into the realm of technology. " +
                "In conclusion, this is a testament to innovation that plays a pivotal role."
        }.joinToString(" ")
        assertTrue(d.isAi("blog.example", null, text))
    }

    @Test fun oneClicheIsNotEnough() {
        assertFalse(d.isAi("example.com", null, "$human In conclusion, the park opens next year."))
    }
}

class ImageProvenanceTest {
    @Test fun detectsIptcAiSourceType() {
        val xmp = "<x:xmpmeta><rdf:Description Iptc4xmpExt:DigitalSourceType=\"http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia\"/></x:xmpmeta>"
        assertTrue(ImageProvenance.isAiGenerated(byteArrayOf(-1, -40) + xmp.toByteArray()))
    }

    @Test fun detectsC2paGenerator() {
        assertTrue(ImageProvenance.isAiGenerated("jumb c2pa manifest claim_generator OpenAI DALL-E".toByteArray()))
    }

    @Test fun ordinaryPhotoPasses() {
        val xmp = "<x:xmpmeta>digitalsourcetype/digitalCapture Canon EOS R5</x:xmpmeta>"
        assertFalse(ImageProvenance.isAiGenerated(xmp.toByteArray()))
    }
}
