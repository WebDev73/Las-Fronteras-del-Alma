package app.fronteras.core.language

import kotlinx.serialization.Serializable

/** What kind of statement a piece of usage guidance makes. Keeps rules and preferences apart. */
@Serializable
enum class GuidanceKind {
    /** Holds across varieties: a grammatical rule. */
    UNIVERSAL_RULE,
    /** A variety prefers this word or form; others are still correct elsewhere. */
    REGIONAL_PREFERENCE,
    /** Informal or colloquial; correct in context, may be unsuitable in formal settings. */
    INFORMAL,
    /** Stylistic recommendation, not a correctness judgement. */
    STYLISTIC,
    /** Same form, meaning depends on the variety or context. */
    CONTEXT_DEPENDENT_MEANING,
}

@Serializable
enum class ReviewStatus { CURATED_DRAFT, LINGUIST_REVIEWED }

@Serializable
data class VariantNote(
    val id: String,
    val base: String,
    val kind: GuidanceKind,
    /** Form per variety tag (`fr-CA` -> "magasiner"). */
    val forms: Map<String, String>,
    val meaning: String,
    val explanation: String,
    val register: Register? = null,
    val reviewStatus: ReviewStatus = ReviewStatus.CURATED_DRAFT,
)

@Serializable
enum class Register { FORMAL, NEUTRAL, INFORMAL }

/**
 * A small, deliberately conservative set of widely documented variety differences.
 * Every entry is marked [ReviewStatus.CURATED_DRAFT] until a qualified reviewer signs off;
 * the app labels them as such. Cultural "rules" are intentionally not encoded here.
 */
object VariantGuidance {
    val notes: List<VariantNote> = listOf(
        VariantNote(
            "fr-dejeuner", "fr", GuidanceKind.CONTEXT_DEPENDENT_MEANING,
            mapOf("fr-CA" to "déjeuner", "fr-FR" to "déjeuner"),
            meaning = "meal name",
            explanation = "In Québec 'déjeuner' usually means breakfast (lunch is 'dîner'); in France it usually means lunch (breakfast is 'petit-déjeuner').",
        ),
        VariantNote(
            "fr-shopping", "fr", GuidanceKind.REGIONAL_PREFERENCE,
            mapOf("fr-CA" to "magasiner", "fr-FR" to "faire les magasins / faire du shopping"),
            meaning = "to go shopping",
            explanation = "'Magasiner' is standard in Québec; speakers in France generally say 'faire les magasins'.",
        ),
        VariantNote(
            "fr-car", "fr", GuidanceKind.INFORMAL,
            mapOf("fr-CA" to "char", "fr-FR" to "bagnole"),
            meaning = "car (informal)",
            explanation = "'Voiture' is neutral everywhere. 'Char' (Québec) and 'bagnole' (France) are informal.",
            register = Register.INFORMAL,
        ),
        VariantNote(
            "es-plural-you", "es", GuidanceKind.REGIONAL_PREFERENCE,
            mapOf("es-ES" to "vosotros / ustedes", "es-419" to "ustedes"),
            meaning = "you (plural)",
            explanation = "Most of Spain uses 'vosotros' for informal plural address and 'ustedes' for formal; Latin America uses 'ustedes' for both.",
        ),
        VariantNote(
            "es-car", "es", GuidanceKind.REGIONAL_PREFERENCE,
            mapOf("es-ES" to "coche", "es-MX" to "carro / coche", "es-419" to "carro / auto"),
            meaning = "car",
            explanation = "Spain usually says 'coche'; much of Latin America says 'carro' or 'auto'. All are correct Spanish.",
        ),
        VariantNote(
            "es-computer", "es", GuidanceKind.REGIONAL_PREFERENCE,
            mapOf("es-ES" to "ordenador", "es-419" to "computadora"),
            meaning = "computer",
            explanation = "Spain uses 'ordenador'; Latin America generally uses 'computadora' (or 'computador').",
        ),
        VariantNote(
            "pt-bus", "pt", GuidanceKind.REGIONAL_PREFERENCE,
            mapOf("pt-BR" to "ônibus", "pt-PT" to "autocarro"),
            meaning = "bus",
            explanation = "Brazil says 'ônibus'; Portugal says 'autocarro'.",
        ),
        VariantNote(
            "pt-train", "pt", GuidanceKind.REGIONAL_PREFERENCE,
            mapOf("pt-BR" to "trem", "pt-PT" to "comboio"),
            meaning = "train",
            explanation = "Brazil says 'trem'; Portugal says 'comboio'.",
        ),
        VariantNote(
            "en-apartment", "en", GuidanceKind.REGIONAL_PREFERENCE,
            mapOf("en-US" to "apartment", "en-GB" to "flat", "en-CA" to "apartment"),
            meaning = "apartment",
            explanation = "'Flat' is common in the UK; 'apartment' in North America.",
        ),
    )

    fun forBase(base: String): List<VariantNote> = notes.filter { it.base == base }

    /** Notes relevant to a learner's chosen variety (those naming it, or any sibling variety). */
    fun forTarget(target: LanguageTag): List<VariantNote> =
        notes.filter { it.base == target.base && (target.region == null || it.forms.keys.any { k -> k == target.value || LanguageTag(k).base == target.base }) }

    /** Form the learner's variety prefers, falling back to the most general matching entry. */
    fun preferredForm(note: VariantNote, target: LanguageTag): String? =
        note.forms[target.value]
            ?: note.forms.entries.firstOrNull { it.key == "${target.base}-419" && target.region?.let { r -> r.length == 2 && r != "ES" } == true }?.value
}
