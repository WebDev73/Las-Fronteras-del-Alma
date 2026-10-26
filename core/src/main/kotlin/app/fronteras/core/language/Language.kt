package app.fronteras.core.language

import kotlinx.serialization.Serializable

/**
 * BCP-47 language tag, e.g. `fr`, `fr-CA`, `es-419`, `pt-BR`.
 * Nothing in the platform assumes English is the learner's native language.
 */
@Serializable
@JvmInline
value class LanguageTag(val value: String) {
    init {
        require(value.matches(Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$"))) { "Invalid language tag: $value" }
    }

    /** Primary language subtag: `fr-CA` -> `fr`. */
    val base: String get() = value.substringBefore('-')

    /** Region or other subtags, if present. */
    val region: String? get() = value.substringAfter('-', "").ifEmpty { null }

    fun sameLanguageAs(other: LanguageTag): Boolean = base == other.base

    override fun toString(): String = value

    companion object {
        fun of(value: String) = LanguageTag(value)
    }
}

@Serializable
data class Language(
    val tag: LanguageTag,
    val englishName: String,
    val nativeName: String,
    val rightToLeft: Boolean = false,
)

/**
 * A regional or social variety of a language. Variants are first-class: a learner
 * selects a target variety and other legitimate varieties are never treated as defective.
 */
@Serializable
data class LanguageVariant(
    val tag: LanguageTag,
    val displayName: String,
    val nativeDisplayName: String,
    val description: String,
)
