package app.fronteras.core.language

/**
 * Languages the platform can use for interface, explanation and as known languages,
 * plus the target varieties for which curated learning content exists.
 *
 * Target-content availability is reported separately ([hasLearningContent]) so the UI
 * never offers a target language the content library cannot support.
 */
object LanguageCatalog {
    val languages: List<Language> = listOf(
        Language(LanguageTag("en"), "English", "English"),
        Language(LanguageTag("es"), "Spanish", "Español"),
        Language(LanguageTag("fr"), "French", "Français"),
        Language(LanguageTag("pt"), "Portuguese", "Português"),
        Language(LanguageTag("de"), "German", "Deutsch"),
        Language(LanguageTag("it"), "Italian", "Italiano"),
        Language(LanguageTag("ar"), "Arabic", "العربية", rightToLeft = true),
        Language(LanguageTag("zh"), "Chinese", "中文"),
        Language(LanguageTag("hi"), "Hindi", "हिन्दी"),
        Language(LanguageTag("ja"), "Japanese", "日本語"),
        Language(LanguageTag("ko"), "Korean", "한국어"),
        Language(LanguageTag("ru"), "Russian", "Русский"),
        Language(LanguageTag("tl"), "Tagalog", "Tagalog"),
        Language(LanguageTag("uk"), "Ukrainian", "Українська"),
        Language(LanguageTag("pl"), "Polish", "Polski"),
        Language(LanguageTag("vi"), "Vietnamese", "Tiếng Việt"),
    )

    val variants: List<LanguageVariant> = listOf(
        LanguageVariant(LanguageTag("fr-CA"), "Canadian French (Québec)", "Français canadien (Québec)",
            "French as spoken in Québec and much of Canada."),
        LanguageVariant(LanguageTag("fr-FR"), "European French", "Français de France",
            "French as spoken in France."),
        LanguageVariant(LanguageTag("es-419"), "Latin American Spanish", "Español latinoamericano",
            "A broad Latin American norm; regional differences remain."),
        LanguageVariant(LanguageTag("es-MX"), "Mexican Spanish", "Español de México",
            "Spanish as spoken in Mexico."),
        LanguageVariant(LanguageTag("es-ES"), "European Spanish", "Español de España",
            "Spanish as spoken in Spain."),
        LanguageVariant(LanguageTag("pt-BR"), "Brazilian Portuguese", "Português brasileiro",
            "Portuguese as spoken in Brazil."),
        LanguageVariant(LanguageTag("pt-PT"), "European Portuguese", "Português europeu",
            "Portuguese as spoken in Portugal."),
        LanguageVariant(LanguageTag("en-US"), "American English", "American English",
            "English as spoken in the United States."),
        LanguageVariant(LanguageTag("en-GB"), "British English", "British English",
            "English as spoken in the United Kingdom."),
        LanguageVariant(LanguageTag("en-CA"), "Canadian English", "Canadian English",
            "English as spoken in Canada."),
    )

    /** Base languages with curated curriculum, diagnostic items and scenarios. */
    val learningContentLanguages: Set<String> = setOf("es", "fr", "en")

    fun language(tag: LanguageTag): Language? = languages.firstOrNull { it.tag.base == tag.base }

    fun variant(tag: LanguageTag): LanguageVariant? = variants.firstOrNull { it.tag == tag }

    fun variantsOf(base: String): List<LanguageVariant> = variants.filter { it.tag.base == base }

    fun hasLearningContent(tag: LanguageTag): Boolean = tag.base in learningContentLanguages

    fun displayName(tag: LanguageTag): String =
        variant(tag)?.displayName ?: language(tag)?.englishName ?: tag.value
}
