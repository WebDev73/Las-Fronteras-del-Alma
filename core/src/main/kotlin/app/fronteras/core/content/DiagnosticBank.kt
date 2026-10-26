package app.fronteras.core.content

import app.fronteras.core.assessment.DiagnosticItem
import app.fronteras.core.assessment.DiagnosticItemKind
import app.fronteras.core.assessment.DiagnosticItemKind.GRAMMAR_CHOICE
import app.fronteras.core.assessment.DiagnosticItemKind.LISTENING_CHOICE
import app.fronteras.core.assessment.DiagnosticItemKind.OPEN_SPEAKING
import app.fronteras.core.assessment.DiagnosticItemKind.PARAPHRASE
import app.fronteras.core.assessment.DiagnosticItemKind.READING_CHOICE
import app.fronteras.core.assessment.DiagnosticItemKind.READ_ALOUD
import app.fronteras.core.assessment.DiagnosticItemKind.VOCABULARY_CHOICE
import app.fronteras.core.assessment.DiagnosticItemKind.WARM_UP_CONVERSATION

/**
 * Seed diagnostic items. Difficulty uses the CEFR-aligned scale (A1 = 1 ... C2 = 6) and
 * is an initial author estimate, to be recalibrated from response data.
 */
object DiagnosticBank {

    /** Multiple-choice helper: the correct answer is written first, then placed deterministically. */
    private fun mc(id: String, base: String, kind: DiagnosticItemKind, difficulty: Double, prompt: String,
                   correct: String, vararg wrong: String, audio: String? = null, passage: String? = null): DiagnosticItem {
        val opts = (listOf(correct) + wrong).toMutableList()
        val rotation = Math.floorMod(id.hashCode(), opts.size)
        java.util.Collections.rotate(opts, rotation)
        return DiagnosticItem(id, base, kind, difficulty, prompt, audioText = audio, passage = passage, options = opts, correctIndex = opts.indexOf(correct))
    }

    private fun open(id: String, base: String, kind: DiagnosticItemKind, difficulty: Double, prompt: String) =
        DiagnosticItem(id, base, kind, difficulty, prompt)

    val spanish: List<DiagnosticItem> = listOf(
        open("es-warm", "es", WARM_UP_CONVERSATION, 1.0, "¡Hola! ¿Cómo te llamas y de dónde eres?"),
        mc("es-v1", "es", VOCABULARY_CHOICE, 1.0, "Por la mañana bebo una ___ de café.", "taza", "mesa", "cama", "silla"),
        mc("es-v2", "es", VOCABULARY_CHOICE, 2.0, "Necesito ___ dinero del cajero automático.", "sacar", "quitar", "poner", "llevar"),
        mc("es-v3", "es", VOCABULARY_CHOICE, 3.0, "Tengo que ___ una decisión antes del viernes.", "tomar", "hacer", "poner", "dar"),
        mc("es-v4", "es", VOCABULARY_CHOICE, 4.2, "El proyecto se retrasó porque surgieron varios ___ imprevistos.", "contratiempos", "contrapesos", "contratos", "contrastes"),
        mc("es-g1", "es", GRAMMAR_CHOICE, 1.0, "Nosotros ___ estudiantes de español.", "somos", "estamos", "son", "es"),
        mc("es-g2", "es", GRAMMAR_CHOICE, 2.0, "Ayer ___ al cine con mis amigos.", "fui", "voy", "iré", "vaya"),
        mc("es-g3", "es", GRAMMAR_CHOICE, 3.0, "Espero que tú ___ bien el examen.", "hagas", "haces", "harás", "hiciste"),
        mc("es-g4", "es", GRAMMAR_CHOICE, 4.0, "Si ___ más tiempo, viajaría por todo el mundo.", "tuviera", "tendría", "tengo", "tenga"),
        mc("es-g5", "es", GRAMMAR_CHOICE, 5.0, "No es que no ___ ayudarte; es que hoy no puedo.", "quiera", "quiero", "querré", "querría"),
        mc("es-r1", "es", READING_CHOICE, 2.0, "¿Qué hace Marta después del trabajo?", "Va al gimnasio.", "Empieza a trabajar.", "Va al hospital.", "Se acuesta.",
            passage = "Marta trabaja en un hospital. Empieza a las siete de la mañana y termina a las tres. Después de trabajar, va al gimnasio."),
        mc("es-r2", "es", READING_CHOICE, 3.2, "¿Qué opinan del restaurante?", "Esperaban más de lo que recibieron.", "Les encantó la comida.", "El servicio fue muy rápido.", "No conocían sus críticas.",
            passage = "Aunque el restaurante tenía buenas críticas, la comida nos decepcionó: los platos llegaron fríos y el servicio fue lento."),
        mc("es-l1", "es", LISTENING_CHOICE, 1.0, "¿Cuántos años tiene Carlos?", "25", "35", "20", "15", audio = "Me llamo Carlos y tengo veinticinco años."),
        mc("es-l2", "es", LISTENING_CHOICE, 2.0, "¿A qué hora sale el tren?", "10:30", "4:00", "10:15", "11:30", audio = "El tren a Sevilla sale del andén cuatro a las diez y media."),
        mc("es-l3", "es", LISTENING_CHOICE, 3.0, "¿Qué propone la persona?", "Verse el jueves.", "Terminar el informe juntos.", "Quedar hoy por la tarde.", "No volver a quedar.",
            audio = "Lo siento, pero hoy no puedo quedar. Tengo que terminar un informe para mañana. ¿Te viene bien el jueves?"),
        mc("es-l4", "es", LISTENING_CHOICE, 4.2, "¿Qué decide la dirección?", "Esperar antes de contratar.", "Contratar a más personal ya.", "Reducir las ventas.", "Despedir a parte del personal.",
            audio = "A pesar de que las cifras de ventas han mejorado, la dirección prefiere ser prudente y no contratar a más personal hasta final de año."),
        open("es-o1", "es", OPEN_SPEAKING, 3.0, "Cuéntame un viaje o una experiencia que recuerdes bien. ¿Qué pasó?"),
        open("es-o2", "es", OPEN_SPEAKING, 4.0, "¿Crees que trabajar desde casa es mejor que trabajar en una oficina? ¿Por qué?"),
        open("es-p1", "es", PARAPHRASE, 3.2, "Imagina que no recuerdas la palabra «destornillador». Explica qué objeto necesitas sin usar esa palabra."),
        open("es-ra1", "es", READ_ALOUD, 2.0, "Buenos días, quería reservar una mesa para dos personas esta noche."),
    )

