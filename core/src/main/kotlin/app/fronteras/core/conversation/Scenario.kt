package app.fronteras.core.conversation

import app.fronteras.core.assessment.CefrLevel
import kotlinx.serialization.Serializable

@Serializable
data class Scenario(
    val id: String,
    val title: String,
    val mode: SessionMode,
    val setting: String,
    val partnerRole: String,
    val learnerRole: String,
    val learnerGoal: String,
    val minLevel: CefrLevel,
    val maxLevel: CefrLevel,
    val competencyIds: List<String> = emptyList(),
    /** Fictional simulation of a sensitive real-world setting (healthcare, legal...). */
    val sensitive: Boolean = false,
    /** Opening lines by base language; the partner may vary them. */
    val openings: Map<String, String> = emptyMap(),
    /** Scripted follow-up prompts by base language, used by the offline partner. */
    val offlinePrompts: Map<String, List<String>> = emptyMap(),
    val learnerCreated: Boolean = false,
) {
    val disclaimer: String?
        get() = if (sensitive) "This is a fictional role-play for language practice. It is not medical, legal or other professional advice." else null
}
