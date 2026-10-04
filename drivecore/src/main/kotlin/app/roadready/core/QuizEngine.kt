package app.roadready.core

import kotlin.random.Random

/**
 * Picks questions and updates per-kid mastery with Leitner boxes: a right
 * answer moves a question up a box (max [MAX_BOX]), a wrong one sends it back to 0.
 * Practice favors low boxes so weak questions come back sooner.
 */
object QuizEngine {
    const val MAX_BOX = 4
    const val PRACTICE_SIZE = 10

    fun practice(
        stats: Map<String, QuestionStat>,
        random: Random = Random.Default,
        size: Int = PRACTICE_SIZE,
        bank: List<Question> = QuestionBank.all,
    ): List<Question> {
        // Weight 2^(MAX_BOX - box): a box-0 question is 16x as likely as a mastered one.
        val pool = bank.map { it to (1 shl (MAX_BOX - (stats[it.id]?.box ?: 0))).toDouble() }.toMutableList()
        val picked = mutableListOf<Question>()
        while (picked.size < size && pool.isNotEmpty()) {
            val total = pool.sumOf { it.second }
            var r = random.nextDouble() * total
            val index = pool.indexOfFirst { r -= it.second; r < 0 }.let { if (it < 0) pool.lastIndex else it }
            picked += pool.removeAt(index).first
        }
        return picked
    }

    fun topic(topicId: String, random: Random = Random.Default): List<Question> =
        QuestionBank.byTopic(topicId).shuffled(random)

    /** A DMV-style test: [OregonGdl.KNOWLEDGE_TEST_QUESTIONS] questions spread across every topic. */
    fun mockExam(random: Random = Random.Default, bank: List<Question> = QuestionBank.all): List<Question> {
        val size = minOf(OregonGdl.KNOWLEDGE_TEST_QUESTIONS, bank.size)
        val byTopic = bank.groupBy { it.topicId }.mapValues { it.value.shuffled(random).toMutableList() }
        val picked = mutableListOf<Question>()
        // Round-robin over topics so no topic is skipped, then shuffle the order.
        while (picked.size < size) {
            for (queue in byTopic.values) {
                if (picked.size == size) break
                queue.removeFirstOrNull()?.let { picked += it }
            }
        }
        return picked.shuffled(random)
    }

    fun passed(mode: QuizMode, correct: Int, total: Int): Boolean = when (mode) {
        QuizMode.MOCK_EXAM -> total == OregonGdl.KNOWLEDGE_TEST_QUESTIONS && correct >= OregonGdl.KNOWLEDGE_TEST_PASS
        else -> total > 0 && correct * 5 >= total * 4
    }

    fun record(stat: QuestionStat?, correct: Boolean): QuestionStat {
        val s = stat ?: QuestionStat()
        return QuestionStat(
            box = if (correct) minOf(s.box + 1, MAX_BOX) else 0,
            seen = s.seen + 1,
            correct = s.correct + if (correct) 1 else 0,
        )
    }

    /** 0–100: how much of the bank this kid has moved up to box 3 or higher. */
    fun readiness(stats: Map<String, QuestionStat>, bank: List<Question> = QuestionBank.all): Int {
        if (bank.isEmpty()) return 0
        val score = bank.sumOf { minOf(stats[it.id]?.box ?: 0, 3) }
        return score * 100 / (bank.size * 3)
    }

    /** Topics with the lowest share of correct answers, worst first (only topics the kid has tried). */
    fun weakTopics(stats: Map<String, QuestionStat>, limit: Int = 3): List<Topic> =
        RulesOfTheRoad.topics.mapNotNull { topic ->
            val seen = QuestionBank.byTopic(topic.id).mapNotNull { stats[it.id] }.filter { it.seen > 0 }
            if (seen.isEmpty()) null else topic to seen.sumOf { it.correct }.toDouble() / seen.sumOf { it.seen }
        }.filter { it.second < 0.8 }.sortedBy { it.second }.take(limit).map { it.first }
}
