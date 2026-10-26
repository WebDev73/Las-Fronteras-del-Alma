package app.fronteras.core.profile

import app.fronteras.core.language.LanguageTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OnboardingTest {
    private val fr = LanguageTag("fr-CA")
    private val ar = LanguageTag("ar")
    private val en = LanguageTag("en")

    @Test
    fun `essential steps cannot be skipped`() {
        assertFailsWith<IllegalArgumentException> { OnboardingFlow.skip(OnboardingDraft(), OnboardingStep.TARGET_LANGUAGE) }
        val draft = OnboardingFlow.skip(OnboardingDraft(), OnboardingStep.GOALS)
        assertTrue(OnboardingStep.GOALS in draft.skipped)
    }

    @Test
    fun `incomplete drafts report what is missing`() {
        val r = OnboardingFlow.complete(OnboardingDraft(interfaceLanguage = en), "id", 0)
        assertIs<OnboardingResult.Incomplete>(r)
        assertEquals(listOf(OnboardingStep.TARGET_LANGUAGE, OnboardingStep.NATIVE_LANGUAGES), r.missing)
    }

    @Test
    fun `non-English native speakers get sensible defaults`() {
        val draft = OnboardingDraft(interfaceLanguage = en, target = fr, nativeLanguages = listOf(ar, LanguageTag("es")))
        assertEquals(OnboardingStep.OTHER_LANGUAGES, OnboardingFlow.nextStep(draft))
        val p = (OnboardingFlow.complete(draft, "id", 5) as OnboardingResult.Complete).profile
        assertEquals(2, p.nativeLanguages.size)
        assertEquals(ar, p.active.explanationLanguage, "interface language unknown to learner -> first native language")
        assertEquals(FeedbackMode.BALANCED, p.active.feedbackMode)
        assertEquals(fr, p.active.voice.accent)
    }

    @Test
    fun `unsupported target languages are flagged`() {
        val r = OnboardingFlow.complete(OnboardingDraft(interfaceLanguage = en, target = LanguageTag("ja"), nativeLanguages = listOf(en)), "id", 0)
        assertIs<OnboardingResult.Incomplete>(r)
        assertTrue(r.problems.isNotEmpty())
    }

    @Test
    fun `learners can add more target languages`() {
        val p = (OnboardingFlow.complete(OnboardingDraft(interfaceLanguage = en, target = fr, nativeLanguages = listOf(en)), "id", 0) as OnboardingResult.Complete).profile
        val es = TargetLanguageProfile(LanguageTag("es-MX"), explanationLanguage = en)
        val p2 = OnboardingFlow.addTarget(p, es, 1)
        assertEquals(2, p2.targets.size)
        assertEquals(es.target, p2.activeTarget)
        assertFailsWith<IllegalArgumentException> { OnboardingFlow.addTarget(p2, es, 2) }
    }
}