    val french: List<DiagnosticItem> = listOf(
        open("fr-warm", "fr", WARM_UP_CONVERSATION, 1.0, "Bonjour ! Comment tu t'appelles et d'où viens-tu ?"),
        mc("fr-v1", "fr", VOCABULARY_CHOICE, 1.0, "Le matin, je bois une ___ de café.", "tasse", "table", "chaise", "porte"),
        mc("fr-v2", "fr", VOCABULARY_CHOICE, 2.0, "Je dois ___ de l'argent au distributeur.", "retirer", "enlever", "tirer", "porter"),
        mc("fr-v3", "fr", VOCABULARY_CHOICE, 3.0, "Il faut ___ une décision avant vendredi.", "prendre", "faire", "mettre", "donner"),
        mc("fr-v4", "fr", VOCABULARY_CHOICE, 4.2, "Le projet a pris du retard à cause de plusieurs ___ imprévus.", "contretemps", "contrepoids", "contrats", "contrastes"),
        mc("fr-g1", "fr", GRAMMAR_CHOICE, 1.0, "Nous ___ étudiants.", "sommes", "avons", "sont", "êtes"),
        mc("fr-g2", "fr", GRAMMAR_CHOICE, 2.0, "Hier, je ___ allé au cinéma avec des amis.", "suis", "ai", "vais", "serai"),
        mc("fr-g3", "fr", GRAMMAR_CHOICE, 3.0, "Il faut que tu ___ tes devoirs.", "fasses", "fais", "feras", "faisais"),
        mc("fr-g4", "fr", GRAMMAR_CHOICE, 4.0, "Si j'___ plus de temps, je voyagerais partout.", "avais", "aurais", "ai", "aie"),
        mc("fr-g5", "fr", GRAMMAR_CHOICE, 5.0, "Bien qu'il ___ tard, nous avons continué à travailler.", "soit", "est", "sera", "serait"),
        mc("fr-r1", "fr", READING_CHOICE, 2.0, "Que fait Marie après le travail ?", "Elle va à la salle de sport.", "Elle commence à travailler.", "Elle va à l'hôpital.", "Elle se couche.",
            passage = "Marie travaille dans un hôpital. Elle commence à sept heures du matin et finit à quinze heures. Après le travail, elle va à la salle de sport."),
        mc("fr-r2", "fr", READING_CHOICE, 3.2, "Qu'en pensent-ils ?", "Ils s'attendaient à mieux.", "Ils ont adoré le repas.", "Le service était très rapide.", "Ils ne connaissaient pas les critiques.",
            passage = "Même si le restaurant avait de bonnes critiques, nous avons été déçus : les plats sont arrivés froids et le service était lent."),
        mc("fr-l1", "fr", LISTENING_CHOICE, 1.0, "Quel âge a Lucas ?", "25 ans", "35 ans", "20 ans", "15 ans", audio = "Je m'appelle Lucas et j'ai vingt-cinq ans."),
        mc("fr-l2", "fr", LISTENING_CHOICE, 2.0, "À quelle heure part le train ?", "10 h 30", "4 h 00", "10 h 15", "11 h 30", audio = "Le train pour Lyon part du quai quatre à dix heures et demie."),
        mc("fr-l3", "fr", LISTENING_CHOICE, 3.0, "Que propose la personne ?", "Se voir jeudi.", "Finir le rapport ensemble.", "Se voir cet après-midi.", "Ne plus se voir.",
            audio = "Désolé, je ne peux pas te voir aujourd'hui. Je dois finir un rapport pour demain. Jeudi, ça te va ?"),
        mc("fr-l4", "fr", LISTENING_CHOICE, 4.2, "Que décide la direction ?", "Attendre avant d'embaucher.", "Embaucher tout de suite.", "Réduire les ventes.", "Licencier du personnel.",
            audio = "Bien que les ventes se soient améliorées, la direction préfère rester prudente et ne pas embaucher avant la fin de l'année."),
        open("fr-o1", "fr", OPEN_SPEAKING, 3.0, "Raconte-moi un voyage ou un souvenir dont tu te souviens bien. Que s'est-il passé ?"),
        open("fr-o2", "fr", OPEN_SPEAKING, 4.0, "Penses-tu que le télétravail est mieux que le travail au bureau ? Pourquoi ?"),
        open("fr-p1", "fr", PARAPHRASE, 3.2, "Imagine que tu as oublié le mot « tournevis ». Explique quel objet tu cherches sans utiliser ce mot."),
        open("fr-ra1", "fr", READ_ALOUD, 2.0, "Bonjour, je voudrais réserver une table pour deux personnes ce soir."),
    )

