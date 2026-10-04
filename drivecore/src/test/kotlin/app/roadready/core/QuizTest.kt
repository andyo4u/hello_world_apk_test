package app.roadready.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizTest {
    @Test fun bankIsWellFormed() {
        val ids = QuestionBank.all.map { it.id }
        assertEquals("duplicate ids", ids.size, ids.toSet().size)
        val topicIds = RulesOfTheRoad.topics.map { it.id }.toSet()
        for (q in QuestionBank.all) {
            assertTrue("${q.id} has unknown topic", q.topicId in topicIds)
            assertEquals("${q.id} has duplicate choices", q.choices.size, q.choices.toSet().size)
        }
        for (t in RulesOfTheRoad.topics) assertTrue("${t.id} has no questions", QuestionBank.byTopic(t.id).isNotEmpty())
        assertTrue(QuestionBank.all.size >= OregonGdl.KNOWLEDGE_TEST_QUESTIONS)
    }

    @Test fun answersAreSpreadAcrossPositions() {
        val positions = QuestionBank.all.groupingBy { it.answer }.eachCount()
        assertTrue("answers bunched: $positions", positions.size >= 3)
    }

    @Test fun mockExamCoversEveryTopic() {
        val exam = QuizEngine.mockExam(Random(1))
        assertEquals(35, exam.size)
        assertEquals(35, exam.map { it.id }.toSet().size)
        assertEquals(RulesOfTheRoad.topics.map { it.id }.toSet(), exam.map { it.topicId }.toSet())
    }

    @Test fun passMark() {
        assertTrue(QuizEngine.passed(QuizMode.MOCK_EXAM, 28, 35))
        assertFalse(QuizEngine.passed(QuizMode.MOCK_EXAM, 27, 35))
        assertTrue(QuizEngine.passed(QuizMode.PRACTICE, 8, 10))
        assertFalse(QuizEngine.passed(QuizMode.PRACTICE, 7, 10))
    }

    @Test fun leitnerBoxes() {
        var s = QuizEngine.record(null, true)
        assertEquals(1, s.box)
        repeat(10) { s = QuizEngine.record(s, true) }
        assertEquals(QuizEngine.MAX_BOX, s.box)
        s = QuizEngine.record(s, false)
        assertEquals(0, s.box)
        assertEquals(12, s.seen)
        assertEquals(11, s.correct)
    }

    @Test fun practiceFavorsWeakQuestions() {
        // Everything mastered except the gdl topic.
        val stats = QuestionBank.all.associate { it.id to QuestionStat(box = if (it.topicId == "gdl") 0 else 4) }
        val gdlShare = QuestionBank.byTopic("gdl").size.toDouble() / QuestionBank.all.size
        var gdl = 0
        repeat(50) { seed -> gdl += QuizEngine.practice(stats, Random(seed)).count { it.topicId == "gdl" } }
        assertTrue("gdl picks $gdl of 500", gdl / 500.0 > gdlShare * 2)
        assertEquals(10, QuizEngine.practice(stats, Random(3)).map { it.id }.toSet().size)
    }

    @Test fun readinessAndWeakTopics() {
        assertEquals(0, QuizEngine.readiness(emptyMap()))
        assertEquals(100, QuizEngine.readiness(QuestionBank.all.associate { it.id to QuestionStat(box = 4) }))
        val stats = mapOf(
            "spd1" to QuestionStat(seen = 4, correct = 1),
            "gdl1" to QuestionStat(seen = 2, correct = 2),
        )
        assertEquals(listOf("speed"), QuizEngine.weakTopics(stats).map { it.id })
    }

    @Test fun skillsProgress() {
        assertEquals(0, Skills.progress(emptyMap()))
        assertEquals(100, Skills.progress(Skills.all.associate { it.id to SkillLevel.CONFIDENT }))
        assertEquals(Skills.all.size, Skills.all.map { it.id }.toSet().size)
        assertEquals(SkillLevel.NOT_STARTED, SkillLevel.CONFIDENT.next())
    }
}
