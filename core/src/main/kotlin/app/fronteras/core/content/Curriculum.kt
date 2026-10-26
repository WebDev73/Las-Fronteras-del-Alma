package app.fronteras.core.content

import app.fronteras.core.assessment.CefrLevel.A1
import app.fronteras.core.assessment.CefrLevel.A2
import app.fronteras.core.assessment.CefrLevel.B1
import app.fronteras.core.assessment.CefrLevel.B2
import app.fronteras.core.assessment.CefrLevel.C1
import app.fronteras.core.assessment.Skill
import app.fronteras.core.curriculum.ActivityType
import app.fronteras.core.curriculum.AssessmentMethod
import app.fronteras.core.curriculum.Competency
import app.fronteras.core.curriculum.CompetencyGraph
import app.fronteras.core.curriculum.Expression
import app.fronteras.core.curriculum.MasteryCriteria
import app.fronteras.core.profile.LearningGoal

/** Seed curriculum. Competencies are language-independent; expressions are per language. */
object Curriculum {
    private val LANGS = setOf("es", "fr", "en")
    private fun e(text: String, gloss: String, vararg variants: String) = Expression(text, gloss, variants = variants.toList())

    val competencies: List<Competency> = listOf(
        Competency(
            id = "greet-introduce", title = "Introduce yourself", canDo = "Introduce yourself and ask follow-up questions.",
            minLevel = A1, maxLevel = A2, skills = setOf(Skill.SPEAKING, Skill.INTERACTION, Skill.LISTENING),
            goals = LearningGoal.entries.toSet(),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.GUIDED_DIALOGUE, ActivityType.ROLE_PLAY, ActivityType.RETRIEVAL_PRACTICE),
            mastery = MasteryCriteria(description = "Introduce yourself and keep the exchange going with at least two follow-up questions, without switching language."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, scenarioIds = listOf("meet-neighbour"), typicalPracticeMinutes = 20,
            expressions = mapOf(
                "es" to listOf(e("Me llamo…", "My name is…"), e("Soy de…", "I'm from…"), e("¿Y tú, a qué te dedicas?", "And you, what do you do?"), e("Mucho gusto.", "Nice to meet you."), e("¿Cuánto tiempo llevas aquí?", "How long have you been here?")),
                "fr" to listOf(e("Je m'appelle…", "My name is…"), e("Je viens de…", "I'm from…"), e("Et toi, qu'est-ce que tu fais dans la vie ?", "And you, what do you do?"), e("Enchanté(e).", "Nice to meet you."), e("Ça fait combien de temps que tu es ici ?", "How long have you been here?")),
                "en" to listOf(e("My name is…", "Me llamo… / Je m'appelle…"), e("I'm from…", "Soy de… / Je viens de…"), e("What do you do?", "¿A qué te dedicas? / Qu'est-ce que tu fais ?"), e("Nice to meet you.", "Mucho gusto. / Enchanté."), e("How long have you been here?", "¿Cuánto tiempo llevas aquí?")),
            ),
        ),
        Competency(
            id = "everyday-transactions", title = "Order and pay", canDo = "Order food or drinks and pay politely.",
            minLevel = A1, maxLevel = A2, skills = setOf(Skill.SPEAKING, Skill.LISTENING, Skill.VOCABULARY),
            prerequisites = setOf("greet-introduce"), goals = setOf(LearningGoal.TRAVEL, LearningGoal.CONVERSATION, LearningGoal.IMMIGRATION, LearningGoal.CULTURAL_INTEGRATION),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.ROLE_PLAY, ActivityType.LISTENING_TASK),
            mastery = MasteryCriteria(description = "Complete an order, handle one unexpected question (e.g. size or an item being unavailable) and pay."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, scenarioIds = listOf("cafe-order"), typicalPracticeMinutes = 20,
            expressions = mapOf(
                "es" to listOf(e("Quería un café con leche, por favor.", "I'd like a white coffee, please."), e("¿Me pone un café?", "Could I have a coffee? (Spain)", "es-ES"), e("¿Me trae la cuenta, por favor?", "Could you bring the bill, please?"), e("¿Cuánto es?", "How much is it?"), e("Para llevar.", "To take away.")),
                "fr" to listOf(e("Je voudrais un café, s'il vous plaît.", "I'd like a coffee, please."), e("L'addition, s'il vous plaît.", "The bill, please."), e("C'est combien ?", "How much is it?"), e("À emporter.", "To take away.", "fr-FR"), e("Pour emporter.", "To take away.", "fr-CA")),
                "en" to listOf(e("Could I get a coffee, please?", "¿Me pone un café? / Je voudrais un café."), e("Could we have the bill, please?", "La cuenta / L'addition", "en-GB"), e("Could we get the check, please?", "La cuenta / L'addition", "en-US", "en-CA"), e("How much is it?", "¿Cuánto es? / C'est combien ?"), e("To go, please.", "Para llevar / À emporter", "en-US", "en-CA"), e("To take away, please.", "Para llevar / À emporter", "en-GB")),
            ),
        ),
        Competency(
            id = "travel-problem", title = "Handle a travel problem", canDo = "Ask for directions and handle an unfamiliar travel situation.",
            minLevel = A2, maxLevel = B1, skills = setOf(Skill.SPEAKING, Skill.LISTENING, Skill.INTERACTION),
            prerequisites = setOf("everyday-transactions"), goals = setOf(LearningGoal.TRAVEL, LearningGoal.IMMIGRATION),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.ROLE_PLAY, ActivityType.LISTENING_TASK),
            mastery = MasteryCriteria(description = "Explain the problem, understand the proposed solution and confirm the next step."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, scenarioIds = listOf("train-station"), typicalPracticeMinutes = 30,
            expressions = mapOf(
                "es" to listOf(e("Perdone, ¿cómo llego a la estación?", "Excuse me, how do I get to the station?"), e("¿Está lejos de aquí?", "Is it far from here?"), e("He perdido el billete.", "I've lost my ticket.", "es-ES"), e("Perdí mi boleto.", "I lost my ticket.", "es-419", "es-MX"), e("¿Podría repetirlo más despacio?", "Could you repeat that more slowly?")),
                "fr" to listOf(e("Excusez-moi, comment je peux aller à la gare ?", "Excuse me, how can I get to the station?"), e("C'est loin d'ici ?", "Is it far from here?"), e("J'ai perdu mon billet.", "I've lost my ticket."), e("Pourriez-vous répéter plus lentement ?", "Could you repeat more slowly?")),
                "en" to listOf(e("Excuse me, how do I get to the station?", "¿Cómo llego a la estación?"), e("Is it far from here?", "¿Está lejos?"), e("I've lost my ticket.", "He perdido el billete."), e("Could you say that again more slowly?", "¿Podría repetirlo más despacio?")),
            ),
        ),
        Competency(
            id = "describe-work", title = "Talk about your work", canDo = "Describe your professional responsibilities.",
            minLevel = A2, maxLevel = B1, skills = setOf(Skill.SPEAKING, Skill.VOCABULARY, Skill.GRAMMAR),
            prerequisites = setOf("greet-introduce"), goals = setOf(LearningGoal.WORK, LearningGoal.IMMIGRATION, LearningGoal.CONVERSATION, LearningGoal.EXAMINATION),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.GUIDED_DIALOGUE, ActivityType.RETRIEVAL_PRACTICE),
            mastery = MasteryCriteria(description = "Explain what you do, one typical task and one challenge, answering follow-up questions."),
            assessment = AssessmentMethod.ORAL_PRODUCTION_TASK, languages = LANGS, typicalPracticeMinutes = 30,
            expressions = mapOf(
                "es" to listOf(e("Trabajo como…", "I work as…"), e("Me encargo de…", "I'm in charge of…"), e("Mi trabajo consiste en…", "My job involves…"), e("Lo que más me gusta es…", "What I like most is…")),
                "fr" to listOf(e("Je travaille comme…", "I work as…"), e("Je m'occupe de…", "I take care of…"), e("Mon travail consiste à…", "My job involves…"), e("Ce que je préfère, c'est…", "What I like best is…")),
                "en" to listOf(e("I work as…", "Trabajo como… / Je travaille comme…"), e("I'm responsible for…", "Me encargo de… / Je m'occupe de…"), e("My job involves…", "Mi trabajo consiste en…"), e("What I enjoy most is…", "Lo que más me gusta es…")),
            ),
        ),
        Competency(
            id = "make-plans", title = "Make plans", canDo = "Suggest, accept and politely decline plans.",
            minLevel = A2, maxLevel = B1, skills = setOf(Skill.SPEAKING, Skill.INTERACTION),
            prerequisites = setOf("greet-introduce"), goals = setOf(LearningGoal.CONVERSATION, LearningGoal.RELATIONSHIPS, LearningGoal.CULTURAL_INTEGRATION),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.ROLE_PLAY),
            mastery = MasteryCriteria(description = "Agree on a time and place, including turning down one suggestion politely."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, scenarioIds = listOf("weekend-plans"), typicalPracticeMinutes = 20,
            expressions = mapOf(
                "es" to listOf(e("¿Te apetece ir al cine?", "Do you fancy going to the cinema?", "es-ES"), e("¿Tienes ganas de ir al cine?", "Do you feel like going to the cinema?"), e("¿Qué tal si…?", "How about…?"), e("Me encantaría, pero…", "I'd love to, but…"), e("¿Quedamos a las ocho?", "Shall we meet at eight?", "es-ES")),
                "fr" to listOf(e("Ça te dit d'aller au cinéma ?", "Do you fancy going to the cinema?"), e("Et si on… ?", "What if we…?"), e("J'aimerais bien, mais…", "I'd like to, but…"), e("On se retrouve à quelle heure ?", "What time shall we meet?")),
                "en" to listOf(e("Do you fancy going to the cinema?", "¿Te apetece…?", "en-GB"), e("Do you want to go to the movies?", "¿Quieres ir al cine?", "en-US", "en-CA"), e("How about…?", "¿Qué tal si…?"), e("I'd love to, but…", "Me encantaría, pero…"), e("What time shall we meet?", "¿A qué hora quedamos?")),
            ),
        ),
        Competency(
            id = "narrate-past", title = "Tell what happened", canDo = "Narrate a past event using appropriate tenses.",
            minLevel = B1, maxLevel = B1, skills = setOf(Skill.SPEAKING, Skill.GRAMMAR, Skill.FLUENCY),
            prerequisites = setOf("greet-introduce"), goals = LearningGoal.entries.toSet(),
            activities = listOf(ActivityType.GRAMMAR_FOCUS, ActivityType.STORYTELLING, ActivityType.RETRIEVAL_PRACTICE),
            mastery = MasteryCriteria(description = "Tell a two-minute story with background and events clearly distinguished in time."),
            assessment = AssessmentMethod.ORAL_PRODUCTION_TASK, languages = LANGS, scenarioIds = listOf("tell-a-story"), typicalPracticeMinutes = 40,
            expressions = mapOf(
                "es" to listOf(e("El año pasado…", "Last year…"), e("Mientras estaba…, de repente…", "While I was…, suddenly…"), e("Al final…", "In the end…"), e("Lo que pasó fue que…", "What happened was…")),
                "fr" to listOf(e("L'année dernière…", "Last year…"), e("Pendant que j'étais…, tout à coup…", "While I was…, suddenly…"), e("Finalement…", "In the end…"), e("Ce qui s'est passé, c'est que…", "What happened was…")),
                "en" to listOf(e("Last year…", "El año pasado…"), e("While I was…, suddenly…", "Mientras estaba…, de repente…"), e("In the end…", "Al final…"), e("What happened was…", "Lo que pasó fue que…")),
            ),
        ),
        Competency(
            id = "repair-misunderstanding", title = "Repair misunderstandings", canDo = "Ask for clarification and paraphrase without switching languages.",
            minLevel = A2, maxLevel = B2, skills = setOf(Skill.INTERACTION, Skill.VOCABULARY, Skill.LISTENING),
            prerequisites = setOf("greet-introduce"), goals = LearningGoal.entries.toSet(),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.GUIDED_DIALOGUE, ActivityType.FREE_CONVERSATION),
            mastery = MasteryCriteria(description = "Recover from two misunderstandings in a conversation by asking or paraphrasing, staying in the target language."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, typicalPracticeMinutes = 30,
            expressions = mapOf(
                "es" to listOf(e("¿Qué quieres decir con…?", "What do you mean by…?"), e("Perdona, no te he entendido.", "Sorry, I didn't understand you.", "es-ES"), e("Perdón, no te entendí.", "Sorry, I didn't understand you.", "es-419", "es-MX"), e("Lo que quiero decir es que…", "What I mean is…"), e("Es una cosa que sirve para…", "It's a thing used for…")),
                "fr" to listOf(e("Qu'est-ce que tu veux dire par… ?", "What do you mean by…?"), e("Pardon, je n'ai pas compris.", "Sorry, I didn't understand."), e("Ce que je veux dire, c'est que…", "What I mean is…"), e("C'est un truc qui sert à…", "It's a thing used for… (informal)")),
                "en" to listOf(e("What do you mean by…?", "¿Qué quieres decir con…?"), e("Sorry, I didn't catch that.", "Perdona, no te he entendido."), e("What I mean is…", "Lo que quiero decir es…"), e("It's a thing you use for…", "Es una cosa que sirve para…")),
            ),
        ),
        Competency(
            id = "express-opinion", title = "Give and defend an opinion", canDo = "Express an opinion and defend it with reasons.",
            minLevel = B1, maxLevel = B2, skills = setOf(Skill.SPEAKING, Skill.GRAMMAR, Skill.VOCABULARY, Skill.INTERACTION),
            prerequisites = setOf("narrate-past"), goals = setOf(LearningGoal.CONVERSATION, LearningGoal.EDUCATION, LearningGoal.EXAMINATION, LearningGoal.WORK),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.DEBATE, ActivityType.FREE_CONVERSATION),
            mastery = MasteryCriteria(description = "State a view with two reasons and respond to a counter-argument."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, scenarioIds = listOf("debate-remote-work"), typicalPracticeMinutes = 40,
            expressions = mapOf(
                "es" to listOf(e("En mi opinión…", "In my opinion…"), e("Creo que… porque…", "I think… because…"), e("Por un lado…, por otro…", "On the one hand…, on the other…"), e("No estoy de acuerdo, ya que…", "I don't agree, since…"), e("Depende de…", "It depends on…")),
                "fr" to listOf(e("À mon avis…", "In my opinion…"), e("Je pense que… parce que…", "I think… because…"), e("D'un côté…, de l'autre…", "On the one hand…, on the other…"), e("Je ne suis pas d'accord, car…", "I don't agree, because…"), e("Ça dépend de…", "It depends on…")),
                "en" to listOf(e("In my opinion…", "En mi opinión… / À mon avis…"), e("I think… because…", "Creo que… porque…"), e("On the one hand…, on the other…", "Por un lado…, por otro…"), e("I don't agree, because…", "No estoy de acuerdo…"), e("It depends on…", "Depende de…")),
            ),
        ),
        Competency(
            id = "explain-technical-problem", title = "Explain a technical problem", canDo = "Explain a technical problem and what you have already tried.",
            minLevel = B1, maxLevel = B2, skills = setOf(Skill.SPEAKING, Skill.VOCABULARY, Skill.INTERACTION),
            prerequisites = setOf("describe-work", "repair-misunderstanding"), goals = setOf(LearningGoal.WORK),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.ROLE_PLAY),
            mastery = MasteryCriteria(description = "Describe symptoms, steps already taken and the desired outcome clearly enough for the listener to act."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, scenarioIds = listOf("it-support"), typicalPracticeMinutes = 30,
            expressions = mapOf(
                "es" to listOf(e("El problema es que…", "The problem is that…"), e("Cuando intento…, me sale un error.", "When I try to…, I get an error."), e("Ya he probado a…", "I've already tried…"), e("¿Podrías echarle un vistazo?", "Could you take a look?")),
                "fr" to listOf(e("Le problème, c'est que…", "The problem is that…"), e("Quand j'essaie de…, j'ai un message d'erreur.", "When I try to…, I get an error message."), e("J'ai déjà essayé de…", "I've already tried to…"), e("Tu pourrais y jeter un œil ?", "Could you take a look?")),
                "en" to listOf(e("The problem is that…", "El problema es que…"), e("When I try to…, I get an error.", "Cuando intento…, me sale un error."), e("I've already tried…", "Ya he probado a…"), e("Could you take a look?", "¿Podrías echarle un vistazo?")),
            ),
        ),
        Competency(
            id = "natural-expressions", title = "Sound natural", canDo = "Understand and respond to natural conversational expressions.",
            minLevel = B1, maxLevel = C1, skills = setOf(Skill.LISTENING, Skill.VOCABULARY, Skill.INTERACTION),
            prerequisites = setOf("express-opinion"), goals = setOf(LearningGoal.CONVERSATION, LearningGoal.RELATIONSHIPS, LearningGoal.CULTURAL_INTEGRATION),
            activities = listOf(ActivityType.LISTENING_TASK, ActivityType.FREE_CONVERSATION, ActivityType.RETRIEVAL_PRACTICE),
            mastery = MasteryCriteria(description = "Respond appropriately to informal expressions and use several yourself in a conversation."),
            assessment = AssessmentMethod.LISTEN_AND_RESPOND, languages = LANGS, typicalPracticeMinutes = 40,
            expressions = mapOf(
                "es" to listOf(e("¡Qué va!", "No way! / Not at all!", "es-ES"), e("Vale.", "OK.", "es-ES"), e("Ya veo.", "I see."), e("¿En serio?", "Really?"), e("Bueno, pues…", "Well then…")),
                "fr" to listOf(e("Ah bon ?", "Really?"), e("C'est clair.", "Absolutely. (informal)"), e("Bref…", "Anyway…"), e("Pas mal.", "Not bad."), e("C'est correct.", "It's fine / OK.", "fr-CA")),
                "en" to listOf(e("Fair enough.", "Vale, es justo."), e("No way!", "¡Qué va! / ¡No me digas!"), e("Anyway…", "Bueno… / Bref…"), e("That makes sense.", "Tiene sentido."), e("I see what you mean.", "Ya veo lo que quieres decir.")),
            ),
        ),
        Competency(
            id = "five-minute-conversation", title = "Five-minute conversation", canDo = "Take part in a five-minute conversation with limited assistance.",
            minLevel = B1, maxLevel = C1, skills = setOf(Skill.SPEAKING, Skill.FLUENCY, Skill.INTERACTION, Skill.LISTENING),
            prerequisites = setOf("express-opinion", "repair-misunderstanding"), goals = LearningGoal.entries.toSet(),
            activities = listOf(ActivityType.FREE_CONVERSATION),
            mastery = MasteryCriteria(independentSuccesses = 2, distinctSessions = 2, minRubricScore = 2.5,
                description = "Sustain five minutes on a familiar topic with no more than occasional help, in two separate sessions."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, scenarioIds = listOf("free-chat"), typicalPracticeMinutes = 60,
            expressions = mapOf(
                "es" to listOf(e("A ver…", "Let's see…"), e("O sea…", "I mean…"), e("Es decir…", "That is…")),
                "fr" to listOf(e("Alors…", "So…"), e("En fait…", "Actually…"), e("C'est-à-dire…", "That is…")),
                "en" to listOf(e("Well…", "Pues…"), e("Actually…", "En realidad…"), e("I mean…", "O sea…")),
            ),
        ),
        Competency(
            id = "healthcare-visit", title = "At the doctor's (fictional)", canDo = "Describe symptoms and understand simple instructions in a fictional appointment.",
            minLevel = A2, maxLevel = B1, skills = setOf(Skill.SPEAKING, Skill.LISTENING, Skill.VOCABULARY),
            prerequisites = setOf("everyday-transactions"), goals = setOf(LearningGoal.IMMIGRATION, LearningGoal.TRAVEL, LearningGoal.CULTURAL_INTEGRATION),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.ROLE_PLAY),
            mastery = MasteryCriteria(description = "Describe two symptoms with duration and confirm the instructions you were given."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, scenarioIds = listOf("doctor-appointment"), typicalPracticeMinutes = 30,
            expressions = mapOf(
                "es" to listOf(e("Me duele la cabeza.", "I have a headache."), e("Tengo fiebre desde ayer.", "I've had a fever since yesterday."), e("¿Tengo que tomar algo?", "Do I need to take anything?"), e("Soy alérgico / alérgica a…", "I'm allergic to…")),
                "fr" to listOf(e("J'ai mal à la tête.", "I have a headache."), e("J'ai de la fièvre depuis hier.", "I've had a fever since yesterday."), e("Est-ce que je dois prendre quelque chose ?", "Do I need to take anything?"), e("Je suis allergique à…", "I'm allergic to…")),
                "en" to listOf(e("I've got a headache.", "Me duele la cabeza."), e("I've had a fever since yesterday.", "Tengo fiebre desde ayer."), e("Do I need to take anything?", "¿Tengo que tomar algo?"), e("I'm allergic to…", "Soy alérgico a…")),
            ),
        ),
        Competency(
            id = "job-interview", title = "Job interview", canDo = "Answer common interview questions and ask your own.",
            minLevel = B1, maxLevel = B2, skills = setOf(Skill.SPEAKING, Skill.FLUENCY, Skill.VOCABULARY, Skill.INTERACTION),
            prerequisites = setOf("describe-work", "express-opinion"), goals = setOf(LearningGoal.WORK, LearningGoal.IMMIGRATION),
            activities = listOf(ActivityType.CONTEXT_AND_EXPRESSIONS, ActivityType.ROLE_PLAY),
            mastery = MasteryCriteria(description = "Answer four interview questions with relevant examples and ask one question of your own."),
            assessment = AssessmentMethod.CONVERSATION_TASK, languages = LANGS, scenarioIds = listOf("job-interview"), typicalPracticeMinutes = 45,
            expressions = mapOf(
                "es" to listOf(e("Tengo experiencia en…", "I have experience in…"), e("Uno de mis puntos fuertes es…", "One of my strengths is…"), e("Me gustaría saber más sobre…", "I'd like to know more about…"), e("En mi puesto anterior…", "In my previous job…")),
                "fr" to listOf(e("J'ai de l'expérience dans…", "I have experience in…"), e("L'un de mes points forts, c'est…", "One of my strengths is…"), e("J'aimerais en savoir plus sur…", "I'd like to know more about…"), e("Dans mon poste précédent…", "In my previous job…")),
                "en" to listOf(e("I have experience in…", "Tengo experiencia en…"), e("One of my strengths is…", "Uno de mis puntos fuertes es…"), e("I'd like to know more about…", "Me gustaría saber más sobre…"), e("In my previous role…", "En mi puesto anterior…")),
            ),
        ),
    )

    val graph: CompetencyGraph by lazy { CompetencyGraph(competencies) }
}