    val english: List<DiagnosticItem> = listOf(
        open("en-warm", "en", WARM_UP_CONVERSATION, 1.0, "Hi! What's your name, and where are you from?"),
        mc("en-v1", "en", VOCABULARY_CHOICE, 1.0, "In the morning I drink a ___ of coffee.", "cup", "table", "chair", "door"),
        mc("en-v2", "en", VOCABULARY_CHOICE, 2.0, "I need to ___ some money from the cash machine.", "take out", "take off", "put on", "bring up"),
        mc("en-v3", "en", VOCABULARY_CHOICE, 3.0, "Could you ___ me a favour?", "do", "make", "give", "take"),
        mc("en-v4", "en", VOCABULARY_CHOICE, 4.2, "The project was delayed by several unexpected ___.", "setbacks", "setups", "takeaways", "turnouts"),
        mc("en-g1", "en", GRAMMAR_CHOICE, 1.0, "We ___ students.", "are", "is", "am", "be"),
        mc("en-g2", "en", GRAMMAR_CHOICE, 2.0, "Yesterday I ___ to the cinema with my friends.", "went", "go", "have gone", "will go"),
        mc("en-g3", "en", GRAMMAR_CHOICE, 3.0, "I've lived here ___ 2015.", "since", "for", "from", "during"),
        mc("en-g4", "en", GRAMMAR_CHOICE, 4.0, "If I ___ more time, I would travel around the world.", "had", "would have", "have", "will have"),
        mc("en-g5", "en", GRAMMAR_CHOICE, 5.0, "Not only ___ late, but he also forgot the documents.", "was he", "he was", "he is", "is he being"),
        mc("en-r1", "en", READING_CHOICE, 2.0, "What does Mary do after work?", "She goes to the gym.", "She starts work.", "She goes to the hospital.", "She goes to bed.",
            passage = "Mary works in a hospital. She starts at seven in the morning and finishes at three. After work, she goes to the gym."),
        mc("en-r2", "en", READING_CHOICE, 3.2, "What do they think of the restaurant?", "They expected more.", "They loved the food.", "The service was very fast.", "They hadn't read any reviews.",
            passage = "Although the restaurant had good reviews, the food disappointed us: the dishes arrived cold and the service was slow."),
        mc("en-l1", "en", LISTENING_CHOICE, 1.0, "How old is Lucas?", "25", "35", "20", "15", audio = "My name is Lucas and I'm twenty-five years old."),
        mc("en-l2", "en", LISTENING_CHOICE, 2.0, "When does the train leave?", "10:30", "4:00", "10:15", "11:30", audio = "The train to Manchester leaves from platform four at half past ten."),
        mc("en-l3", "en", LISTENING_CHOICE, 3.0, "What does the speaker suggest?", "Meeting on Thursday.", "Finishing the report together.", "Meeting this afternoon.", "Not meeting again.",
            audio = "Sorry, I can't meet today. I have to finish a report for tomorrow. How about Thursday?"),
        mc("en-l4", "en", LISTENING_CHOICE, 4.2, "What has management decided?", "To wait before hiring.", "To hire more staff now.", "To reduce sales.", "To let staff go.",
            audio = "Although sales have improved, management prefers to be cautious and won't hire more staff until the end of the year."),
        open("en-o1", "en", OPEN_SPEAKING, 3.0, "Tell me about a trip or an experience you remember well. What happened?"),
        open("en-o2", "en", OPEN_SPEAKING, 4.0, "Do you think working from home is better than working in an office? Why?"),
        open("en-p1", "en", PARAPHRASE, 3.2, "Imagine you've forgotten the word \"screwdriver\". Explain what you need without using that word."),
        open("en-ra1", "en", READ_ALOUD, 2.0, "Good morning, I'd like to book a table for two this evening."),
    )

    fun forBase(base: String): List<DiagnosticItem> = when (base) {
        "es" -> spanish
        "fr" -> french
        "en" -> english
        else -> emptyList()
    }
}
