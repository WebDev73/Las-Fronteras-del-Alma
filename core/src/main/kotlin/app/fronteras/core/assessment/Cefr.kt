package app.fronteras.core.assessment

import kotlinx.serialization.Serializable

/**
 * CEFR reference levels. Results are *framework-aligned estimates*, never certification.
 * The continuous scale centres each level on an integer (A1 = 1.0 ... C2 = 6.0).
 */
@Serializable
enum class CefrLevel(val scaleCentre: Double, val summary: String) {
    A1(1.0, "Can understand and use familiar everyday expressions and very basic phrases."),
    A2(2.0, "Can communicate in simple, routine tasks on familiar topics."),
    B1(3.0, "Can deal with most situations likely to arise while travelling; can describe experiences and give brief reasons."),
    B2(4.0, "Can interact with a degree of fluency and spontaneity; can explain a viewpoint on a topical issue."),
    C1(5.0, "Can express ideas fluently and spontaneously without much obvious searching for expressions."),
    C2(6.0, "Can express themselves spontaneously, very fluently and precisely in complex situations.");

    companion object {
        const val SCALE_MIN = 0.0
        const val SCALE_MAX = 7.0

        fun fromScale(value: Double): CefrLevel = when {
            value < 1.5 -> A1
            value < 2.5 -> A2
            value < 3.5 -> B1
            value < 4.5 -> B2
            value < 5.5 -> C1
            else -> C2
        }
    }
}

/**
 * Abilities assessed and tracked separately. A single multiple-choice score never
 * stands in for all of them.
 */
@Serializable
enum class Skill(val label: String) {
    LISTENING("Listening comprehension"),
    READING("Reading comprehension"),
    VOCABULARY("Vocabulary range and precision"),
    GRAMMAR("Grammatical accuracy"),
    SPEAKING("Spontaneous speaking / comprehensibility"),
    PRONUNCIATION("Pronunciation intelligibility"),
    FLUENCY("Fluency and continuity"),
    INTERACTION("Interaction, relevance and conversational repair"),
}
