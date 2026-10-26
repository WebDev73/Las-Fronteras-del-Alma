package app.fronteras.core.content

import app.fronteras.core.assessment.CefrLevel
import app.fronteras.core.conversation.Scenario
import app.fronteras.core.conversation.SessionMode

object Scenarios {
    val all: List<Scenario> = listOf(
        Scenario(
            id = "free-chat", title = "Free conversation", mode = SessionMode.FREE_CONVERSATION,
            setting = "An informal chat about whatever the learner wants to talk about.",
            partnerRole = "a friendly conversation partner", learnerRole = "yourself",
            learnerGoal = "Talk about anything. Change the subject whenever you like.",
            minLevel = CefrLevel.A1, maxLevel = CefrLevel.C2, competencyIds = listOf("five-minute-conversation"),
            openings = mapOf("es" to "¡Hola! ¿Qué tal? ¿De qué te apetece hablar hoy?", "fr" to "Salut ! Comment ça va ? De quoi tu as envie de parler aujourd'hui ?", "en" to "Hi! How are you doing? What would you like to talk about today?"),
            offlinePrompts = mapOf(
                "es" to listOf("¿Qué has hecho hoy?", "¿Qué te gusta hacer los fines de semana?", "Cuéntame algo sobre tu ciudad.", "¿Qué planes tienes para esta semana?", "¿Cuál es tu comida favorita y por qué?"),
                "fr" to listOf("Qu'est-ce que tu as fait aujourd'hui ?", "Qu'est-ce que tu aimes faire le week-end ?", "Parle-moi un peu de ta ville.", "Qu'est-ce que tu as prévu cette semaine ?", "Quel est ton plat préféré, et pourquoi ?"),
                "en" to listOf("What have you done today?", "What do you like doing at weekends?", "Tell me a bit about your town.", "What are your plans for this week?", "What's your favourite food, and why?"),
            ),
        ),
        Scenario(
            id = "meet-neighbour", title = "Meeting a new neighbour", mode = SessionMode.ROLE_PLAY,
            setting = "You meet your new neighbour in the hallway of your building.",
            partnerRole = "a friendly new neighbour", learnerRole = "a resident of the building",
            learnerGoal = "Introduce yourself and find out two things about your neighbour.",
            minLevel = CefrLevel.A1, maxLevel = CefrLevel.A2, competencyIds = listOf("greet-introduce"),
            openings = mapOf("es" to "¡Hola! Creo que somos vecinos. Acabo de mudarme al tercero.", "fr" to "Bonjour ! Je crois qu'on est voisins. Je viens d'emménager au troisième.", "en" to "Hi! I think we're neighbours. I've just moved in on the third floor."),
            offlinePrompts = mapOf(
                "es" to listOf("¿Y tú, cuánto tiempo llevas aquí?", "¿A qué te dedicas?", "¿Conoces algún buen sitio para comer por aquí?", "¡Encantado de conocerte! Nos vemos pronto."),
                "fr" to listOf("Et toi, ça fait longtemps que tu habites ici ?", "Qu'est-ce que tu fais dans la vie ?", "Tu connais un bon endroit pour manger dans le coin ?", "Ravi de te rencontrer ! À bientôt."),
                "en" to listOf("How long have you lived here?", "What do you do?", "Do you know any good places to eat around here?", "Lovely to meet you! See you around."),
            ),
        ),
        Scenario(
            id = "cafe-order", title = "Ordering at a café", mode = SessionMode.ROLE_PLAY,
            setting = "A busy café at lunchtime.", partnerRole = "a server", learnerRole = "a customer",
            learnerGoal = "Order something to drink and to eat, answer the server's questions and pay.",
            minLevel = CefrLevel.A1, maxLevel = CefrLevel.A2, competencyIds = listOf("everyday-transactions"),
            openings = mapOf("es" to "¡Buenas! ¿Qué le pongo?", "fr" to "Bonjour ! Qu'est-ce que je vous sers ?", "en" to "Hi there! What can I get you?"),
            offlinePrompts = mapOf(
                "es" to listOf("¿Lo quiere grande o pequeño?", "Lo siento, no nos quedan cruasanes. ¿Le apetece otra cosa?", "¿Para tomar aquí o para llevar?", "Son seis euros con cincuenta. ¿Paga con tarjeta?"),
                "fr" to listOf("Vous le voulez grand ou petit ?", "Désolé, il n'y a plus de croissants. Vous voulez autre chose ?", "Sur place ou à emporter ?", "Ça fera six euros cinquante. Vous payez par carte ?"),
                "en" to listOf("Would you like a large or a small one?", "Sorry, we're out of croissants. Would you like something else?", "Is that to have here or to take away?", "That's six fifty. Are you paying by card?"),
            ),
        ),
        Scenario(
            id = "train-station", title = "A problem at the station", mode = SessionMode.ROLE_PLAY,
            setting = "A train station. Your train was cancelled and you can't find your ticket.",
            partnerRole = "a station employee", learnerRole = "a traveller",
            learnerGoal = "Explain your problem, understand the options and agree on what to do.",
            minLevel = CefrLevel.A2, maxLevel = CefrLevel.B1, competencyIds = listOf("travel-problem", "repair-misunderstanding"),
            openings = mapOf("es" to "Buenas tardes, ¿en qué puedo ayudarle?", "fr" to "Bonjour, je peux vous aider ?", "en" to "Good afternoon, how can I help you?"),
            offlinePrompts = mapOf(
                "es" to listOf("¿Tiene el número de reserva?", "El próximo tren sale a las seis y cuarto del andén dos. ¿Le viene bien?", "Tendría que ir a la taquilla para cambiar el billete. Está al fondo, a la derecha.", "¿Necesita algo más?"),
                "fr" to listOf("Vous avez votre numéro de réservation ?", "Le prochain train part à dix-huit heures quinze, quai deux. Ça vous va ?", "Il faudrait aller au guichet pour changer votre billet. C'est au fond, à droite.", "Vous avez besoin d'autre chose ?"),
                "en" to listOf("Do you have your booking reference?", "The next train leaves at quarter past six from platform two. Does that work for you?", "You'd need to go to the ticket office to change your ticket. It's at the end on the right.", "Is there anything else you need?"),
            ),
        ),
        Scenario(
            id = "weekend-plans", title = "Making weekend plans", mode = SessionMode.ROLE_PLAY,
            setting = "Messaging a friend to organise something for the weekend.",
            partnerRole = "a friend", learnerRole = "yourself",
            learnerGoal = "Agree on an activity, a time and a place. Turn down at least one idea politely.",
            minLevel = CefrLevel.A2, maxLevel = CefrLevel.B1, competencyIds = listOf("make-plans"),
            openings = mapOf("es" to "¡Ey! ¿Haces algo este fin de semana?", "fr" to "Coucou ! Tu fais quelque chose ce week-end ?", "en" to "Hey! Are you up to anything this weekend?"),
            offlinePrompts = mapOf(
                "es" to listOf("¿Y si vamos de excursión el sábado por la mañana?", "Vale, ¿y el domingo? ¿Te va mejor?", "¿A qué hora quedamos y dónde?", "¡Perfecto! Nos vemos entonces."),
                "fr" to listOf("Et si on faisait une randonnée samedi matin ?", "D'accord, et dimanche ? C'est mieux pour toi ?", "On se retrouve à quelle heure, et où ?", "Parfait ! À ce week-end."),
                "en" to listOf("How about a hike on Saturday morning?", "OK, what about Sunday? Is that better for you?", "What time shall we meet, and where?", "Perfect! See you then."),
            ),
        ),
        Scenario(
            id = "doctor-appointment", title = "At the doctor's (fictional)", mode = SessionMode.ROLE_PLAY,
            setting = "A fictional appointment at a general practice.", partnerRole = "a fictional doctor", learnerRole = "a patient",
            learnerGoal = "Describe how you feel and since when, and check what you should do.",
            minLevel = CefrLevel.A2, maxLevel = CefrLevel.B1, competencyIds = listOf("healthcare-visit"), sensitive = true,
            openings = mapOf("es" to "Buenos días, siéntese. ¿Qué le pasa?", "fr" to "Bonjour, asseyez-vous. Qu'est-ce qui vous amène ?", "en" to "Good morning, have a seat. What seems to be the problem?"),
            offlinePrompts = mapOf(
                "es" to listOf("¿Desde cuándo se encuentra así?", "¿Tiene fiebre?", "¿Es alérgico a algún medicamento?", "De acuerdo. Descanse y beba mucha agua. Si no mejora en tres días, vuelva."),
                "fr" to listOf("Depuis quand vous sentez-vous comme ça ?", "Vous avez de la fièvre ?", "Vous êtes allergique à des médicaments ?", "D'accord. Reposez-vous et buvez beaucoup d'eau. Si ça ne va pas mieux dans trois jours, revenez me voir."),
                "en" to listOf("How long have you been feeling like this?", "Have you got a temperature?", "Are you allergic to any medication?", "All right. Rest and drink plenty of water. If you're not better in three days, come back and see me."),
            ),
        ),
        Scenario(
            id = "job-interview", title = "Job interview", mode = SessionMode.INTERVIEW,
            setting = "A job interview for a role in the learner's field.", partnerRole = "an interviewer", learnerRole = "a candidate",
            learnerGoal = "Answer questions with concrete examples and ask one question yourself.",
            minLevel = CefrLevel.B1, maxLevel = CefrLevel.C1, competencyIds = listOf("job-interview", "describe-work"),
            openings = mapOf("es" to "Buenos días, gracias por venir. Para empezar, ¿podría hablarme un poco de usted?", "fr" to "Bonjour, merci d'être venu. Pour commencer, pouvez-vous me parler un peu de vous ?", "en" to "Good morning, thanks for coming in. To start, could you tell me a little about yourself?"),
            offlinePrompts = mapOf(
                "es" to listOf("¿Por qué le interesa este puesto?", "Hábleme de un problema difícil que haya resuelto en el trabajo.", "¿Cuáles son sus puntos fuertes y débiles?", "¿Tiene alguna pregunta para nosotros?"),
                "fr" to listOf("Pourquoi ce poste vous intéresse-t-il ?", "Parlez-moi d'un problème difficile que vous avez résolu au travail.", "Quels sont vos points forts et vos points faibles ?", "Avez-vous des questions à nous poser ?"),
                "en" to listOf("Why are you interested in this role?", "Tell me about a difficult problem you solved at work.", "What would you say are your strengths and weaknesses?", "Do you have any questions for us?"),
            ),
        ),
        Scenario(
            id = "it-support", title = "Calling IT support", mode = SessionMode.ROLE_PLAY,
            setting = "Your laptop won't connect to the office network and you have a meeting soon.",
            partnerRole = "an IT support technician", learnerRole = "an employee",
            learnerGoal = "Explain the problem, what you've tried, and agree on a solution.",
            minLevel = CefrLevel.B1, maxLevel = CefrLevel.B2, competencyIds = listOf("explain-technical-problem"),
            openings = mapOf("es" to "Soporte técnico, buenos días. ¿En qué le puedo ayudar?", "fr" to "Support informatique, bonjour. Que puis-je faire pour vous ?", "en" to "IT support, good morning. How can I help?"),
            offlinePrompts = mapOf(
                "es" to listOf("¿Qué mensaje de error le aparece exactamente?", "¿Ha probado a reiniciar el ordenador?", "¿Otros compañeros tienen el mismo problema?", "Vale, voy a abrir una incidencia. ¿Me da su número de empleado?"),
                "fr" to listOf("Quel message d'erreur s'affiche exactement ?", "Vous avez essayé de redémarrer l'ordinateur ?", "Est-ce que vos collègues ont le même problème ?", "D'accord, je vais ouvrir un ticket. Vous pouvez me donner votre numéro d'employé ?"),
                "en" to listOf("What error message do you see exactly?", "Have you tried restarting the laptop?", "Are your colleagues having the same problem?", "OK, I'll open a ticket. Could I have your employee number?"),
            ),
        ),
        Scenario(
            id = "debate-remote-work", title = "Debate: remote work", mode = SessionMode.DEBATE,
            setting = "A friendly debate about whether remote work is better than office work.",
            partnerRole = "a colleague who takes the opposite view", learnerRole = "yourself",
            learnerGoal = "Defend your view with reasons and respond to counter-arguments.",
            minLevel = CefrLevel.B1, maxLevel = CefrLevel.C1, competencyIds = listOf("express-opinion"),
            openings = mapOf("es" to "Yo creo que trabajar en la oficina es mucho mejor que trabajar desde casa. ¿Tú qué opinas?", "fr" to "Moi, je trouve que travailler au bureau, c'est bien mieux que le télétravail. Tu en penses quoi ?", "en" to "Honestly, I think working in the office is much better than working from home. What's your view?"),
            offlinePrompts = mapOf(
                "es" to listOf("Pero en casa es más difícil separar el trabajo de la vida personal, ¿no crees?", "¿Y qué pasa con la comunicación con el equipo?", "Entiendo tu punto, pero ¿no te sientes más aislado?", "Vale, quizás lo mejor sea un modelo mixto. ¿Qué te parece?"),
                "fr" to listOf("Mais chez soi, c'est plus difficile de séparer travail et vie perso, tu ne crois pas ?", "Et la communication avec l'équipe, alors ?", "Je comprends, mais tu ne te sens pas plus isolé ?", "Bon, peut-être qu'un modèle hybride, c'est le mieux. Qu'est-ce que tu en penses ?"),
                "en" to listOf("But at home it's harder to separate work from your personal life, don't you think?", "And what about communication with the team?", "I see your point, but don't you feel more isolated?", "OK, maybe a hybrid model is best. What do you think?"),
            ),
        ),
        Scenario(
            id = "tell-a-story", title = "Tell me a story", mode = SessionMode.STORYTELLING,
            setting = "A relaxed chat where the partner asks about a memorable experience.",
            partnerRole = "a curious friend", learnerRole = "yourself",
            learnerGoal = "Tell what happened, with background and events in the right order.",
            minLevel = CefrLevel.A2, maxLevel = CefrLevel.B2, competencyIds = listOf("narrate-past"),
            openings = mapOf("es" to "¿Cuál ha sido el viaje más interesante que has hecho? Cuéntamelo todo.", "fr" to "C'est quoi le voyage le plus marquant que tu as fait ? Raconte-moi tout.", "en" to "What's the most interesting trip you've ever taken? Tell me everything."),
            offlinePrompts = mapOf(
                "es" to listOf("¿Y qué pasó después?", "¿Con quién estabas?", "¿Qué fue lo más difícil?", "¿Volverías? ¿Por qué?"),
                "fr" to listOf("Et ensuite, qu'est-ce qui s'est passé ?", "Tu étais avec qui ?", "Qu'est-ce qui a été le plus difficile ?", "Tu y retournerais ? Pourquoi ?"),
                "en" to listOf("And what happened next?", "Who were you with?", "What was the hardest part?", "Would you go back? Why?"),
            ),
        ),
    )

    val byId: Map<String, Scenario> = all.associateBy { it.id }

    /** Generic offline prompts when a scenario has none for a language. */
    val genericPrompts: Map<String, List<String>> = mapOf(
        "es" to byId.getValue("free-chat").offlinePrompts.getValue("es"),
        "fr" to byId.getValue("free-chat").offlinePrompts.getValue("fr"),
        "en" to byId.getValue("free-chat").offlinePrompts.getValue("en"),
    )

    /** Learner-created scenario; validated for length and flagged as learner-authored. */
    fun custom(id: String, title: String, setting: String, partnerRole: String, learnerGoal: String, level: CefrLevel): Scenario {
        require(title.length in 3..80 && setting.length in 3..500 && partnerRole.length in 2..80 && learnerGoal.length in 3..300) { "Scenario text length out of range" }
        return Scenario(
            id = id, title = title.trim(), mode = SessionMode.ROLE_PLAY, setting = setting.trim(), partnerRole = partnerRole.trim(),
            learnerRole = "yourself", learnerGoal = learnerGoal.trim(), minLevel = level, maxLevel = level, learnerCreated = true,
        )
    }
}
