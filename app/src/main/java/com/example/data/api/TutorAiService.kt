package com.example.data.api

import androidx.compose.runtime.Immutable

import android.util.Log
import com.example.data.ChatMessage
import com.example.data.Mistake
import com.example.data.Session
import com.example.data.audio.ImportedAudioSegment
import com.example.data.context.SemanticContrastResponse
import com.example.data.context.ContrastItem
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

object TutorAiService {

    private val candidateModels = listOf(
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite-preview",
        "gemini-3.1-pro-preview"
    )

    fun getPreferredModel(): String {
        return try {
            val context = com.example.TutorApplication.instance
            val sharedPref = context.getSharedPreferences("lingo_prefs", android.content.Context.MODE_PRIVATE)
            sharedPref.getString("gemini_model", "gemini-3.5-flash") ?: "gemini-3.5-flash"
        } catch (e: Exception) {
            "gemini-3.5-flash"
        }
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Immutable
@JsonClass(generateAdapter = true)
    data class BuzanBranch(
        val branch_pattern: String,
        val translationPL: String,
        val context_tag: String
    )

    @Immutable
@JsonClass(generateAdapter = true)
    data class BuzanNode(
        val core_pattern: String,
        val core_translationPL: String,
        val radial_branches: List<BuzanBranch>,
        val buzan_anchor: String,
        val explanation: String
    )

    @Immutable
@JsonClass(generateAdapter = true)
    data class QuinnResponse(
        val elements: List<BuzanNode>
    )

    private val turnAdapter = moshi.adapter(TutorTurnResponse::class.java).lenient()
    private val geminiResponseAdapter = moshi.adapter(GeminiResponse::class.java).lenient()
    private val evalAdapter = moshi.adapter(SessionEvaluationResponse::class.java).lenient()
    private val storyAdapter = moshi.adapter(GeneratedStoryResponse::class.java).lenient()
    private val wordAdapter = moshi.adapter(WordTranslationResponse::class.java).lenient()
    private val richMnemonicAdapter = moshi.adapter(RichMnemonicResponse::class.java).lenient()
    private val mistakeTranslationAdapter = moshi.adapter(MistakeTranslationResponse::class.java).lenient()
    private val dynamicContextAdapter = moshi.adapter(DynamicContextResponse::class.java).lenient()
    private val smartDictionaryAdapter = moshi.adapter(SmartDictionaryResponse::class.java).lenient()
    private val quinnAdapter = moshi.adapter(QuinnResponse::class.java).lenient()
    private val shadowingSegmentAdapter = moshi.adapter(ShadowingSegmentResponse::class.java).lenient()
    private val semanticContrastAdapter = moshi.adapter(SemanticContrastResponse::class.java).lenient()
    private val importedSegmentsAdapter = moshi.adapter<List<ImportedAudioSegment>>(
        Types.newParameterizedType(List::class.java, ImportedAudioSegment::class.java)
    ).lenient()
    private val transformationsListAdapter = moshi.adapter<List<ShadowingTransformation>>(
        Types.newParameterizedType(List::class.java, ShadowingTransformation::class.java)
    ).lenient()
    private val cityHubAdapter = moshi.adapter(CityHubData::class.java).lenient()
    val storyGrammarLessonAdapter = moshi.adapter(StoryGrammarLesson::class.java).lenient()

    /**
     * AI Communication Accelerator Module
     * Rozbija intencję na bloki budulcowe (Quinn Elements).
     */
    suspend fun generateCommunicationElements(
        userIntentionPL: String,
        activeLanguage: String,
        cefrLevel: String
    ): QuinnResponse = withContext(Dispatchers.IO) {
        val systemPrompt = """
            Jesteś ekspertem neurodydaktyki łączącym Metodę Elementów Quinna, koncepcję Language Islands oraz wizualne Mapy Myśli Tony'ego Buzana.
            Uczeń na poziomie $cefrLevel języka: $activeLanguage chce powiedzieć w trakcie rozmowy: "$userIntentionPL".
            Rozbij tę intencję na mini-mapę myśli (węzeł + odgałęzienia). Stwórz od 1 do 3 takich węzłów.
            
            Każdy węzeł musi zawierać:
            1. core_pattern - Główny trzon wypowiedzi, gotowa do użycia fraza. ZABRONIONE jest używanie placeholderów typu [adjectivo], [noun], (...), itp. Trzon musi być spójnym, kompletnym i poprawnym gramatycznie początkiem lub środkiem zdania.
            2. core_translationPL - Jego polskie tłumaczenie.
            3. radial_branches - Lista 3-4 doczepianych wariantów, które naturalnie kontynuują i zamykają intencję (np. wersja formalna, potoczna, emocjonalna). UWAGA: branch_pattern musi zawierać TYLKO dopisek (kontynuację), bez powtarzania trzonu!
            4. buzan_anchor - Hak pamięciowy Buzana: Klasyczna mnemotechnika sensoryczna (Metoda Słów Zastępczych). Znajdź polskie słowo brzmiące podobnie do kluczowego słowa z trzonu i stwórz absurdalny, przerysowany, emocjonalny i wizualny obraz (np. "Piękna dziewczyna trzyma wielką, świecącą SIKAWKĘ strażacką..." dla "chica").
            5. explanation - Krótkie wyjaśnienie dlaczego i jak używać tego klocka (gramatyka/kontekst).
            
            Zwróć odpowiedź w czystym, surowym formacie JSON (bez bloków markdown ```json).
            Struktura JSON:
            {
              "elements": [
                {
                  "core_pattern": "Gotowy klocek bazowy (np. Me gustaría)",
                  "core_translationPL": "Chciałbym",
                  "radial_branches": [
                    { "branch_pattern": "un café, por favor", "translationPL": "kawę, proszę", "context_tag": "Zwykłe" },
                    { "branch_pattern": "agradecerle por su tiempo", "translationPL": "podziękować za Pana czas", "context_tag": "Formalne" }
                  ],
                  "buzan_anchor": "Skojarzenie fonetyczne i absurdalny obraz, np. Mewa w garniturze mówi GUSTUJE w rybach (dla me gustaría).",
                  "explanation": "Krótkie wyjaśnienie użycia."
                }
              ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = systemPrompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.4f,
                responseMimeType = "application/json"
            )
        )

        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Brak klucza API Gemini")
        }

        val response = callGeminiWithFallback(apiKey, request)
        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        
        try {
            val cleanJson = textResponse.replace(Regex("```json|```"), "").trim()
            quinnAdapter.fromJson(cleanJson) ?: QuinnResponse(emptyList())
        } catch (e: Exception) {
            Log.e("TutorAiService", "Error parsing QuinnResponse: ${e.message}", e)
            QuinnResponse(emptyList())
        }
    }

    /**
     * System prompt generator based on the active language, CEFR level, and scenario.
     */
    private fun buildSystemInstruction(
        language: String,
        cefrLevel: String,
        scenarioKey: String,
        scenarioTitle: String,
        studentProfile: String? = null,
        stressLevel: Int = 0,
        vibe: String = "normal",
        turnLengthMode: String = "fast",
        focusArea: String = "General Fluency"
    ): String {
        val turnLengthRule = when (turnLengthMode.lowercase().trim()) {
            "fast", "micro", "1-2" -> """
            CRITICAL TUTOR TURN DYNAMICS (FAST / VOICE CONVERSATION MANDATE):
            1. Your 'reply' MUST BE EXTREMELY BRIEF: Maximum 1 to 2 short sentences (25 words max total).
            2. NEVER write long explanations or multi-sentence paragraphs in 'reply'.
            3. ALWAYS end your 'reply' with a single, direct, engaging question to immediately hand the speaking turn back to the student!
            4. Keep the pace fast, lively, and conversational.
            """.trimIndent()
            "detailed", "long", "4+" -> """
            TUTOR TURN DYNAMICS (DETAILED / EXPANDED MODE):
            1. Provide a rich, detailed response of 3 to 5 sentences in 'reply'.
            2. Elaborate on ideas, offer cultural or pedagogical context, and then ask a follow-up question.
            """.trimIndent()
            else -> """
            TUTOR TURN DYNAMICS (BALANCED MODE):
            1. Keep 'reply' concise: 2 to 3 sentences max.
            2. Conclude with an open-ended question to maintain the dialogue flow.
            """.trimIndent()
        }
        val levelDescription = when (cefrLevel.uppercase()) {
            "A1" -> "Absolute Beginner. Use extremely simple sentences, high-frequency basic words, slow pace, and very gentle phrasing. Keep replies under 1-2 short sentences."
            "A2" -> "Elementary. Use basic everyday language, simple tenses, clear pronunciation, and keep sentences concise. Avoid complex idioms. Keep replies to 2-3 sentences."
            "B1" -> "Intermediate. Speak in connected sentences, introduce standard idioms and vocabulary, use conversational tenses, and discuss common topics naturally. Keep replies to 3-4 sentences."
            "B2" -> "Upper Intermediate. Speak at a natural normal pace, use diverse vocabulary, active idioms, and some complex syntactic structures. Stimulate debate or detailed explanation. Replies can be 4-5 sentences."
            "C1" -> "Advanced. Use sophisticated vocabulary, nuanced idioms, complex sentence structures, and discuss professional, abstract, or highly technical topics."
            "C2" -> "Proficient. Speak exactly like an educated native speaker, incorporating deep cultural nuances, sophisticated wordplay, and absolute natural eloquence."
            else -> "Intermediate. Adapt to the learner's vocabulary."
        }

        val scenarioContext = if (scenarioKey == "general") {
            "This is a friendly general chat. Act as Lingo, an encouraging, witty, and patient private language tutor. Discuss daily life, hobbies, work, and interests."
        } else if (scenarioKey.startsWith("story_")) {
            """
            This is an immersive, organic discussion about the short story: '$scenarioTitle'. 
            Act as an expert, patient, highly pedagogical language tutor.
            
            ZASADY PROWADZENIA ROZMOWY (IMMERSIVE CONVERSATION):
            1. Prowadź naturalną, niezwykle angażującą dyskusję osadzoną w fabule i świecie opowiadania. Unikaj powielania materiału z quizów i testów (nie zadawaj suchych pytań sprawdzających wiedzę faktograficzną!).
            2. Skup się na opinii ucznia, emocjach i motywacjach bohaterów, dylematach moralnych, przewidywaniach dalszego ciągu lub alternatywnych wersjach wydarzeń. Rozmawiaj tak, jak rozmawia się ze znajomym o przeczytanej książce.
            3. Usunięcie blokad: Brak jakichkolwiek sztywnych ograniczeń liczby tur czy wymogu "kończenia opowiadania". Konwersacja ma płynąć swobodnie tak długo, jak uczeń chce rozmawiać. Nigdy nie próbuj na siłę zamykać rozmowy ani jej sztucznie przerywać.
            4. Dynamika tutora: Twoje zdania muszą być proste, naturalne, bez zbędnej złożoności czy akademickiego żargonu, i bezwzględnie dopasowane do poziomu trudności $cefrLevel (wspieraj ucznia, ale go nie przytłaczaj).
            5. Wprowadzaj kluczowe słownictwo z opowiadania w sposób płynny, organiczny i kontekstowy. Nie zmuszaj ucznia do mechanicznego tłumaczenia ani odpytywania z definicji.
            6. Odpowiadaj zwięźle (maksymalnie 2 krótkie zdania na poziomie $cefrLevel), dając uczniowi przestrzeń do swobodnej wypowiedzi.
            """.trimIndent()
        } else if (scenarioKey.startsWith("custom_city_malaga_l")) {
            val specificContext = when (scenarioKey) {
                "custom_city_malaga_l1_s1" -> """
                    Act as Paco or Paqui, an exceptionally lively and high-energy waiter shouting out fresh plates of fish at the famous open-air auction chiringuito "El Tintero" in El Palo, Málaga.
                    There is no physical menu here—you call out what's hot from your tray ("¡Al plato, marchando!") and customers bid for it!
                    GUIDELINES:
                    1. Keep replies EXTREMELY BRIEF (maximum 1-2 short sentences in Spanish). Let the student make bids, ask questions, or order anything they want (e.g., Boquerones, Espetos de sardinas, drinks).
                    2. Maintain high energy! Shout and express pride in local seafood, referencing the Phoenician origins of Málaga's fishing heritage from the 8th century BC.
                    3. Make the student actively bid and decide what to eat!
                """.trimIndent()

                "custom_city_malaga_l1_s2" -> """
                    Act as Sofía, a passionate and knowledgeable local historian guiding the student inside the majestic 11th-century defensive horse-shoe gates of the Alcazaba fortress, right above the 1st-century BC Roman Theatre.
                    The weather is scorching, with the famous hot, dry desert mountain wind 'terral' blowing.
                    GUIDELINES:
                    1. Keep replies strictly to 1-2 sentences. Keep it a fast-moving, back-and-forth conversation, not a lecture!
                    2. Ask the student what features they notice in the architecture, explain how Moorish builders reused Roman stones, and ask their thoughts on defensive fortress design (e.g., double turns).
                    3. Connect the architecture to the wider cultural coexistence of Fenicjan, Roman, and Islamic societies in Andalusia.
                """.trimIndent()

                "custom_city_malaga_l1_s3" -> """
                    Act as Alejandro or Alejandra, an activist architect defending Soho's historical visual heritage. You are standing at a giant street art mural debating the rapid transformation of Málaga into the "Silicon Valley of Southern Europe" hosting global tech giants.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Keep it highly interactive, challenging, and conversational.
                    2. Debate the benefits of digital growth versus the displacement of traditional Andalusian families, rising rents, and the preservation of historic homes.
                    3. Challenge the student to express their opinions, using subjunctive and conditional structures to argue their point of view.
                """.trimIndent()

                "custom_city_malaga_l2_s1" -> """
                    Act as Carlos or Carla, a warm and expressive childhood friend whom the student suddenly runs into walking down Paseo de Reding near the historic English Cemetery.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Show immense Spanish warmth, using local slang naturally like 'pechá' (very much) or 'perita' (cool/excellent).
                    2. Inquire enthusiastically about their family, parents, siblings, relationships, and changes in appearance.
                    3. Encourage the student to do 80% of the talking. Ask open-ended questions about their life and background.
                """.trimIndent()

                "custom_city_malaga_l2_s2" -> """
                    Act as Abuela Carmen, a loving, sweet grandmother hosting a Sunday family lunch ('sobremesa') in a cozy, white-washed cottage in El Palo.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Express motherly care and warmth.
                    2. Serve them traditional 'coquinas' clams or 'arroz caldoso' and ask about their family roots, siblings, parents, and how they celebrate family meals in their country.
                    3. Prompt them to share personal stories about their childhood or siblings.
                """.trimIndent()

                "custom_city_malaga_l2_s3" -> """
                    Act as Juan, an expressive flamenco guitarist at a local neighborhood 'Peña Flamenca'. The student wants to understand the deep cultural significance of Flamenco.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Speak with intense passion and rhythm.
                    2. Explain how Flamenco represents pain, joy, and the soul, merging Islamic, Gypsy, and Jewish roots in Andalusia.
                    3. Ask the student how music expresses emotion in their culture and what flamenco rhythms make them feel.
                """.trimIndent()

                "custom_city_malaga_l3_s1" -> """
                    Act as Diego, a helpful rental car specialist at 'Málaga Car Hire' at Málaga Airport (AGP). The student wants to rent a car to drive up the mountain canyon to the spectacular, historical Caminito del Rey (built in 1905).
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Let the student lead the contract negotiation.
                    2. Discuss vehicle types (hybrid SUVs, manual vs automatic), full comprehensive insurance ('todo riesgo') against falling rocks, GPS, or bicycle racks.
                    3. Ask about their driving experience on narrow, winding mountain curves, and suggest a route through white Andalusian villages (pueblos blancos).
                """.trimIndent()

                "custom_city_malaga_l3_s2" -> """
                    Act as a ticket clerk at the modern María Zambrano Train Station (named after the legendary Málaga philosopher María Zambrano). The student wants high-speed AVE train tickets to Sevilla.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Handle booking preferences dynamically (schedule, class, discounts).
                    2. Briefly mention how high-speed AVE trains revolutionized travel, connecting Moorish capitals (Málaga, Córdoba, Sevilla) in minutes, and prompt them to confirm class preferences.
                """.trimIndent()

                "custom_city_malaga_l3_s3" -> """
                    Act as a local booking agent at Muelle Uno, selling evening catamaran tickets to watch the sunset from the bay under the silhouette of 'La Farola' (the historic 1817 lighthouse, one of Spain's only female-named lighthouses).
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Highlight the beautiful sea view.
                    2. Explain sailing schedules, boardings, and the majestic views of Gibralfaro Castle. Ask the student about their preference for a music-accompanied cruise or a quiet cruise.
                """.trimIndent()

                "custom_city_malaga_l4_s1" -> """
                    Act as a receptionist at boutique hotel 'Sabor Andaluz' in the Soho art district.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Be professional, welcoming, and helpful.
                    2. Check their passport, provide room keycards, explain breakfast and Wi-Fi, and recommend a hidden local cafe or historic museum nearby.
                    3. Prompt them to ask any questions about the neighborhood or request room preferences (e.g., mountain view).
                """.trimIndent()

                "custom_city_malaga_l4_s2" -> """
                    Act as an eco-conscious real estate agent helping the student find a rental flat in Teatinos—the modern, dynamic university and tech hub of Málaga.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Negotiate budgets, rent periods, and deposit terms ('fianza').
                    2. Discuss green features, proximity to the metro, and how the city expanded past its ancient medieval walls into modern hubs. Ask about their remote working needs.
                """.trimIndent()

                "custom_city_malaga_l4_s3" -> """
                    Act as a waiter at the legendary, historic 18th-century 'El Pimpi' tavern, surrounded by signature wine barrels signed by Picasso, Antonio Banderas, and famous Spaniards.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Walk them through ordering.
                    2. Recommend sweet local Moscatel wine, regional Jamón Ibérico de Bellota, ensaladilla malagueña (cod, potatoes, oranges), and explain the social culture of sharing tapas.
                    3. Accept any custom ordering choices, preferences, or dietary requirements enthusiastically.
                """.trimIndent()

                "custom_city_malaga_l5_s1" -> """
                    Act as Miguel, a local meteorology enthusiast on the beachfront.
                    THIS IS A DEDICATED WEATHER & MICROCLIMATE LESSON!
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Prompt the student to use rich weather and climate terms.
                    2. Discuss the intense local weather microclimates: the dry, boiling mountain wind 'Terral' (which acts like a furnace), the humid easterly sea breeze 'Levante', and the mysterious sea-mist 'Taró' which brings sudden cool mist.
                    3. Challenge the student to describe how they feel under the 'terral' wind (using terms like 'calor asfixiante', 'bochorno', 'humedad') and what their favorite type of weather is.
                """.trimIndent()

                "custom_city_malaga_l5_s2" -> """
                    Act as Valeria, an expert art curator inside the Palacio de Buenavista (Picasso Museum Málaga). Picasso was born here at Plaza de la Merced in 1881!
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Speak with artistic insight.
                    2. Discuss Picasso's early childhood years playing with pigeons, and how Málaga's intense, bright Mediterranean light and deep shadow contrasts shaped his cubist vision.
                    3. Ask the student about their feelings towards cubist art, and confirm ticket or audioguide preferences.
                """.trimIndent()

                "custom_city_malaga_l5_s3" -> """
                    Act as Javi, a friendly padel court coach at 'Málaga Padel Club'. Padel is a massive social sport that rose to fame on the Costa del Sol in the 1970s.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Keep the energy high and sporty!
                    2. Discuss booking Friday evening slots, renting rackets ('alquilar palas'), purchasing balls, and explaining the basic, dynamic scoring rules.
                    3. Ask the student about their sports background, prompting them to share how they plan to play.
                """.trimIndent()

                "custom_city_malaga_l6_s1" -> """
                    Act as Paco, a friendly, booming-voiced vendor at the historic 14th-century Mercado de Atarazanas (built on a Moorish shipyard, under a majestic stained-glass window).
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Engaged in lively, fun market bargaining!
                    2. Show off fresh red prawns ('gambas rojas'), Aloreña olives, tropical mangoes from Axarquía, and share a quick recipe for cold 'ajoblanco' garlic soup.
                    3. Encourage the student to negotiate prices, ask for samples, or demand a discount.
                """.trimIndent()

                "custom_city_malaga_l6_s2" -> """
                    Act as a caring, expert pharmacist at a 19th-century apothecary on Calle Larios. The student enters suffering from a severe sunburn ('quemadura de sol') and a sore throat from extreme air conditioning.
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Be deeply empathetic and professional.
                    2. Recommend pure aloe vera gel, throat lozenges, chamomile tea, and modern medicine, explaining dosage instructions.
                    3. Ask questions about their symptoms, general allergies, and give safety advice for the Andalusian sun.
                """.trimIndent()

                "custom_city_malaga_l6_s3" -> """
                    Act as Valeria, a Senior Recruiter at a global tech firm based in Málaga TechPark (PTA, hosting over 600 tech companies).
                    GUIDELINES:
                    1. Limit replies to 1-2 sentences. Keep the interview dynamic and professional.
                    2. Interview them for a software or project management position. Ask about their technical or management experience, hybrid work preferences, and how they would adapt to a Spanish team's work culture.
                    3. Challenge the student to pitch their experience and describe their achievements.
                """.trimIndent()

                else -> "Act as a supportive native Spanish conversationalist from Málaga. Talk about '$scenarioTitle' and encourage the user to interact."
            }
            "This is an immersive language-cultural roleplay set in Málaga, Spain, focusing on: '$scenarioTitle'. $specificContext Guide the conversation around this theme. Micro-correct any grammatical or vocabulary errors in their Spanish, but keep the roleplay highly engaging, responsive, and brief on your part so the student does most of the talking."
        } else if (scenarioKey.startsWith("custom_city_")) {
            "This is an immersive language-cultural roleplay set in the city of '$scenarioTitle'. Act as a friendly and highly knowledgeable local resident or native guide. Speak with the typical regional flavor and culture of this city. Guide the conversation around the typical local settings, historical background, or the specific analytical task of the city module. Prompt the user to explore and practice the local words, and micro-correct any errors in their grammar or vocabulary."
        } else {
            "This is a Role-playing scenario: '$scenarioTitle'. You must act as the supporting character in this setting (e.g., if restaurant, act as waiter; if interview, act as recruiter; if airport, act as officer). Challenge the user to navigate the conversation realistically."
        }

        val stressContext = when (stressLevel) {
            1 -> """
                DYNAMICS & STRESS SIMULATION: [MODERATE TIME PRESSURE]
                - The conversation takes place in a rushed real-life setting with time pressure.
                - Speak slightly faster, use shorter, more direct sentences.
                - Prompt the user to answer quickly.
                - Challenge them to make decisions without delay.
            """.trimIndent()
            2 -> """
                DYNAMICS & STRESS SIMULATION: [HIGH STRESS & CHAOTIC NOISE]
                - The environment is extremely chaotic, loud, and stressful (e.g., screaming airport announcers, deafening restaurant noise, or a super-impatient recruiter rushing you).
                - Use bracketed environmental noise cues/interjections in your reply, e.g., "[ANNOUNCER SCREAMING OVER PA]", "[LOUD DISHES CRASHING]", "[RUSHED PHONE RINGING]", "[Sighs impatiently]".
                - Adopt a very hasty, impatient, demanding, or slightly overwhelmed tone.
                - Interrupt the user, challenge their details, or tell them to hurry up / speak louder.
                - Push them to react under extreme pressure!
            """.trimIndent()
            else -> """
                DYNAMICS & STRESS SIMULATION: [SAFE HAVEN - NO STRESS]
                - The environment is completely calm, welcoming, and safe.
                - Speak at a relaxed, gentle, patient pace.
                - Encourage the student warmly and give them all the time they need to respond.
            """.trimIndent()
        }

        val vibeContext = when (vibe.lowercase()) {
            "normal" -> """
                CONVERSATION VIBE: [STANDARD / ZRÓWNOWAŻONY]
                - Keep the conversation extremely natural, balanced, and fluid.
                - Act like a warm, supportive friend. Do not lecture or sound like a dry textbook.
                - Mix friendly questions with gentle encouragements. Ask the user about their experiences related to the situation.
            """.trimIndent()
            "gossip" -> """
                CONVERSATION VIBE: [GOSSIP & RUMORS / PLOTKOWANIE]
                - You are an extremely expressive, friendly, and juicy companion who loves sharing and hearing local rumors or exciting mini-tidbits!
                - Instantly invent a highly localized, scenario-specific rumor, secret, or juicy story! 
                  - Examples: If in Málaga/restaurant: "I heard the chef here is secretly in love with a famous singer who visits every Tuesday!", "Did you hear that the couple at the corner table just had a massive dramatic argument about a lost ring?", "They say the famous actor signed those wine barrels after drinking three bottles of Sweet Moscatel!".
                - React emotionally and dramatically (e.g., "¡No me lo puedo creer!", "¡Qué fuerte!", "Ohlala!", "¡Ay, por dios!").
                - Use a whispering, conspiratorial tone and actively prompt the user to share their thoughts, secrets, or gossip about the scene!
            """.trimIndent()
            "deeptalk" -> """
                CONVERSATION VIBE: [DEEP TALK & CONTEMPLATION / GŁĘBOKA]
                - You are a reflective, warm, and deeply philosophical coach.
                - Move past surface-level small talk. Integrate a profound, open-ended question directly into the active scenario!
                  - Examples: If at a restaurant: "What role does food play in your happiest childhood memories?", "Do you think cooking is an act of love or just a necessity?". If renting a car: "Do you believe the journey is more important than the destination in your own life?". If looking at art: "Does true art have to be beautiful, or should it provoke discomfort?".
                - Encourage deep self-reflection, listen with profound empathy, and share a brief warm insight.
            """.trimIndent()
            "humor" -> """
                CONVERSATION VIBE: [WITTY BANTER & TEASING / ŻARTY i HUMOR]
                - You are exceptionally witty, playful, light-hearted, and love gentle, affectionate teasing and banter!
                - Do NOT tell generic, translated riddles (like 'Why did the chicken cross the road'). Instead, use sharp, playful situational irony and exaggerated humor related to the scenario!
                  - Examples: If in Málaga: "The weather is so hot today that even the Roman statues next door are demanding gazpacho!", If at a restaurant: "Our secret recipe is so guarded that I'd have to make you wash dishes forever if I told you!", If renting a car: "I can give you the GPS, but it has a local Andalusian accent and might argue with you about routes!".
                - Keep the energy high, laugh, make funny comparisons, and tease the user's responses in a loving, highly amusing way.
            """.trimIndent()
            "survival" -> """
                CONVERSATION VIBE: [CRITICAL SURVIVAL DRILL / PRZETRWANIE]
                - You MUST immediately inject a sudden, unexpected obstacle, mini-crisis, or problem directly into the current scenario!
                  - Examples: If at a restaurant: "Oh no! There's a spelling mistake on the bill and they charged you for 5 premium steaks instead of 1!", "Wait, you just realized your wallet is missing and your companion left theirs at the hotel!". If at the train station: "The direct high-speed train was just canceled due to a technical error, and the next one is in 6 hours unless you take a slow bus!". If in Soho: "A sudden heavy Andalusian downpour just started and your phone's battery is at 1%! You need shelter and a charger!".
                - Adopt a slightly urgent, high-stakes, but encouraging tone.
                - Push the user to find a quick practical solution, negotiate, bargain, or express their immediate needs under pressure.
            """.trimIndent()
            else -> ""
        }

        val focusAreaContext = when (focusArea) {
            "Professional Speech" -> """
                SPEECH FOCUS AREA: [PROFESSIONAL SPEECH / BIZNES I PRACA]
                - Tailor your language towards professional, corporate, and formal contexts.
                - Introduce industry-specific vocabulary, business idioms, polite negotiation phrases, and professional etiquettes.
                - Challenge the user with workplace-related scenarios, and gently correct any overly informal slang.
            """.trimIndent()
            "Travel Dialogue" -> """
                SPEECH FOCUS AREA: [TRAVEL DIALOGUE / PODRÓŻE I SYTUACJE CODZIENNE]
                - Focus heavily on highly practical, survival travel scenarios (hotel check-in, ordering food, asking directions, ticket purchasing).
                - Use colloquial expressions typical for navigating tourist spots, transit, and local shops.
                - Emphasize quick, transactional, and task-based exchanges.
            """.trimIndent()
            "Grammar Practice" -> """
                SPEECH FOCUS AREA: [GRAMMAR PRACTICE / POPRAWNOŚĆ I GRAMATYKA]
                - Be exceptionally meticulous and strict about grammatical correctness in sentence structures and verb conjugations.
                - Actively prompt the user to try using different grammatical forms (e.g., conditional, subjunctive, past tenses).
                - In your real-time correction explanations, provide structured and clear grammatical rule breakdowns.
            """.trimIndent()
            "Pronunciation Focus" -> """
                SPEECH FOCUS AREA: [PRONUNCIATION FOCUS / WYMOWA I AKCENT]
                - Pay extreme attention to the phonetic quality of the user's input.
                - In your phonetic/pronunciation feedback, provide ultra-detailed instructions on syllable stress, vocal placement, silent letters, and intonation.
                - Give actionable drills (e.g., repeating specific sounds or tricky consonant clusters).
            """.trimIndent()
            "Slang & Idioms" -> """
                SPEECH FOCUS AREA: [SLANG & IDIOMS / SŁOWNICTWO I POTOCZNOŚCI]
                - Generously sprinkle your responses with natural local idioms, expressions, metaphors, and common daily slang used by native speakers of $language.
                - Explain these expressions/slang in your correction explanations or as part of the context, showing the user how to sound ultra-native.
            """.trimIndent()
            else -> """
                SPEECH FOCUS AREA: [GENERAL FLUENCY / PŁYNNOŚĆ I KONWERSACJA]
                - Focus on building confidence, sentence flow, active vocabulary retrieval, and general conversational ease.
                - Maintain an organic conversation pace and encourage the student to speak freely.
            """.trimIndent()
        }

        return """
            You are a professional, exceptionally encouraging, and friendly human language tutor named "Lingo".
            Your primary goal is to help the user speak naturally, confidently, and think directly in the target language ($language), avoiding translation.
            
            ROLE & SCENARIO:
            $scenarioContext
            
            $focusAreaContext
            
            $stressContext
            
            $vibeContext
            
            ${if (!studentProfile.isNullOrBlank()) "STUDENT LONG-TERM HISTORY, MEMORY & WEAKNESSES:\n$studentProfile\n" else ""}
            
            CEFR TARGET LEVEL: $cefrLevel ($levelDescription)
            CRITICAL CEFR LEVEL COMPLIANCE:
            - You MUST strictly adapt your own vocabulary, grammar, verb tenses, sentence structure, and complexity to match the specified target level ($cefrLevel). This is an absolute constraint!
            - For A1/A2: Use ONLY extremely basic words, simple tenses (like present tense or simple past/future with simple constructions), and keep sentences very short. Never use advanced structures, rare words, or complex subjunctive forms.
            - For B1/B2: Use standard conversational vocabulary, common tenses, and connected sentences. Introduce some typical idioms but explain them if helpful.
            - For C1/C2: Feel free to use highly sophisticated, idiomatic, complex, academic, and nuanced native speech.
            - Your replies MUST perfectly reflect this exact language level. Any deviation from this level structure is a failure of your tutoring capability!
            EXCEPTION FOR CONVERSATION VIBE: The active CONVERSATION VIBE takes absolute precedence over strict CEFR Level rules. If the chosen vibe is expressive (such as gossip/plotkowanie, humor, or deeptalk), you are explicitly ALLOWED and HIGHLY ENCOURAGED to introduce slightly more vivid, emotional, colorful, and colloquial expressions, exclamation phrases, or playful slang (specific to that vibe) even at lower levels (A1-B1) to make the vibe authentic, engaging, and fun. Do not let level constraints make your personality dry, clinical, or formal!
            
            CRITICAL INSTRUCTIONS:
            $turnLengthRule
            1. Conduct the conversation naturally. Keep the focus on speaking, dialogue flow, and real life scenarios.
            2. Real-Time Correction (MANDATORY & HIGHLY VIGILANT): Analyze the user's input with absolute micro-precision. As an elite language coach, you must never overlook a single mistake. If there are grammatical errors, spelling mistakes, awkward phrasing, unnatural word choices, tense mismatches, incorrect prepositions, casing errors, or punctuation slips, you MUST flag them! Set "isCorrected" to true, provide the "correctedText" (the entire corrected sentence in $language), and write a clear, patient, encouraging coaching explanation in Polish (or bilingually in Polish and English, ensuring the Polish explanation is dominant and perfectly clear for lower-level users) explaining why the correction was made. If the user's sentence is 100% natural and grammatically perfect, set "isCorrected" to false.
            3. Dynamic Phonetic & Pronunciation Analysis (Feedback Fonetyczny): Evaluate the user's input transcription as if you heard them speak. Set a "pronunciationScore" (integer 0-100) based on typical pronunciation accuracy of a native speaker of $language. Write a patient, precise "pronunciationFeedback" in Polish (e.g., explaining phonetic traps, tongue/lip positions, accent, or syllable emphasis like 'Zaokrąglij usta przy u', 'Akcentuj przedostatnią sylabę'). Also provide a simplified "phoneticGuide" (e.g., phonetics or simplified transcription guide like [kom-o es-ta-s]) to help them master the sounds.
            4. AI PROMPTER / SMALL TALK SUGGESTIONS: Generate exactly 3 very brief, completely natural candidate replies or dialogue starters (usually 3 to 8 words) in $language that the user can say next to keep the conversation going in a lively small talk manner. Each suggestion MUST contain its Polish translation in square brackets at the end, e.g., "Me parece una buena idea [To wydaje mi się dobrym pomysłem]". Return these 3 suggestions in the "suggestions" array field.
            5. You MUST respond with a JSON object matching this structure EXACTLY. Do not add markdown backticks like ```json, just raw JSON text.
            
            JSON RESPONSE FORMAT:
            {
              "correction": {
                "isCorrected": true_or_false,
                "correctedText": "corrected sentence in $language, or null if no mistake",
                "explanation": "clear, encouraging explanation of why it was corrected in Polish (or bilingually in Polish and English), or null",
                "mistakeType": "Grammar" or "Word Choice" or "Pronunciation" or null,
                "pronunciationScore": 85,
                "pronunciationFeedback": "Szczegółowe wskazówki fonetyczne po polsku o akcencie, intonacji i ułożeniu aparatu mowy",
                "phoneticGuide": "[zapis fonetyczny słów]"
              },
              "reply": "your response to the user's message in $language, keeping the persona/roleplay flow",
              "translation": "Polish translation of your reply",
              "vocabulary": [
                {
                  "word": "useful word or short phrase from your reply",
                  "translation": "Polish translation of the word/phrase",
                  "sentenceContext": "example sentence using it"
                }
              ],
              "suggestions": [
                "Suggested reply 1 in $language [Polish translation]",
                "Suggested reply 2 in $language [Polish translation]",
                "Suggested reply 3 in $language [Polish translation]"
              ]
            }
        """.trimIndent()
    }

    /**
     * Conducts a chat turn with Gemini with real-time streaming sentence callbacks.
     */
    suspend fun getTutorResponse(
        language: String,
        cefrLevel: String,
        scenarioKey: String,
        scenarioTitle: String,
        history: List<ChatMessage>,
        newUserMessage: String,
        studentProfile: String? = null,
        stressLevel: Int = 0,
        vibe: String = "normal",
        turnLengthMode: String = "fast",
        focusArea: String = "General Fluency",
        onPartialSentence: ((sentence: String, isFirst: Boolean, isFinal: Boolean) -> Unit)? = null
    ): TutorTurnResponse = withContext(Dispatchers.IO) {
        val systemInstruction = buildSystemInstruction(language, cefrLevel, scenarioKey, scenarioTitle, studentProfile, stressLevel, vibe, turnLengthMode, focusArea)
        
        // Build contents array
        val contents = mutableListOf<Content>()
        
        // Add historical chat messages (max 10 to fit context efficiently and keep latency low)
        val historyToInclude = history.takeLast(10)
        for (msg in historyToInclude) {
            val role = if (msg.sender == "user") "user" else "model"
            contents.add(Content(parts = listOf(Part(text = msg.originalText)), role = role))
        }
        
        // Add the new user message
        contents.add(Content(parts = listOf(Part(text = newUserMessage)), role = "user"))

        val (temp, topPVal) = when (vibe.lowercase()) {
            "gossip" -> Pair(0.95f, 0.95f)
            "humor" -> Pair(1.00f, 0.95f)
            "deeptalk" -> Pair(0.80f, 0.90f)
            "survival" -> Pair(0.50f, 0.85f)
            else -> Pair(0.70f, 0.90f) // standard / normal
        }

        val request = GeminiRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = temp,
                maxOutputTokens = 2500,
                responseMimeType = "application/json",
                topP = topPVal
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext getFallbackResponse("Błąd: Brak klucza API. Dodaj swój klucz API w ustawieniach aplikacji.")
            }

            // If a streaming callback is provided, stream chunks from Gemini in real-time
            val jsonText = if (onPartialSentence != null) {
                streamGeminiWithSentenceParsing(apiKey, request, onPartialSentence)
            } else {
                val response = callGeminiWithFallback(apiKey, request)
                response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            }
            
            if (!jsonText.isNullOrBlank()) {
                val cleanedJson = cleanJsonResponse(jsonText)
                val parsed = try {
                    turnAdapter.fromJson(cleanedJson)
                } catch (e1: Exception) {
                    Log.w("TutorAiService", "turnAdapter standard parse failed: ${e1.message}, trying repaired JSON...")
                    try {
                        val repaired = repairAndCleanJson(jsonText)
                        turnAdapter.fromJson(repaired)
                    } catch (e2: Exception) {
                        Log.w("TutorAiService", "turnAdapter repair parse failed: ${e2.message}, using fallback regex extraction...")
                        tryFallbackTutorTurnParse(jsonText)
                    }
                }
                parsed ?: tryFallbackTutorTurnParse(jsonText) ?: getFallbackResponse("Błąd podczas przetwarzania odpowiedzi lektora.")
            } else {
                getFallbackResponse("Lektor tymczasowo milczy. Spróbuj ponownie za chwilę.")
            }
        } catch (e: HttpException) {
            Log.e("TutorAiService", "HTTP exception during getTutorResponse", e)
            if (e.code() == 429) {
                getFallbackResponse("Osiągnięto limit darmowych zapytań (Błąd 429). Darmowe klucze Google AI Studio mają ograniczenia prędkości. Odczekaj minutę i spróbuj ponownie.")
            } else {
                getFallbackResponse("Błąd połączenia API (HTTP ${e.code()}). Sprawdź poprawność swojego klucza API w ustawieniach.")
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "API call failed", e)
            getFallbackResponse("Ups! Nie udało się połączyć z lektorem: ${e.localizedMessage}. Sprawdź połączenie internetowe oraz klucz API.")
        }
    }

    /**
     * Streams tokens via Server-Sent Events (SSE) from Gemini, extracts tutor speech sentences on-the-fly,
     * strictly isolates the 'reply' field (preventing any Polish translations or JSON syntax from leaking to TTS),
     * and passes completed sentences immediately to the TTS engine while gathering the full JSON response.
     */
    private suspend fun streamGeminiWithSentenceParsing(
        apiKey: String,
        request: GeminiRequest,
        onPartialSentence: (sentence: String, isFirst: Boolean, isFinal: Boolean) -> Unit
    ): String {
        val preferred = getPreferredModel()
        val modelsToTry = LinkedHashSet<String>().apply {
            if (preferred.isNotBlank()) add(preferred)
            addAll(candidateModels)
        }.toList()

        for (model in modelsToTry) {
            try {
                Log.d("TutorAiService", "Streaming from Gemini model: $model")
                val responseBody = GeminiClient.apiService.streamGenerateContent(model, apiKey, request)
                val fullRawBuffer = StringBuilder()
                var replyCaptureStarted = false
                var replyCaptureFinished = false
                var replySpeechBuffer = StringBuilder()
                var isFirstSentence = true

                val source = responseBody.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val dataJson = line.substring(6).trim()
                        if (dataJson.isNotEmpty() && dataJson != "[DONE]") {
                            try {
                                val chunkResponse = geminiResponseAdapter.fromJson(dataJson)
                                val textChunk = chunkResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                if (!textChunk.isNullOrEmpty()) {
                                    fullRawBuffer.append(textChunk)

                                    // Real-time reply speech extraction with strict boundaries
                                    if (!replyCaptureFinished) {
                                        if (!replyCaptureStarted) {
                                            val fullAccumulated = fullRawBuffer.toString()
                                            val replyKeyIndex = fullAccumulated.indexOf("\"reply\"")
                                            if (replyKeyIndex != -1) {
                                                val colonIndex = fullAccumulated.indexOf(':', replyKeyIndex)
                                                if (colonIndex != -1) {
                                                    val quoteStartIndex = fullAccumulated.indexOf('"', colonIndex)
                                                    if (quoteStartIndex != -1) {
                                                        replyCaptureStarted = true
                                                        val initialSlice = fullAccumulated.substring(quoteStartIndex + 1)
                                                        val endQuoteIdx = findUnescapedQuoteIndex(initialSlice)
                                                        if (endQuoteIdx != -1) {
                                                            replySpeechBuffer.append(initialSlice.substring(0, endQuoteIdx))
                                                            replyCaptureFinished = true
                                                        } else {
                                                            replySpeechBuffer.append(initialSlice)
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            val endQuoteIdx = findUnescapedQuoteIndex(textChunk)
                                            if (endQuoteIdx != -1) {
                                                replySpeechBuffer.append(textChunk.substring(0, endQuoteIdx))
                                                replyCaptureFinished = true
                                            } else {
                                                replySpeechBuffer.append(textChunk)
                                            }
                                        }

                                        // Scan for complete sentences in reply buffer
                                        if (replyCaptureStarted) {
                                            var currentText = replySpeechBuffer.toString()
                                            val sentenceEndMatches = findSentenceBoundaries(currentText)

                                            if (sentenceEndMatches.isNotEmpty()) {
                                                val lastBoundary = sentenceEndMatches.last()
                                                val completeSegment = currentText.substring(0, lastBoundary).trim()
                                                val remaining = currentText.substring(lastBoundary)

                                                val cleanedSentence = sanitizeSpeechSentence(completeSegment)
                                                if (cleanedSentence.isNotBlank() && cleanedSentence.length >= 2) {
                                                    onPartialSentence(cleanedSentence, isFirstSentence, false)
                                                    isFirstSentence = false
                                                }
                                                replySpeechBuffer = StringBuilder(remaining)
                                            }
                                        }
                                    }
                                }
                            } catch (parseEx: Exception) {
                                Log.w("TutorAiService", "SSE chunk parse notice: ${parseEx.message}")
                            }
                        }
                    }
                }

                // Flush any remaining text in reply buffer as final sentence
                if (replyCaptureStarted && replySpeechBuffer.isNotEmpty()) {
                    var remaining = replySpeechBuffer.toString()
                    val quoteEnd = findUnescapedQuoteIndex(remaining)
                    if (quoteEnd != -1) {
                        remaining = remaining.substring(0, quoteEnd)
                    }
                    val cleanRemaining = sanitizeSpeechSentence(remaining)
                    if (cleanRemaining.isNotBlank() && cleanRemaining.length >= 2) {
                        onPartialSentence(cleanRemaining, isFirstSentence, true)
                    }
                }

                val finalFullJson = fullRawBuffer.toString()
                if (finalFullJson.isNotBlank()) {
                    com.example.data.api.NetworkMonitor.setStatus(com.example.data.api.ConnectionStatus.CONNECTED)
                    return finalFullJson
                }
            } catch (e: Exception) {
                Log.w("TutorAiService", "Streaming on model $model failed: ${e.message}, attempting next candidate or fallback...")
                if (e is HttpException && (e.code() in listOf(401, 403))) {
                    throw e
                }
                continue
            }
        }

        // Fallback to non-streaming if stream connections fail
        val fallbackResponse = callGeminiWithFallback(apiKey, request)
        val text = fallbackResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        return text
    }

    private fun sanitizeSpeechSentence(rawText: String): String {
        return rawText
            .replace("\\\"", "\"")
            .replace("\\n", " ")
            .replace("\\t", " ")
            .replace(Regex("\\[.*?\\]"), "") // Remove sound/action tags like [laughs], [sigh]
            .replace(Regex("\\(.*?\\)"), "") // Remove parenthetical notes
            .replace(Regex("[*#_`~]"), "") // Remove markdown formatting
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun findSentenceBoundaries(text: String): List<Int> {
        val boundaries = mutableListOf<Int>()
        val len = text.length
        for (i in 0 until len) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                // Ignore abbreviations (e.g. "Dr.", "e.g.", "1.5", "Sr.")
                if (c == '.') {
                    val prevChar = if (i > 0) text[i - 1] else ' '
                    val nextChar = if (i + 1 < len) text[i + 1] else ' '
                    if (prevChar.isDigit() && nextChar.isDigit()) continue // e.g. 1.5
                    if (i >= 2 && prevChar.isUpperCase() && (i == 1 || text[i - 2] == ' ')) continue // e.g. "U. S." or "A."
                }
                // Check if followed by whitespace or end of string
                if (i + 1 == len || text[i + 1].isWhitespace() || text[i + 1] == '"' || text[i + 1] == '\'' || text[i + 1] == '»') {
                    boundaries.add(i + 1)
                }
            }
        }
        return boundaries
    }

    private fun findUnescapedQuoteIndex(text: String): Int {
        var isEscaped = false
        for (i in text.indices) {
            val c = text[i]
            if (c == '\\') {
                isEscaped = !isEscaped
            } else if (c == '"' && !isEscaped) {
                return i
            } else {
                isEscaped = false
            }
        }
        return -1
    }

    /**
     * Attempts to query Gemini with our list of candidate models in sequence.
     * If a model fails, we automatically fall back to the next one in the list.
     */
    suspend fun callGeminiWithFallback(
        apiKey: String,
        request: GeminiRequest
    ): GeminiResponse {
        var lastException: Exception? = null
        val preferred = getPreferredModel()
        val modelsToTry = LinkedHashSet<String>().apply {
            if (preferred.isNotBlank()) add(preferred)
            addAll(candidateModels)
        }.toList()

        for (model in modelsToTry) {
            try {
                Log.d("TutorAiService", "Querying Gemini with model: $model")
                val response = GeminiClient.apiService.generateContent(model, apiKey, request)
                if (response.candidates != null && response.candidates.isNotEmpty()) {
                    Log.i("TutorAiService", "Successfully received response using model: $model")
                    com.example.data.api.NetworkMonitor.setStatus(com.example.data.api.ConnectionStatus.CONNECTED)
                    return response
                }
            } catch (e: Exception) {
                lastException = e
                Log.w("TutorAiService", "Model $model call failed: ${e.message}")

                if (e is HttpException) {
                    val code = e.code()
                    if (code in listOf(401, 403)) {
                        throw e // Bad API key, abort immediately
                    }
                    if (code == 429) {
                        Log.w("TutorAiService", "Rate limited on $model (429), trying next candidate model immediately...")
                        continue
                    }
                }
                // Fast fallback to next model candidate
                continue
            }
        }
        throw lastException ?: Exception("Wszystkie próby połączenia z modelami Gemini nie powiodły się.")
    }

    /**
     * Generate evaluation summary after ending a session.
     */
    suspend fun evaluateSession(
        session: Session,
        messages: List<ChatMessage>
    ): SessionEvaluationResponse = withContext(Dispatchers.IO) {
        val conversationHistoryText = messages.joinToString("\n") { msg ->
            "${msg.sender.uppercase()}: ${msg.originalText}" + 
            if (msg.isCorrected) " (Corrected to: ${msg.correctedText})" else ""
        }
 
        val prompt = """
            You are Elena, a premium language coach. Review the following conversational session and provide a professional evaluation.
            
            SESSION DETAILS:
            - Title: ${session.title}
            - Language: ${session.language}
            - Target CEFR Level: ${session.cefrLevel}
            
            CONVERSATION HISTORY:
            $conversationHistoryText
            
            Based on the user's responses, evaluate their:
            1. Fluency Score (out of 100): Be realistic but encouraging. Higher scores require smooth, varied sentence structures.
            2. Strengths: What did they do well?
            3. Areas of Improvement: Syntactic errors, vocabulary limitations.
            4. Grammatical Tips: 2-3 specific, actionable grammar tips with examples in ${session.language} and English.
            5. Personalized Study Plan: Next steps for their level.
            
            You MUST return a JSON object matching this structure EXACTLY. No markdown formatting.
            
            JSON RESPONSE FORMAT:
            {
              "fluencyScore": 75,
              "strengths": "Provide 2-3 sentences highlighting positive vocabulary usage and confidence.",
              "improvements": "Provide 2-3 sentences on word choice or sentence complexity issues.",
              "grammarTips": "Highlight specific grammar issues found in the dialogue with corrected examples.",
              "studyPlan": "A brief 2-sentence personalized homework or practice guide."
            }
        """.trimIndent()
 
        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.5f,
                maxOutputTokens = 1500,
                responseMimeType = "application/json"
            )
        )
 
        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext getFallbackEvaluation("Brak klucza API.")
            }
 
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (!jsonText.isNullOrBlank()) {
                val cleanedJson = cleanJsonResponse(jsonText)
                val parsed = try {
                    evalAdapter.fromJson(cleanedJson)
                } catch (e1: Exception) {
                    Log.w("TutorAiService", "evalAdapter standard parse failed: ${e1.message}, trying repaired JSON...")
                    try {
                        val repaired = repairAndCleanJson(jsonText)
                        evalAdapter.fromJson(repaired)
                    } catch (e2: Exception) {
                        Log.w("TutorAiService", "evalAdapter repair parse failed: ${e2.message}, using fallback evaluation...")
                        tryFallbackEvaluationParse(jsonText)
                    }
                }
                parsed ?: tryFallbackEvaluationParse(jsonText) ?: getFallbackEvaluation("Błąd podczas przetwarzania podsumowania.")
            } else {
                getFallbackEvaluation("Nie udało się przeanalizować tej sesji.")
            }
        } catch (e: HttpException) {
            Log.e("TutorAiService", "HTTP exception during evaluateSession", e)
            if (e.code() == 429) {
                getFallbackEvaluation("Przekroczono limit darmowych zapytań (Błąd 429) podczas generowania podsumowania. Odczekaj minutę i spróbuj ponownie.")
            } else {
                getFallbackEvaluation("Błąd połączenia API (HTTP ${e.code()}). Sprawdź swój klucz API w ustawieniach.")
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "Evaluation failed", e)
            getFallbackEvaluation("Błąd sieci podczas generowania podsumowania: ${e.localizedMessage}")
        }
    }

    /**
     * Light-weight method to test if a given API key has a valid, working connection with Gemini API.
     */
    suspend fun testApiConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Brak klucza API. Klucz jest pusty lub posiada domyślną wartość."))
        }
        
        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = "Respond with only one word: OK")))),
            generationConfig = GenerationConfig(
                temperature = 0.1f,
                maxOutputTokens = 10
            )
        )

        var lastException: Exception? = null
        var workingModel: String? = null
        var text: String? = null

        val preferred = getPreferredModel()
        val modelsToTry = LinkedHashSet<String>().apply {
            if (preferred.isNotBlank()) add(preferred)
            addAll(candidateModels)
        }.toList()

        for (model in modelsToTry) {
            try {
                Log.d("TutorAiService", "Testing connection with model: $model")
                val response = GeminiClient.apiService.generateContent(model, apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!responseText.isNullOrBlank()) {
                    workingModel = model
                    text = responseText
                    break
                }
            } catch (e: Exception) {
                lastException = e
                Log.w("TutorAiService", "Connection test failed for model: $model. Error: ${e.message}")
                if (e is HttpException) {
                    val code = e.code()
                    if (code in listOf(401, 403)) {
                        break // Auth error, abort testing other models
                    }
                }
            }
        }

        if (workingModel != null && !text.isNullOrBlank()) {
            Result.success("Sukces ($workingModel): ${text.trim()}")
        } else {
            val e = lastException ?: Exception("Wszystkie modele zwróciły błąd połączenia.")
            if (e is HttpException) {
                val errorBody = try { e.response()?.errorBody()?.string() ?: "" } catch (ioe: Exception) { "" }
                Log.e("TutorAiService", "Test connection HTTP error: ${e.code()} - $errorBody", e)
                Result.failure(Exception("Błąd HTTP ${e.code()}: ${parseApiError(errorBody)}"))
            } else {
                Log.e("TutorAiService", "Test connection generic error", e)
                Result.failure(Exception("Błąd sieci/połączenia: ${e.localizedMessage}"))
            }
        }
    }

    private fun parseApiError(errorBody: String): String {
        return try {
            if (errorBody.contains("API_KEY_INVALID")) {
                "Nieprawidłowy klucz API (API_KEY_INVALID)"
            } else if (errorBody.contains("quota") || errorBody.contains("RESOURCE_EXHAUSTED")) {
                "Przekroczono limit zapytań (RESOURCE_EXHAUSTED / Quota exceeded)"
            } else if (errorBody.contains("blocked")) {
                "Zapytanie zostało zablokowane przez serwer."
            } else {
                // Return a snippet of the message
                val cleaned = errorBody.replace(Regex("[{}\"]"), "").trim()
                if (cleaned.length > 120) cleaned.take(120) + "..." else cleaned
            }
        } catch (e: Exception) {
            "Nieznany błąd serwera"
        }
    }

    private fun cleanJsonResponse(raw: String): String {
        try {
            var clean = raw.trim()
            if (clean.isBlank()) return "{}"
            if (clean.startsWith("```json")) {
                clean = clean.substringAfter("```json")
            } else if (clean.startsWith("```")) {
                clean = clean.substringAfter("```")
            }
            if (clean.endsWith("```")) {
                clean = clean.substringBeforeLast("```")
            }
            clean = clean.trim()
            val firstBrace = clean.indexOf('{')
            val lastBrace = clean.lastIndexOf('}')
            val firstBracket = clean.indexOf('[')
            val lastBracket = clean.lastIndexOf(']')

            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                if (firstBracket != -1 && lastBracket != -1 && firstBracket < firstBrace && lastBracket > lastBrace) {
                    return clean.substring(firstBracket, lastBracket + 1).trim()
                }
                return clean.substring(firstBrace, lastBrace + 1).trim()
            } else if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
                return clean.substring(firstBracket, lastBracket + 1).trim()
            }
            if (firstBrace != -1) {
                return clean.substring(firstBrace).trim()
            }
            return clean
        } catch (e: Exception) {
            Log.e("TutorAiService", "cleanJsonResponse failed", e)
            return "{}"
        }
    }

    private fun repairAndCleanJson(raw: String): String {
        var json = cleanJsonResponse(raw)
        if (json.isBlank() || json == "{}") return "{}"

        // 1. Count quote parity to close unclosed string
        var quoteCount = 0
        var isEscaped = false
        for (char in json) {
            if (char == '\\' && !isEscaped) {
                isEscaped = true
            } else {
                if (char == '"' && !isEscaped) {
                    quoteCount++
                }
                isEscaped = false
            }
        }
        if (quoteCount % 2 != 0) {
            json += "\""
        }

        // 2. Balance unclosed brackets and braces
        val stack = mutableListOf<Char>()
        var inString = false
        var esc = false
        for (char in json) {
            if (char == '\\' && !esc) {
                esc = true
                continue
            }
            if (char == '"' && !esc) {
                inString = !inString
            }
            if (!inString) {
                if (char == '{' || char == '[') {
                    stack.add(char)
                } else if (char == '}' && stack.isNotEmpty() && stack.last() == '{') {
                    stack.removeAt(stack.size - 1)
                } else if (char == ']' && stack.isNotEmpty() && stack.last() == '[') {
                    stack.removeAt(stack.size - 1)
                }
            }
            esc = false
        }

        val sb = StringBuilder(json)
        for (i in stack.indices.reversed()) {
            val matching = if (stack[i] == '{') '}' else ']'
            sb.append(matching)
        }
        return sb.toString()
    }

    private fun tryFallbackTutorTurnParse(rawText: String): TutorTurnResponse? {
        return try {
            val replyMatch = Regex("\"reply\"\\s*:\\s*\"([^\"]*)\"").find(rawText)?.groupValues?.get(1)
                ?: Regex("\"reply\"\\s*:\\s*\"(.*)").find(rawText)?.groupValues?.get(1)?.trimEnd('"', '}', ' ')
            val translationMatch = Regex("\"translation\"\\s*:\\s*\"([^\"]*)\"").find(rawText)?.groupValues?.get(1)
                ?: Regex("\"translation\"\\s*:\\s*\"(.*)").find(rawText)?.groupValues?.get(1)?.trimEnd('"', '}', ' ')

            val isCorrectedMatch = Regex("\"isCorrected\"\\s*:\\s*(true|false)").find(rawText)?.groupValues?.get(1)?.toBoolean() ?: false
            val correctedTextMatch = Regex("\"correctedText\"\\s*:\\s*\"([^\"]*)\"").find(rawText)?.groupValues?.get(1)
            val explanationMatch = Regex("\"explanation\"\\s*:\\s*\"([^\"]*)\"").find(rawText)?.groupValues?.get(1)
            val mistakeTypeMatch = Regex("\"mistakeType\"\\s*:\\s*\"([^\"]*)\"").find(rawText)?.groupValues?.get(1)
            val pronunciationScoreMatch = Regex("\"pronunciationScore\"\\s*:\\s*(\\d+)").find(rawText)?.groupValues?.get(1)?.toIntOrNull()
            val pronunciationFeedbackMatch = Regex("\"pronunciationFeedback\"\\s*:\\s*\"([^\"]*)\"").find(rawText)?.groupValues?.get(1)
            val phoneticGuideMatch = Regex("\"phoneticGuide\"\\s*:\\s*\"([^\"]*)\"").find(rawText)?.groupValues?.get(1)

            val correction = if (isCorrectedMatch || !correctedTextMatch.isNullOrBlank() || pronunciationScoreMatch != null) {
                TutorCorrection(
                    isCorrected = isCorrectedMatch,
                    correctedText = correctedTextMatch,
                    explanation = explanationMatch,
                    mistakeType = mistakeTypeMatch,
                    pronunciationScore = pronunciationScoreMatch,
                    pronunciationFeedback = pronunciationFeedbackMatch,
                    phoneticGuide = phoneticGuideMatch
                )
            } else null

            if (!replyMatch.isNullOrBlank()) {
                TutorTurnResponse(
                    correction = correction,
                    reply = replyMatch.trim(),
                    translation = translationMatch?.trim(),
                    vocabulary = emptyList(),
                    suggestions = listOf(
                        "Tell me more [Powiedz mi więcej]",
                        "I understand [Rozumiem]",
                        "What do you mean? [Co masz na myśli?]"
                    )
                )
            } else null
        } catch (e: Exception) {
            Log.e("TutorAiService", "tryFallbackTutorTurnParse error", e)
            null
        }
    }

    private fun tryFallbackEvaluationParse(rawText: String): SessionEvaluationResponse? {
        return try {
            val scoreMatch = Regex("\"fluencyScore\"\\s*:\\s*(\\d+)").find(rawText)?.groupValues?.get(1)?.toIntOrNull() ?: 75
            val strengthsMatch = Regex("\"strengths\"\\s*:\\s*\"([^\"]*)").find(rawText)?.groupValues?.get(1) ?: "Dobra aktywność w rozmowie."
            val improvementsMatch = Regex("\"improvements\"\\s*:\\s*\"([^\"]*)").find(rawText)?.groupValues?.get(1) ?: "Kontynuuj regularną praktykę."
            val grammarTipsMatch = Regex("\"grammarTips\"\\s*:\\s*\"([^\"]*)").find(rawText)?.groupValues?.get(1) ?: "Zwracaj uwagę na naturalny szyk zdań."
            val studyPlanMatch = Regex("\"studyPlan\"\\s*:\\s*\"([^\"]*)").find(rawText)?.groupValues?.get(1) ?: "Praktykuj 10 minut dziennie."

            SessionEvaluationResponse(
                fluencyScore = scoreMatch,
                strengths = strengthsMatch,
                improvements = improvementsMatch,
                grammarTips = grammarTipsMatch,
                studyPlan = studyPlanMatch
            )
        } catch (e: Exception) {
            Log.e("TutorAiService", "tryFallbackEvaluationParse error", e)
            null
        }
    }

    private fun tryFallbackShadowingParse(rawText: String): ShadowingSegmentResponse? {
        return try {
            val foreignMatch = Regex("\"foreignText\"\\s*:\\s*\"([^\"]*)").find(rawText)?.groupValues?.get(1)
            val translationMatch = Regex("\"nativeTranslation\"\\s*:\\s*\"([^\"]*)").find(rawText)?.groupValues?.get(1)
            val grammarMatch = Regex("\"grammarExplanation\"\\s*:\\s*\"([^\"]*)").find(rawText)?.groupValues?.get(1)
            if (!foreignMatch.isNullOrBlank()) {
                ShadowingSegmentResponse(
                    foreignText = foreignMatch.trim(),
                    nativeTranslation = translationMatch?.trim() ?: "Tłumaczenie tekstu",
                    keyPhrases = emptyList(),
                    retrievedQuestions = emptyList(),
                    transformations = emptyList(),
                    communicationScenario = "Standardowy przepływ",
                    grammarExplanation = grammarMatch?.trim()
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
    private fun getFallbackResponse(message: String) = TutorTurnResponse(
        correction = null,
        reply = message,
        translation = "I had a connection hitch.",
        vocabulary = emptyList(),
        suggestions = listOf("Please try again [Spróbuj ponownie]", "Check connection [Sprawdź połączenie]", "Okay, I understand [Dobrze, rozumiem]")
    )

    private fun getFallbackEvaluation(errorMsg: String) = SessionEvaluationResponse(
        fluencyScore = 50,
        strengths = "Conversation practice is always helpful!",
        improvements = "Encountered an issue evaluating: $errorMsg",
        grammarTips = "Keep practice fluid and speak continuously to discover grammar rules naturally.",
        studyPlan = "Continue practicing basic daily dialogues for 10-15 minutes."
    )

    /**
     * Generates a memorable mnemonic association (skojarzenie mnemotechniczne) in Polish for a word/phrase.
     */
    suspend fun generateMnemonic(
        wordOrPhrase: String,
        language: String
    ): String = withContext(Dispatchers.IO) {
        val rich = generateRichMnemonic(wordOrPhrase, language)
        if (rich != null) {
            try {
                richMnemonicAdapter.toJson(rich)
            } catch (e: Exception) {
                rich.association
            }
        } else {
            "Nie udało się wygenerować skojarzenia mnemotechnicznego. Spróbuj ponownie."
        }
    }

    /**
     * Generates a short story in the target language at the specified difficulty level, with Polish translation.
     */
    suspend fun generateReadingStory(
        topic: String,
        language: String,
        difficulty: String
    ): GeneratedStoryResponse = withContext(Dispatchers.IO) {
        val prompt = """
            Napisz ciekawe, wciągające opowiadanie (składające się z 2-4 krótkich akapitów) w języku: $language, na poziomie trudności: $difficulty (skala CEFR), o tematyce: "$topic", w pełni realizujące metodologię StoryLearning® Olly'ego Richardsa (nauka języka przez kontekstowe opowiadania).
            
            WAŻNE ZASADY STRUKTURY I STYLU OPOWIADANIA:
            - Wymuś stosowanie wyłącznie krótszych, prostszych zdań w całym opowiadaniu, aby znacznie ułatwić zrozumienie kontekstu. Unikaj zdań wielokrotnie złożonych i skomplikowanych konstrukcji składniowych.
            - Po każdym akapicie opowiadania, w polu "translation" również stosuj prosty sformułowany styl tłumaczenia na język polski dopasowany akapit w akapit.
            
            Oprócz samej historii, musisz wygenerować dodatkowe elementy metodologiczne:
            1. Listę 4-6 kluczowych, ciekawych słówek lub zwrotów (keyVocabularyList) wyciąganych bezpośrednio z tekstu tego opowiadania.
               - STRUKTURA SŁOWNICTWA: Każda pozycja musi opierać się na rozbiciu na pojedyncze zdanie z tekstu (contextSentence), w którym występuje to słowo, wraz z jego polskim tłumaczeniem (contextSentenceTranslation).
               - ZASADA UNIKALNOŚCI: Żadne dwa słowa na liście nie mogą dzielić tego samego zdania przykładowego (contextSentence). Każde wybrane słówko musi posiadać unikalne zdanie przykładowe z tekstu opowiadania. Wykorzystaj strukturę unikalnych kluczy/Set do eliminacji duplikatów zdań przykładowych.
            2. Wyjątkowo głębokie, pasjonujące i niezwykle rozbudowane wyjaśnienie gramatyczne po polsku (grammarExplanation) struktur gramatycznych, które pojawiły się naturalnie w opowiadaniu. Przyjmij profil zaawansowanego, prestiżowego i wysoce interaktywnego tutora językowego/lingwisty. Unikaj suchej teorii - odwołuj się bezpośrednio do cytatów z tekstu. Podziel to wyjaśnienie na czytelne sekcje:
               - Temat lekcji (użyj nagłówka ###...)
               - Wprowadzenie i kontekst w opowiadaniu
               - Jak to wygląda w tekście (użyj konkretnych cytatów z opowiadania w cudzysłowach "...")
               - Prosta i przejrzysta reguła (użyj punktów wypunktowanych - ...)
               - Przykłady porównawcze (dodaj mnóstwo bogatych przykładów językowych z boku - minimum 3-4 kontrastowe zdania z polskim tłumaczeniem dla każdej omawianej reguły, aby uczeń w pełni zobaczył mechanizm w akcji!)
               
               DODATKOWO, dodaj dedykowaną, zaawansowaną sekcję głębokiej analizy słownictwa i ich polskiego tłumaczenia (Głęboki Kontekst Leksykalny):
               - Słownictwo i Niuanse Kontekstowe (użyj nagłówka ### Słownictwo i Niuanse Kontekstowe)
                 Dla minimum 4 kluczowych, ciekawych słów lub wyrażeń w tekście oraz ich polskich odpowiedników dodaj sekcję szczegółowo wyjaśniającą DLACZEGO danego słowa/wyrażenia użyto w tym konkretnym kontekście (analiza niuansów gramatycznych, rejestr oficjalny/potoczny/literacki/slang, alternatywne sformułowania w tym języku i ich odmienne zabarwienie semantyczne, a także dlaczego polskie tłumaczenie zostało sformułowane właśnie w ten sposób).
               
               Używaj pustych linii między akapitami, aby tekst był przejrzysty i nie zlewał się ze sobą.
            3. Bogaty zestaw od 6 do 10 pytań sprawdzających zrozumienie tekstu oraz gramatykę (comprehensionQuestionsList) w języku $language, jednokrotnego wyboru (3-4 opcje), wraz z indeksem poprawnej odpowiedzi (0-indexed) i szczegółowym wyjaśnieniem po polsku (explanation). Odejdź od sztywnego limitu 3 pytań, aby zaoferować bogatszy zestaw weryfikacyjny.
               - Co najmniej połowa pytań musi wprost sprawdzać i utrwalać struktury gramatyczne oraz niuanse leksykalne omówione w sekcji gramatycznej (Moduł 3) w nowych, zmiennych kontekstach zdaniowych.
               - Pozostałe pytania powinny sprawdzać głębokie zrozumienie fabuły, implikacji oraz specyficznego słownictwa opowiadania.
            4. Generator 4 do 6 losowych, niezwykle zróżnicowanych scenariuszy konwersacyjnych i dylematów (scenariosList) osadzonych bezpośrednio w realiach tego opowiadania, które skutecznie utrwalają nabytą wiedzę oraz nabytą gramatykę w zmiennych kontekstach.
               - Scenariusze powinny obejmować:
                 a) Odgrywanie ról (Role-play) z określonym celem komunikacyjnym (np. negocjacje, rozwiązywanie problemów z bohaterem, dylemat moralny).
                 b) Symulowanie alternatywnego biegu zdarzeń lub dylematów moralnych i etycznych bohaterów.
                 c) Dyskusję filozoficzną lub psychologiczną o motywach postępowania postaci.
                 d) Ćwiczenie konkretnych reguł gramatycznych w symulacji życiowej (np. planowanie przyszłości z użyciem czasu przyszłego w realiach opowiadania).
               - Każda instrukcja inicjująca dla AI (initialPromptInstruction) musi wprost instruować model, aby nakłaniał i korygował użytkownika w zakresie stosowania form gramatycznych i specyficznego słownictwa. Podaj unikalny tytuł scenariusza po polsku, opis po polsku oraz instrukcję inicjującą po angielsku (initialPromptInstruction).

            BEZWZGLĘDNA ZASADA BEZPIECZEŃSTWA JSON (CRITICAL JSON INTEGRITY):
            - Wygenerowana odpowiedź MUSI być perfekcyjnie poprawnym, czystym i dobrze sformatowanym obiektem JSON.
            - W wartościach tekstowych (np. "initialPromptInstruction", "grammarExplanation", "translation") kategorycznie NIE używaj surowych, nieeskapowanych cudzysłowów podwójnych ("). Jeśli chcesz zacytować wypowiedź lub użyć cudzysłowu wewnątrz tekstu, używaj wyłącznie pojedynczych cudzysłowów (') lub eskapuj je za pomocą dwukrotnego ukośnika wstecznego i cudzysłowu (\").
            - Kategorycznie zabrania się używania rzeczywistych, surowych znaków nowej linii wewnątrz wartości stringów w JSON. Jeśli chcesz dodać podział linii w tekście, użyj jawnego znaku \n.

            Zwróć odpowiedź jako czysty obiekt JSON o następującej strukturze:
            {
              "title": "Tytuł opowiadania w języku $language",
              "content": "Pełna treść opowiadania w języku $language",
              "translation": "Tłumaczenie opowiadania na język polski (dopasowane akapitami, akapity oddzielone dwoma znakami nowej linii \n\n)",
              "keyVocabularyList": [
                {
                  "word": "słówko w języku $language wyciągnięte bezpośrednio z tekstu",
                  "translation": "polskie tłumaczenie",
                  "partOfSpeech": "część mowy np. czasownik/rzeczownik",
                  "contextSentence": "unikalne zdanie z opowiadania, w którym występuje to słowo",
                  "contextSentenceTranslation": "polskie tłumaczenie tego zdania przykładowego"
                }
              ],
              "grammarExplanation": "Wyjaśnienie gramatyki w kontekście po polsku...",
              "comprehensionQuestionsList": [
                {
                  "question": "Pytanie w języku $language",
                  "options": ["Opcja A", "Opcja B", "Opcja C"],
                  "answerIndex": 0,
                  "explanation": "Krótkie wyjaśnienie poprawnej odpowiedzi po polsku"
                }
              ],
               "scenariosList": [
                 {
                   "title": "Tytuł scenariusza po polsku",
                   "description": "Krótki opis scenariusza po polsku wyjaśniający kontekst i cel rozmowy",
                   "initialPromptInstruction": "Detailed instructions in English for Gemini of how it should act as a tutor and how to start the discussion for this scenario specifically, using ONLY single quotes for nested quotes."
                 }
               ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.8f,
                maxOutputTokens = 4000,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext GeneratedStoryResponse(
                    title = "Błąd klucza API",
                    content = "Brak skonfigurowanego klucza API Gemini.",
                    translation = "Skonfiguruj go w ustawieniach głównych Lingo AI."
                )
            }

            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = cleanJsonResponse(jsonText)
                storyAdapter.fromJson(cleaned) ?: GeneratedStoryResponse(
                    title = "Błąd parsowania",
                    content = "Nie udało się sparsować opowiadania.",
                    translation = "Spróbuj ponownie."
                )
            } else {
                GeneratedStoryResponse(
                    title = "Błąd pustej odpowiedzi",
                    content = "AI nie zwróciło żadnego tekstu.",
                    translation = "Spróbuj wygenerować jeszcze raz."
                )
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateReadingStory failed", e)
            GeneratedStoryResponse(
                title = "Błąd generowania",
                content = "Wystąpił błąd: ${e.localizedMessage}",
                translation = "Upewnij się, że masz połączenie z internetem."
            )
        }
    }

    /**
     * Analyzes raw material or image content to perform OCR, simplifies it to a specified CEFR level,
     * and turns it into an engaging StoryLearning-compliant story.
     */
    suspend fun generateStoryFromExternalContext(
        rawText: String,
        imageBase64: String?,
        imageMimeType: String?,
        language: String,
        difficulty: String
    ): GeneratedStoryResponse = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś wybitnym profesorem lingwistyki i ekspertem metodyki StoryLearning® Olly'ego Richardsa.
            
            ZADANIE:
            Zanalizuj poniższy materiał źródłowy (może być to tekst przekazany poniżej lub tekst wyodrębniony ze zdjęcia za pomocą OCR).
            Następnie:
            1. Wyodrębnij / odczytaj treść źródłową.
            2. Uprość ją do poziomu trudności $difficulty (skala CEFR) w języku obcym: $language.
            3. Przekształć ten uproszczony materiał w angażujące opowiadanie w formacie StoryLearning.
            
            OPIS MATERIAŁU ŹRÓDŁOWEGO (jeśli podano jako tekst):
            $rawText
            
            WYMAGANE ELEMENTY STRUKTURY (Zwróć jako czysty JSON):
            1. "title": Tytuł opowiadania dopasowany do tematyki w języku $language.
            2. "content": Treść nowego opowiadania w języku $language (podzielone na 2-4 logiczne, krótkie akapity). Język musi być uproszczony pod poziom CEFR: $difficulty. Zdania powinny być proste, dynamiczne i naturalne.
            3. "translation": Dokładne, uproszczone tłumaczenie opowiadania na język polski (dopasowane akapitami, oddzielone dwoma znakami nowej linii \n\n).
            4. "keyVocabularyList": Lista 4-6 kluczowych, interesujących słówek lub zwrotów wyciągniętych bezpośrednio z tekstu tego opowiadania. Dla każdego słowa podaj:
               - "word": słówko/zwrot w języku $language
               - "translation": polskie tłumaczenie słówka
               - "partOfSpeech": część mowy (np. czasownik, rzeczownik)
               - "contextSentence": unikalne zdanie z opowiadania, w którym występuje to słówko
               - "contextSentenceTranslation": polskie tłumaczenie tego zdania przykładowego
               - "mnemonic": spersonalizowana, zabawna, zapadająca w pamięć mnemotechnika w języku polskim, która pomoże polskiemu użytkownikowi skojarzyć to słowo (np. szukając podobnie brzmiącego polskiego słowa i tworząc absurdalną historyjkę skojarzeniową).
            5. "grammarExplanation": Głębokie, pasjonujące i rozbudowane wyjaśnienie struktur gramatycznych, które pojawiły się naturalnie w opowiadaniu. Użyj formatu Markdown z nagłówkami ###. Podziel na:
               - ### Temat lekcji gramatyki
               - ### Wprowadzenie i kontekst w opowiadaniu
               - ### Cytaty z tekstu i reguła gramatyczna
               - ### Bogate przykłady porównawcze (minimum 3-4 zdania kontrastowe z tłumaczeniem na PL)
               - ### Słownictwo i Niuanse Kontekstowe (szczegółowe wyjaśnienie niuansów użycia min. 4 słów)
            6. "comprehensionQuestionsList": Bogaty zestaw 6-10 pytań sprawdzających zrozumienie tekstu oraz gramatyki w języku $language, jednokrotnego wyboru (3-4 opcje), wraz z answerIndex (0-indexed) i szczegółowym wyjaśnieniem po polsku ("explanation").
            7. "scenariosList": Generator 4-6 niezwykle zróżnicowanych scenariuszy konwersacyjnych i dylematów osadzonych w realiach tego opowiadania (odgrywanie ról, dylematy etyczne). Każda instrukcja ("initialPromptInstruction") musi być po angielsku i instruować AI jak zachować się jako tutor i inicjować rozmowę.
            
            BEZWZGLĘDNA ZASADA BEZPIECZEŃSTWA JSON (CRITICAL JSON INTEGRITY):
            - Zwrócona odpowiedź musi być wyłącznie poprawnym, czystym i dobrze sformatowanym obiektem JSON.
            - Wewnątrz wartości stringów kategorycznie NIE używaj surowych cudzysłowów podwójnych ("). Zastąp je pojedynczymi (') lub eskapuj je jako \".
            - Nie używaj surowych znaków nowej linii wewnątrz wartości stringów, stosuj \n.
            
            Zwróć odpowiedź jako czysty obiekt JSON o następującej strukturze:
            {
              "title": "Tytuł",
              "content": "Treść opowiadania",
              "translation": "Tłumaczenie PL",
              "keyVocabularyList": [
                {
                  "word": "słówko",
                  "translation": "tłumaczenie",
                  "partOfSpeech": "część mowy",
                  "contextSentence": "zdanie z tekstu",
                  "contextSentenceTranslation": "tłumaczenie zdania",
                  "mnemonic": "mnemotechnika po polsku"
                }
              ],
              "grammarExplanation": "Wyjaśnienie po polsku",
              "comprehensionQuestionsList": [
                {
                  "question": "Pytanie",
                  "options": ["Opcja A", "Opcja B", "Opcja C"],
                  "answerIndex": 0,
                  "explanation": "Wyjaśnienie PL"
                }
              ],
              "scenariosList": [
                {
                  "title": "Tytuł scenariusza PL",
                  "description": "Opis scenariusza PL",
                  "initialPromptInstruction": "Detailed english instruction..."
                }
              ]
            }
        """.trimIndent()

        val partsList = mutableListOf<Part>()
        if (!imageBase64.isNullOrBlank()) {
            partsList.add(Part(inlineData = InlineData(mimeType = imageMimeType ?: "image/jpeg", data = imageBase64)))
            partsList.add(Part(text = "OCR i analiza obrazu: Przeczytaj tekst widoczny na tym zdjęciu i potraktuj go jako materiał źródłowy. Następnie postępuj zgodnie z instrukcjami poniżej.\n\n$prompt"))
        } else {
            partsList.add(Part(text = prompt))
        }

        val request = GeminiRequest(
            contents = listOf(Content(parts = partsList)),
            generationConfig = GenerationConfig(
                temperature = 0.8f,
                maxOutputTokens = 4000,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext GeneratedStoryResponse(
                    title = "Błąd klucza API",
                    content = "Brak skonfigurowanego klucza API Gemini.",
                    translation = "Skonfiguruj go w ustawieniach głównych Lingo AI."
                )
            }

            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = cleanJsonResponse(jsonText)
                storyAdapter.fromJson(cleaned) ?: GeneratedStoryResponse(
                    title = "Błąd parsowania",
                    content = "Nie udało się sparsować opowiadania ze źródła.",
                    translation = "Spróbuj ponownie z innym materiałem źródłowym."
                )
            } else {
                GeneratedStoryResponse(
                    title = "Błąd pustej odpowiedzi",
                    content = "AI nie wygenerowało historii ze źródła.",
                    translation = "Spróbuj ponownie."
                )
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateStoryFromExternalContext failed", e)
            GeneratedStoryResponse(
                title = "Błąd importera",
                content = "Wystąpił błąd podczas importowania: ${e.localizedMessage}",
                translation = "Upewnij się, że masz połączenie z internetem i sprawny klucz API."
            )
        }
    }

    /**
     * Extracts article content from a web URL and creates a factual, informative executive summary
     * and knowledge synthesis in the target foreign language, rather than generating a fictional story.
     */
    suspend fun generateArticleSummaryFromUrl(
        rawText: String,
        language: String,
        difficulty: String
    ): GeneratedStoryResponse = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś wybitnym analitykiem prasowym, dziennikarzem naukowym i ekspertem edukacji językowej.
            
            ZADANIE:
            Zanalizuj poniższy materiał źródłowy pobrany z adresu WWW.
            Twoim celem NIE jest pisanie fikcyjnego opowiadania ani bajki! 
            Twoim celem jest stworzenie RZETELNEGO, STRUKTURALNEGO STRESZCZENIA i SYNTEZY WIEDZY z tego artykułu prasowego/strony w języku obcym: $language, zoptymalizowanego pod poziom trudności CEFR: $difficulty.
            Użytkownik uczy się języka $language poprzez przyswajanie prawdziwych, rzetelnych informacji ze świata (News, Historia, Nauka, Świat).
            
            MATERIAŁ ŹRÓDŁOWY Z LINKU WWW:
            $rawText
            
            WYMAGANE ELEMENTY STRUKTURALNE (Zwróć jako czysty JSON):
            1. "title": Rzetelny, faktyczny tytuł artykułu lub streszczenia w języku $language (np. z dopiskiem portalu źródłowego).
            2. "content": Rzetelne, strukturyzowane streszczenie kluczowych faktów, danych i wiedzy z artykułu w języku $language (podzielone na 2-4 zwięzłe, przejrzyste akapity/rozdziały). Język dostosuj ściśle do poziomu CEFR: $difficulty. Zadbaj o precyzję merytoryczną i autentyczność faktów.
            3. "translation": Wierne, eleganckie i czytelne tłumaczenie tego streszczenia wiedzy na język polski (dopasowane akapitami, oddzielone \n\n).
            4. "keyVocabularyList": Lista 4-6 najważniejszych słówek, pojęć merytorycznych lub zwrotów dziedzinowych występujących w tym artykule. Dla każdego słowa podaj:
               - "word": słówko/pojęcie w języku $language
               - "translation": polskie tłumaczenie
               - "partOfSpeech": część mowy lub typ pojęcia
               - "contextSentence": konkretne zdanie ze streszczenia artykułu, w którym pojawia się to słówko
               - "contextSentenceTranslation": polskie tłumaczenie tego zdania
               - "mnemonic": spersonalizowana, ułatwiająca zapamiętanie mnemotechnika lub skojarzenie po polsku.
            5. "grammarExplanation": Głębokie, merytoryczne wyjaśnienie struktur gramatycznych, szyku zdań i niuansów językowych użytych w tekście artykułu. Użyj formatu Markdown z nagłówkami ###:
               - ### Prawdziwy język w mediach i artykułach
               - ### Kluczowe struktury gramatyczne w tekście
               - ### Reguły i zastosowanie z przykładami z artykułu
               - ### Słownictwo i Niuanse Dziedzinowe (wyjaśnienie użycia min. 4 pojęć)
            6. "comprehensionQuestionsList": Zestaw 6-10 pytań sprawdzających faktyczną wiedzę i rozumienie informacji zawartych w artykule w języku $language, jednokrotnego wyboru (3-4 opcje), wraz z answerIndex (0-indexed) i szczegółowym wyjaśnieniem po polsku ("explanation").
            7. "scenariosList": Generator 4-6 scenariuszy dyskusyjnych i debat na temat faktów zaprezentowanych w tym artykule (np. udział w dyskusji eksperckiej, rozmowa o aktualnościach ze świata). Instrukcja "initialPromptInstruction" musi być po angielsku dla tutora AI.
            
            BEZWZGLĘDNA ZASADA BEZPIECZEŃSTWA JSON (CRITICAL JSON INTEGRITY):
            - Zwrócona odpowiedź musi być wyłącznie poprawnym, czystym obiektem JSON.
            - Wewnątrz wartości stringów kategorycznie NIE używaj surowych cudzysłowów podwójnych ("). Zastąp je pojedynczymi (') lub eskapuj jako \".
            - Nie używaj surowych znaków nowej linii wewnątrz wartości stringów, stosuj \n.
            
            Zwróć odpowiedź jako czysty obiekt JSON o następującej strukturze:
            {
              "title": "Tytuł Streszczenia",
              "content": "Rzetelne Streszczenie Artykułu",
              "translation": "Tłumaczenie PL",
              "keyVocabularyList": [
                {
                  "word": "pojęcie",
                  "translation": "tłumaczenie",
                  "partOfSpeech": "część mowy",
                  "contextSentence": "zdanie z artykułu",
                  "contextSentenceTranslation": "tłumaczenie zdania",
                  "mnemonic": "mnemotechnika po polsku"
                }
              ],
              "grammarExplanation": "Wyjaśnienie gramatyki i stylu po polsku",
              "comprehensionQuestionsList": [
                {
                  "question": "Pytanie o fakt z artykułu",
                  "options": ["Opcja A", "Opcja B", "Opcja C"],
                  "answerIndex": 0,
                  "explanation": "Wyjaśnienie PL"
                }
              ],
              "scenariosList": [
                {
                  "title": "Debata / Dyskusja o artykule",
                  "description": "Opis dyskusji PL",
                  "initialPromptInstruction": "Detailed english instruction..."
                }
              ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.5f,
                maxOutputTokens = 4000,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext GeneratedStoryResponse(
                    title = "Błąd klucza API",
                    content = "Brak skonfigurowanego klucza API Gemini.",
                    translation = "Skonfiguruj go w ustawieniach głównych Lingo AI."
                )
            }

            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = cleanJsonResponse(jsonText)
                storyAdapter.fromJson(cleaned) ?: GeneratedStoryResponse(
                    title = "Błąd parsowania",
                    content = "Nie udało się sparsować streszczenia artykułu ze źródła.",
                    translation = "Spróbuj ponownie z innym linkiem WWW."
                )
            } else {
                GeneratedStoryResponse(
                    title = "Błąd pustej odpowiedzi",
                    content = "AI nie wygenerowało streszczenia ze źródła.",
                    translation = "Spróbuj ponownie."
                )
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateArticleSummaryFromUrl failed", e)
            GeneratedStoryResponse(
                title = "Błąd importera WWW",
                content = "Wystąpił błąd podczas analizy artykułu: ${e.localizedMessage}",
                translation = "Upewnij się, że podany link jest dostępny publicznie."
            )
        }
    }

    /**
     * Analyzes an existing story and generates the StoryLearning® methodology components (keyVocabularyList, grammarExplanation, comprehensionQuestionsList).
     */
    suspend fun analyzeStoryForStoryLearning(
        title: String,
        content: String,
        language: String
    ): GeneratedStoryResponse = withContext(Dispatchers.IO) {
        val prompt = """
            Przeanalizuj poniższe opowiadanie w języku $language i wyodrębnij elementy zgodne z metodologią StoryLearning® Olly'ego Richardsa:
            
            Tytuł: "$title"
            Treść: "$content"
            
            WAŻNE ZASADY ANALIZY I STRUKTURY SŁOWNICTWA:
            - Wymuś stosowanie wyłącznie krótszych, prostszych zdań w tłumaczeniu opowiadania w polu "translation" na język polski, dopasowując akapit do akapitu.
            - Listę 4-6 kluczowych, ciekawych słówek lub zwrotów (keyVocabularyList) musisz wyciągnąć bezpośrednio z tekstu tego opowiadania.
            - STRUKTURA SŁOWNICTWA: Każda pozycja musi opierać się na rozbiciu na pojedyncze zdanie z tekstu (contextSentence), w którym występuje to słowo, wraz z jego polskim tłumaczeniem (contextSentenceTranslation).
            - ZASADA UNIKALNOŚCI: Żadne dwa słowa na liście nie mogą dzielić tego samego zdania przykładowego (contextSentence). Każde wybrane słówko musi posiadać unikalne zdanie przykładowe z tekstu opowiadania. Wykorzystaj strukturę unikalnych kluczy/Set do eliminacji duplikatów zdań przykładowych.
            
            Wygeneruj:
            1. Listę 4-6 kluczowych słówek lub zwrotów (keyVocabularyList) spełniających powyższe zasady.
            2. Wyjątkowo głębokie, pasjonujące i niezwykle rozbudowane wyjaśnienie gramatyczne po polsku (grammarExplanation) struktur gramatycznych, które pojawiły się naturalnie w opowiadaniu. Przyjmij profil zaawansowanego, prestiżowego i wysoce interaktywnego tutora językowego/lingwisty. Unikaj suchej teorii - odwołuj się bezpośrednio do cytatów z tekstu. Podziel to wyjaśnienie na czytelne sekcje:
               - Temat lekcji (użyj nagłówka ###...)
               - Wprowadzenie i kontekst w opowiadaniu
               - Jak to wygląda w tekście (użyj konkretnych cytatów z opowiadania w cudzysłowach "...")
               - Prosta i przejrzysta reguła (użyj punktów wypunktowanych - ...)
               - Przykłady porównawcze (dodaj mnóstwo bogatych przykładów językowych z boku - minimum 3-4 kontrastowe zdania z polskim tłumaczeniem dla każdej omawianej reguły, aby uczeń w pełni zobaczył mechanizm w akcji!)
               
               DODATKOWO, dodaj dedykowaną, zaawansowaną sekcję głębokiej analizy słownictwa i ich polskiego tłumaczenia (Głęboki Kontekst Leksykalny):
               - Słownictwo i Niuanse Kontekstowe (użyj nagłówka ### Słownictwo i Niuanse Kontekstowe)
                 Dla minimum 4 kluczowych, ciekawych słów lub wyrażeń w tekście oraz ich polskich odpowiedników dodaj sekcję szczegółowo wyjaśniającą DLACZEGO danego słowa/wyrażenia użyto w tym konkretnym kontekście (analiza niuansów gramatycznych, rejestr oficjalny/potoczny/literacki/slang, alternatywne sformułowania w tym języku i ich odmienne zabarwienie semantyczne, a także dlaczego polskie tłumaczenie zostało sformułowane właśnie w ten sposób).
               
               Używaj pustych linii między akapitami, aby tekst był przejrzysty i nie zlewał się ze sobą.
            3. Bogaty zestaw od 6 do 10 pytań sprawdzających zrozumienie tekstu oraz gramatykę (comprehensionQuestionsList) w języku $language, jednokrotnego wyboru (3-4 opcje), wraz z indeksem poprawnej odpowiedzi (0-indexed) i szczegółowym wyjaśnieniem po polsku (explanation). Odejdź od sztywnego limitu 3 pytań, aby zaoferować bogatszy zestaw weryfikacyjny.
               - Co najmniej połowa pytań musi wprost sprawdzać i utrwalać struktury gramatyczne oraz niuanse leksykalne omówione w sekcji gramatycznej (Moduł 3) w nowych, zmiennych kontekstach zdaniowych.
               - Pozostałe pytania powinny sprawdzać głębokie zrozumienie fabuły, implikacji oraz specyficznego słownictwa opowiadania.
            4. Generator 4 do 6 losowych, niezwykle zróżnicowanych scenariuszy konwersacyjnych i dylematów (scenariosList) osadzonych bezpośrednio w realiach tego opowiadania, które skutecznie utrwalają nabytą wiedzę oraz nabytą gramatykę w zmiennych kontekstach.
               - Scenariusze powinny obejmować:
                 a) Odgrywanie ról (Role-play) z określonym celem komunikacyjnym (np. negocjacje, rozwiązywanie problemów z bohaterem, dylemat moralny).
                 b) Symulowanie alternatywnego biegu zdarzeń lub dylematów moralnych i etycznych bohaterów.
                 c) Dyskusję filozoficzną lub psychologiczną o motywach postępowania postaci.
                 d) Ćwiczenie konkretnych reguł gramatycznych w symulacji życiowej (np. planowanie przyszłości z użyciem czasu przyszłego w realiach opowiadania).
               - Każda instrukcja inicjująca dla AI (initialPromptInstruction) musi wprost instruować model, aby nakłaniał i korygował użytkownika w zakresie stosowania form gramatycznych i specyficznego słownictwa. Podaj unikalny tytuł scenariusza po polsku, opis po polsku oraz instrukcję inicjującą po angielsku (initialPromptInstruction).

            BEZWZGLĘDNA ZASADA BEZPIECZEŃSTWA JSON (CRITICAL JSON INTEGRITY):
            - Wygenerowana odpowiedź MUSI być perfekcyjnie poprawnym, czystym i dobrze sformatowanym obiektem JSON.
            - W wartościach tekstowych (np. "initialPromptInstruction", "grammarExplanation", "translation") kategorycznie NIE używaj surowych, nieeskapowanych cudzysłowów podwójnych ("). Jeśli chcesz zacytować wypowiedź lub użyć cudzysłowu wewnątrz tekstu, używaj wyłącznie pojedynczych cudzysłowów (') lub eskapuj je za pomocą dwukrotnego ukośnika wstecznego i cudzysłowu (\").
            - Kategorycznie zabrania się używania rzeczywistych, surowych znaków nowej linii wewnątrz wartości stringów w JSON. Jeśli chcesz dodać podział linii w tekście, użyj jawnego znaku \n.

            Zwróć odpowiedź jako czysty obiekt JSON o następującej strukturze:
            {
              "title": "$title",
              "content": "$content",
              "translation": "Tłumaczenie opowiadania na język polski (dopasowane akapitami, akapity oddzielone dwoma znakami nowej linii \n\n)",
              "keyVocabularyList": [
                {
                  "word": "słówko w języku $language wyciągnięte bezpośrednio z tekstu",
                  "translation": "polskie tłumaczenie",
                  "partOfSpeech": "część mowy np. czasownik/rzeczownik",
                  "contextSentence": "unikalne zdanie z opowiadania, w którym występuje to słowo",
                  "contextSentenceTranslation": "polskie tłumaczenie tego zdania przykładowego"
                }
              ],
              "grammarExplanation": "Wyjaśnienie gramatyki w kontekście po polsku...",
              "comprehensionQuestionsList": [
                {
                  "question": "Pytanie w języku $language",
                  "options": ["Opcja A", "Opcja B", "Opcja C"],
                  "answerIndex": 0,
                  "explanation": "Krótkie wyjaśnienie poprawnej odpowiedzi po polsku"
                }
              ],
               "scenariosList": [
                 {
                   "title": "Tytuł scenariusza po polsku",
                   "description": "Krótki opis scenariusza po polsku wyjaśniający kontekst i cel rozmowy",
                   "initialPromptInstruction": "Detailed instructions in English for Gemini of how it should act as a tutor and how to start the discussion for this scenario specifically, using ONLY single quotes for nested quotes."
                 }
               ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.5f,
                maxOutputTokens = 4000,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext GeneratedStoryResponse(title, content, "Skonfiguruj klucz API Gemini.")
            }

            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = cleanJsonResponse(jsonText)
                storyAdapter.fromJson(cleaned) ?: GeneratedStoryResponse(title, content, "")
            } else {
                GeneratedStoryResponse(title, content, "")
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "analyzeStoryForStoryLearning failed", e)
            GeneratedStoryResponse(title, content, "")
        }
    }

    /**
     * Generates a deep, interactive StoryLearning grammar lesson with mental hooks,
     * voice practice examples, structure duels (contrasts), pitfalls, and interactive micro-challenges.
     */
    suspend fun generateRichGrammarLesson(
        title: String,
        content: String,
        language: String,
        difficulty: String = "B1"
    ): StoryGrammarLesson = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś elitarnym neurodydaktykiem i metodykiem StoryLearning® Olly'ego Richardsa.
            Twoim zadaniem jest stworzenie arcydzieła dydaktycznego: interaktywnej, nowoczesnej lekcji gramatyki w kontekście opartej na poniższym opowiadaniu w języku $language (poziom CEFR: $difficulty).

            TYTUŁ OPOWIADANIA: "$title"
            TREŚĆ OPOWIADANIA:
            "$content"

            ZASADY METODYCZNE (PRAWDZIWA NEURO-GRAMATYKA LINGO-AI):
            1. ZAKAZ NUDNEJ TEORII SZKOLNEJ: Zastąp żargon lingwistyczny intuicją, żywymi obrazami i bezpośrednią wokalizacją.
            2. DEKONSTRUKCJA GRAMATYCZNA (Sentence Deconstruction): Rozbij CAŁE kluczowe zdanie z opowiadania na 3-6 anatomicznych klocków/tokenów (np. [Podmiot/Fundament] -> [Wyzwalacz] -> [Kluczowa forma gramatyczna] -> [Dopełnienie/Kontekst]). Klocki MUSZĄ składać się na PEŁNE zdanie (nie pomijaj początku ani końca zdania!). Do każdego klocka podaj wyjaśnienie jego roli w mózgu native speakera. Oznacz isGrammarCore = true dla rdzenia reguły gramatycznej.
            3. METODA PODSTAWIEŃ (Substitution Drills): Stwórz matrycę podstawień bazującą na schemacie zdania z opowiadania. Wybierz stałą część (fixedPart) i nazwę zmiennego slotu (variableSlotName), a następnie podaj 3 żywe wariacje (variations), gdzie zmiana 1 elementu natychmiast uczy elastycznego tworzenia zdań.
            4. AKTYWNA PRODUKCJA (Output Challenge): Przygotuj wyzwanie aktywnego outputu - polskie zdanie do przetłumaczenia (polishPrompt), docelowe zdanie w $language (targetSentence), podpowiedź struktury (hintStructure) oraz rozsypane klocki/słowa (scrambledTokens).
            5. HAKI PAMIĘCIOWE / MNEMOTECHNIKI (Mental Hooks): "mentalHook" MUSI być konkretną, obrazową metaforą lub symbolem wizualnym z emoji (np. 'Ser to DNA 🧬 vs Estar to Termometr 🌡️', 'Subjuntivo to Mgła Życzeń 🌫️💭 vs Indicativo Twarda Skała 🪨', 'Pretérito Indefinido to Błyskawica ⚡ vs Imperfecto Tło w Filmie 🎬').
            6. POJEDYNKI STRUKTUR / KONTRASTY (Structure Duels): Pokaż żywy kontrast między formą native speakera a kalką z języka polskiego.
            7. TRENING GŁOSEM (Voice Practice): Pole "sentence" MUSI zawierać WYŁĄCZNIE czyste zdanie w języku $language do wymowy. ZAKAZ wpisywania jakichkolwiek polskich opisów czy wyjaśnień reguły do pola "sentence"!
            8. PUŁAPKI DLA POLAKÓW (Polish Pitfalls): Wskaż, w co najczęściej wpadają Polacy (kalki dosłowne, błędny czas).
            9. NIUANSE LEKSYKALNE (Mandatory Lexical Nuances): Obowiązkowo opisz 3-4 unikalne słowa/zwroty z opowiadania z ich rejestrem i alternatywami.

            STRUKTURA JSON (Zwróć WYŁĄCZNIE czysty obiekt JSON, bez znaczników ```json):
            {
              "lessonTitle": "Chwytliwy tytuł lekcji gramatyki po polsku",
              "coreInsight": "Krótkie, fascynujące podsumowanie intuicji językowej płynącej z tej historii (2-3 zdania)",
              "rules": [
                {
                  "ruleName": "Nazwa reguły (np. Pretérito Indefinido vs Imperfecto: Akcja vs Tło)",
                  "oneLiner": "Esencja zasady w jednym, prostym zdaniu",
                  "mentalHook": "🧠 Hak Pamięciowy: obrazowa metafora z emoji wyjaśniająca mechanizm",
                  "storyQuote": "Dokładny cytat z tego opowiadania w języku $language",
                  "storyQuoteTranslation": "Polskie tłumaczenie cytatu",
                  "deconstruction": {
                    "fullSentence": "Zdanie z opowiadania w języku $language",
                    "translation": "Polskie tłumaczenie zdania",
                    "tokens": [
                      {
                        "text": "Fragment/klocek",
                        "role": "Nazwa roli (np. Wyzwalacz / Fundament / Akcja)",
                        "roleDescription": "Krótkie wyjaśnienie dlaczego ta forma została użyta",
                        "isGrammarCore": true
                      }
                    ]
                  },
                  "substitutionDrill": {
                    "templatePattern": "Wzorzec np. 'Es necesario que [OSOBA] [AKCJA]'",
                    "templateTranslation": "Polskie tłumaczenie wzorca",
                    "fixedPart": "Es necesario que",
                    "variableSlotName": "Podmiot & Akcja",
                    "variations": [
                      {
                        "slotValue": "tú / vengas temprano",
                        "resultingSentence": "Es necesario que vengas temprano.",
                        "translation": "Konieczne jest, abyś przyszedł wcześnie.",
                        "whyFormChanged": "Dlaczego forma się zmieniła"
                      }
                    ]
                  },
                  "activeOutputChallenge": {
                    "polishPrompt": "Sytuacja/zdanie po polsku do ułożenia z klocków i wypowiedzenia",
                    "targetSentence": "Poprawne zdanie w języku $language",
                    "hintStructure": "Podpowiedź szkieletu",
                    "scrambledTokens": ["losowe", "klocki", "ze", "zdania"]
                  },
                  "contrasts": [
                    {
                      "title": "Kontrast / Pojedynek (np. Żywa Ulica vs Sztywny Podręcznik)",
                      "correctForm": "Zdanie poprawne w języku $language",
                      "correctTranslation": "Polskie tłumaczenie",
                      "commonMistakeOrLiteral": "Błędne sformułowanie / kalka z polskiego",
                      "explanation": "Dlaczego tak mówimy - intencja, emocja lub niuans",
                      "scenarioContext": "Krótki kontekst sytuacyjny"
                    }
                  ],
                  "voicePracticeExamples": [
                    {
                      "sentence": "Zdanie do wokalizacji na głos w języku $language",
                      "translation": "Polskie tłumaczenie",
                      "speechTip": "Wskazówka: melodia, akcent, łączenie słów (elizja)",
                      "situationContext": "Gdzie tego użyjesz"
                    }
                  ],
                  "polishTrap": "⚠️ Wyjaśnienie typowej pułapki dla osób mówiących po polsku",
                  "interactiveChallenge": {
                    "question": "Pytanie testujące wyczucie tej reguły w języku $language",
                    "options": ["Opcja A", "Opcja B", "Opcja C"],
                    "answerIndex": 0,
                    "explanation": "Dlaczego ta opcja jest poprawna"
                  }
                }
              ],
              "lexicalNuances": [
                {
                  "phrase": "zwrot/słowo z tekstu w języku $language",
                  "translation": "polskie tłumaczenie",
                  "whyUsedInStory": "Dlaczego użyto akurat tego słowa w tej historii",
                  "register": "np. Potoczny / Literacki / Neutralny / Emocjonalny",
                  "alternativePhrases": ["alternatywa 1", "alternatywa 2"]
                }
              ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.5f,
                maxOutputTokens = 3500,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext StoryGrammarLesson(
                    lessonTitle = "Brak klucza API",
                    coreInsight = "Skonfiguruj swój klucz API Gemini w ustawieniach aplikacji."
                )
            }
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = cleanJsonResponse(jsonText)
                storyGrammarLessonAdapter.fromJson(cleaned) ?: StoryGrammarLesson(
                    lessonTitle = "Lekcja Gramatyki w Kontekście",
                    coreInsight = "Gramatyka w metodzie StoryLearning uczy się intuicyjnie z fabuły."
                )
            } else {
                StoryGrammarLesson(
                    lessonTitle = "Lekcja Gramatyki",
                    coreInsight = "Nie udało się wygenerować lekcji gramatyki."
                )
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateRichGrammarLesson failed", e)
            StoryGrammarLesson(
                lessonTitle = "Lekcja Gramatyki",
                coreInsight = "Wystąpił błąd: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Dynamically generates extra scenarios and more comprehension questions for consolidation.
     */
    suspend fun generateMoreQuestionsAndScenarios(
        title: String,
        content: String,
        grammarExplanation: String,
        language: String,
        difficulty: String
    ): GeneratedStoryResponse = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś wybitnym lingwistą i ekspertem ds. dydaktyki językowej. Na podstawie poniższego opowiadania w języku $language (poziom $difficulty):
            
            Tytuł: "$title"
            Treść: "$content"
            Gramatyka omówiona w lekcji: "$grammarExplanation"
            
            Wygeneruj dodatkowe, wyjątkowe materiały konsolidacyjne (Scenario & Question Engine):
            
            1. Bogaty zestaw od 6 do 10 NOWYCH, unikalnych pytań sprawdzających zrozumienie tekstu oraz gramatykę (comprehensionQuestionsList) w języku $language, jednokrotnego wyboru (3-4 opcje), wraz z indeksem poprawnej odpowiedzi (0-indexed) i szczegółowym wyjaśnieniem po polsku (explanation).
               - Co najmniej połowa pytań musi wprost weryfikować opanowanie i poprawne stosowanie struktur gramatycznych oraz niuansów leksykalnych omówionych w lekcji w zupełnie nowych, zmiennych zdaniach.
               - Pytania powinny weryfikować głębokie zrozumienie tekstu, motywów bohaterów, a także poboczne szczegóły fabularne.
            
            2. Generator 4 do 6 losowych, niezwykle zróżnicowanych scenariuszy konwersacyjnych i dylematów (scenariosList) osadzonych bezpośrednio w realiach tego opowiadania, które skutecznie utrwalają nabytą wiedzę oraz nabytą gramatykę w zmiennych kontekstach.
               - Scenariusze powinny obejmować:
                 a) Odgrywanie ról (Role-play) z określonym celem komunikacyjnym (np. negocjacje, rozwiązywanie problemów z bohaterem, dylemat moralny).
                 b) Symulowanie alternatywnego biegu zdarzeń lub dylematów moralnych i etycznych bohaterów.
                 c) Dyskusję filozoficzną lub psychologiczną o motywach postępowania postaci.
                 d) Ćwiczenie konkretnych reguł gramatycznych w symulacji życiowej (np. planowanie przyszłości z użyciem czasu przyszłego w realiach opowiadania).
               - Każdy scenariusz musi zawierać:
                 - Tytuł po polsku (title)
                 - Opis po polsku (description) - jak najbardziej barwny i motywujący do rozmowy
                 - Instrukcję inicjującą po angielsku dla modelu Gemini (initialPromptInstruction), która instruuje model, jak ma odgrywać rolę (np. partnera w dyskusji, antagonistę, doradcę) i aktywnie poprawiać błędy gramatyczno-leksykalne użytkownika. Używaj TYLKO pojedynczych cudzysłowów (') dla wewnętrznych cytatów.

            BEZWZGLĘDNA ZASADA BEZPIECZEŃSTWA JSON (CRITICAL JSON INTEGRITY):
            - Wygenerowana odpowiedź MUSI być perfekcyjnie poprawnym, czystym i dobrze sformatowanym obiektem JSON.
            - W wartościach tekstowych (np. "initialPromptInstruction", "explanation") kategorycznie NIE używaj surowych, nieeskapowanych cudzysłowów podwójnych ("). Jeśli chcesz zacytować wypowiedź lub użyć cudzysłowu wewnątrz tekstu, używaj wyłącznie pojedynczych cudzysłowów (') lub eskapuj je za pomocą dwukrotnego ukośnika wstecznego i cudzysłowu (\\\").
            - Kategorycznie zabrania się używania rzeczywistych, surowych znaków nowej linii wewnątrz wartości stringów w JSON. Jeśli chcesz dodać podział linii w tekście, użyj jawnego znaku \\n.

            Zwróć odpowiedź jako czysty obiekt JSON o następującej strukturze:
            {
              "title": "$title",
              "content": "$content",
              "translation": "",
              "comprehensionQuestionsList": [
                {
                  "question": "Nowe pytanie w języku $language",
                  "options": ["Opcja A", "Opcja B", "Opcja C"],
                  "answerIndex": 0,
                  "explanation": "Szczegółowe wyjaśnienie poprawnej odpowiedzi po polsku w odniesieniu do reguły gramatycznej"
                }
              ],
              "scenariosList": [
                {
                  "title": "Tytuł scenariusza po polsku",
                  "description": "Krótki opis scenariusza po polsku wyjaśniający kontekst i cel rozmowy",
                  "initialPromptInstruction": "Detailed instructions in English for Gemini of how it should act as a tutor and how to start the discussion for this scenario specifically, using ONLY single quotes for nested quotes."
                }
              ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.85f,
                maxOutputTokens = 3500,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext GeneratedStoryResponse(
                    title = title,
                    content = content,
                    translation = ""
                )
            }

            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = cleanJsonResponse(jsonText)
                storyAdapter.fromJson(cleaned) ?: GeneratedStoryResponse(
                    title = title,
                    content = content,
                    translation = ""
                )
            } else {
                GeneratedStoryResponse(
                    title = title,
                    content = content,
                    translation = ""
                )
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateMoreQuestionsAndScenarios failed", e)
            GeneratedStoryResponse(
                title = title,
                content = content,
                translation = ""
            )
        }
    }

    /**
     * Translates a single word or phrase within its sentence context, returning detailed analysis.
     */
    suspend fun translateSingleWord(
        word: String,
        sentenceContext: String,
        language: String
    ): WordTranslationResponse = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś zaawansowanym słownikiem dwujęzycznym (Polski <-> $language).
            Użytkownik wpisał słowo lub frazę: "$word" w kontekście: "${if(sentenceContext.isBlank()) "Brak kontekstu" else sentenceContext}".
            
            ZASADA KRYTYCZNA:
            1. Rozpoznaj język wpisanej frazy ("$word").
            2. Jeśli użytkownik wpisał słowo po polsku (np. "kot", "samochód"), MUSISZ przetłumaczyć je na język docelowy ($language) (np. "el gato", "el coche").
            3. Jeśli użytkownik wpisał słowo w języku $language, przetłumacz je na język polski.
            4. NIGDY nie zwracaj jako głównego tłumaczenia (pole "translation") tego samego języka co wejście.

            Zwróć odpowiedź jako czysty obiekt JSON o następującej strukturze:
            {
              "translation": "Główne tłumaczenie (jeśli wejście PL -> podaj w $language. Jeśli wejście $language -> podaj w PL)",
              "partOfSpeech": "Część mowy po polsku (np. rzeczownik, czasownik)",
              "polishExplanation": "Krótkie (1 zdanie) wyjaśnienie znaczenia po polsku",
              "alternativeMeanings": ["alternatywne tłumaczenie 1", "alternatywne tłumaczenie 2"],
              "contextUsageExplanation": "Zaawansowane wyjaśnienie po polsku dotyczące gramatyki, kontekstu i zastosowania."
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.3f,
                maxOutputTokens = 600,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext WordTranslationResponse(
                    translation = "Brak klucza API",
                    partOfSpeech = "-",
                    polishExplanation = "Skonfiguruj klucz API w ustawieniach aplikacji.",
                    alternativeMeanings = emptyList()
                )
            }

            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = cleanJsonResponse(jsonText)
                wordAdapter.fromJson(cleaned) ?: WordTranslationResponse(
                    translation = "Błąd parsowania",
                    partOfSpeech = "-",
                    polishExplanation = "Nie udało się sparsować odpowiedzi słownika.",
                    alternativeMeanings = emptyList()
                )
            } else {
                WordTranslationResponse(
                    translation = "Brak odpowiedzi",
                    partOfSpeech = "-",
                    polishExplanation = "AI nie zwróciło odpowiedzi słownikowej.",
                    alternativeMeanings = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "translateSingleWord failed", e)
            WordTranslationResponse(
                    translation = "Błąd",
                partOfSpeech = "-",
                polishExplanation = e.localizedMessage ?: "Błąd połączenia",
                alternativeMeanings = emptyList()
            )
        }
    }

    suspend fun translateSentenceToPolish(
        sentence: String,
        language: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś profesjonalnym tłumaczem językowym. Przetłumacz poniższe zdanie z języka $language na język polski.
            
            ZASADY SPECJALNE:
            1. Zwróć wyłącznie samo czyste, w 100% kompletne, dokończone i poprawne gramatycznie tłumaczenie bez cudzysłowów ani dodatkowych komentarzy.
            2. NIGDY nie ucinaj wypowiedzi w połowie zdania. NIGDY nie stawiaj wielokropka (...) na końcu ani nie pozostawiaj zdania niedokończonego. Zdanie musi brzmieć naturalnie, płynnie i logicznie po polsku od początku do samego końca.
            3. Rozpoznawaj regionalizmy i kulturowe zwroty:
               - W języku hiszpańskim z Andaluzji/Málagi, zwroty określające kawę takie jak: "un nube" (bardzo słaba kawa z dużą ilością mleka / dosł. chmurka), "un sombra" (kawa z mlekiem pół na pół), "un mitad" itp. powinny być tłumaczone jako specyficzna kawa (np. "kawa nube", "słaba kawa mleczna", "kawa z dużą ilością mleka"), a nie jako chmury ("nube") czy pianki ("marshmallow/pianka").
               
            Zdanie do przetłumaczenia: "$sentence"
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.2f,
                maxOutputTokens = 500
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext "Skonfiguruj klucz API"
            }
            val response = callGeminiWithFallback(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "Błąd tłumaczenia"
        } catch (e: Exception) {
            Log.e("TutorAiService", "translateSentenceToPolish failed", e)
            "Błąd połączenia"
        }
    }

    suspend fun getPronunciationTip(
        sentence: String,
        language: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś profesjonalnym trenerem wymowy i native speakerem języka $language. 
            Przeanalizuj poniższe zdanie i podaj krótki, konkretny, bardzo praktyczny i motywujący poradnik dotyczący wymowy dla polskiego ucznia.
            
            ZASADY SPECJALNE:
            1. Pisz wyłącznie w języku polskim.
            2. Twoja wypowiedź musi być krótka i zwięzła (maksymalnie 2-3 zdania).
            3. Wskaż 1-2 najtrudniejsze dźwięki w tym zdaniu i wytłumacz, jak je poprawnie wypowiedzieć (np. jak ułożyć język lub usta, co zaakcentować).
            4. Nie używaj skomplikowanych symboli fonetycznych IPA, chyba że są bardzo proste. Zamiast tego używaj intuicyjnego zapisu przybliżonego.
            
            Zdanie do analizy wymowy: "$sentence"
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.5f,
                maxOutputTokens = 300
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext "Skonfiguruj klucz API w ustawieniach (Goals), aby otrzymać indywidualne wskazówki od Trenera AI!"
            }
            val response = callGeminiWithFallback(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "Błąd generowania wskazówek."
        } catch (e: Exception) {
            Log.e("TutorAiService", "getPronunciationTip failed", e)
            "Błąd połączenia z serwerem AI. Sprawdź swój klucz API w zakładce Goals."
        }
    }

    suspend fun generateRichMnemonic(
        wordOrPhrase: String,
        language: String,
        contextSentence: String? = null
    ): RichMnemonicResponse? = withContext(Dispatchers.IO) {
        val contextInfo = if (!contextSentence.isNullOrBlank()) {
            "Kontekst opowiadania lub zdania: \"$contextSentence\""
        } else "Brak dodatkowego kontekstu (samodzielne słowo/fraza)."

        val prompt = """
            Jesteś wybitnym twórcą mnemotechnik językowych i dwujęzycznym lingwistą dla języka docelowego: $language.
            Użytkownik wprowadza zapytanie (słowo, frazę lub całe zdanie): "$wordOrPhrase".
            $contextInfo

            KRYTYCZNE ZASADY ROZPOZNAWANIA JĘZYKA (DWUKIERUNKOWOŚĆ JAK W SŁOWNIKU LINGO-AI):
            1. Rozpoznaj język zapytania ("$wordOrPhrase").
            2. JEŚLI UŻYTKOWNIK WPISAŁ SŁOWO PO POLSKU (np. "portfel", "samochód", "pies", "zgubić się"):
               - Przetłumacz je na JĘZYK DOCELOWY ($language) (np. "la cartera", "el coche", "el perro", "perderse") i umieść w polu "word".
               - W polu "translation" umieść polskie słowo/znaczenie (np. "portfel", "samochód", "pies", "zgubić się").
            3. JEŚLI UŻYTKOWNIK WPISAŁ SŁOWO W JĘZYKU DOCELOWYM ($language) (np. "pierde", "cartera", "coche"):
               - Umieść je w poprawnej formie w polu "word".
               - W polu "translation" umieść zwięzłe, proste polskie tłumaczenie (np. "gubi, traci", "portfel / torebka", "samochód").
            4. NIGDY nie umieszczaj polskiego słowa w polu "word". Pole "word" to ZAWSZE słowo w języku $language.
            5. Pole "translation" to ZAWSZE polskie znaczenie (maksymalnie 1-3 słowa).

            ZASADY TWORZENIA MNEMOTECHNIKI:
            - "association": Stwórz genialne, zabawne, absurdalne lub wyraziste skojarzenie po polsku łączące brzmienie lub zapis obcego słowa ($language) z polskim słowem lub sceną (Metoda Słów Kluczowych).
            - "emojis": 3 do 5 powiązanych kolorowych emoji tworzących żywą scenę wizualną.
            - "visualScene": Krótki (1 zdanie) pobudzający wyobraźnię opis sceny z emoji, którą uczeń ma natychmiast zobaczyć oczyma wyobraźni.
            - "situation": Krótkie wyjaśnienie w jakiej codziennej sytuacji życiowej używa się tego słowa.
            - "contextSentence": Kompletne, naturalne zdanie przykładowe w języku $language.
            - "contextTranslation": Pełne, dokończone tłumaczenie tego zdania na język polski (BEZ wielokropków i ucinania).

            Zwróć odpowiedź WYŁĄCZNIE jako czysty obiekt JSON o strukturze:
            {
              "word": "słowo w języku $language",
              "translation": "tłumaczenie po polsku",
              "contextSentence": "zdanie przykładowe w języku $language",
              "contextTranslation": "polskie tłumaczenie zdania",
              "situation": "wyjaśnienie sytuacji użycia",
              "association": "zabawne/absurdalne skojarzenie po polsku łączące brzmienie ze znaczeniem",
              "emojis": "🛍️ 💳 🏃‍♂️ 💰",
              "visualScene": "krótki opis sceny wizualnej"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                maxOutputTokens = 1000,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrBlank()) {
                val cleaned = cleanJsonResponse(jsonText)
                try {
                    richMnemonicAdapter.fromJson(cleaned)
                } catch (e: Exception) {
                    val repaired = repairAndCleanJson(jsonText)
                    richMnemonicAdapter.fromJson(repaired)
                }
            } else null
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateRichMnemonic failed", e)
            null
        }
    }

    suspend fun translateMistakeToPolish(
        correctedSentence: String,
        explanation: String
    ): MistakeTranslationResponse? = withContext(Dispatchers.IO) {
        val prompt = """
            Przetłumacz poniższe poprawne zdanie oraz wyjaśnienie błędu językowego na język polski.
            Zdanie: "$correctedSentence"
            Wyjaśnienie: "$explanation"
            
            Zwróć odpowiedź jako czysty obiekt JSON o strukturze:
            {
              "translatedSentence": "tłumaczenie zdania na polski",
              "translatedExplanation": "wyjaśnienie błędu po polsku"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.3f,
                maxOutputTokens = 600,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrBlank()) {
                val cleaned = cleanJsonResponse(jsonText)
                try {
                    mistakeTranslationAdapter.fromJson(cleaned)
                } catch (e: Exception) {
                    val repaired = repairAndCleanJson(jsonText)
                    mistakeTranslationAdapter.fromJson(repaired)
                }
            } else null
        } catch (e: Exception) {
            Log.e("TutorAiService", "translateMistakeToPolish failed", e)
            null
        }
    }

    /**
     * Variable-Context Dynamic SRS Context Generator.
     * Generates a completely new, unique target-language sentence containing the target word,
     * along with its Polish translation and kognitive interference tags.
     */
    suspend fun generateDynamicSrsContext(
        word: String,
        translation: String,
        language: String,
        currentContext: String
    ): DynamicContextResponse = withContext(Dispatchers.IO) {
        val systemInstruction = """
            Jesteś wybitnym ekspertem neurodydaktyki i lingwistyki EdTech. Twoim zadaniem jest ułatwienie głębokiego zapamiętywania (Active Recall SRS) u polskiego użytkownika.
            Otrzymujesz słowo kluczowe w języku $language, jego polskie tłumaczenie oraz dotychczasowy kontekst zdaniowy.
            Musisz wygenerować całkowicie nowe, unikalne i żywe zdanie w języku $language, które osadza to słowo w zupełnie innym kontekście gramatycznym lub sytuacyjnym niż dotychczasowy, aby zmusić mózg do elastycznej rekonstrukcji znaczenia.
            Dodatkowo musisz podać polskie tłumaczenie tego nowego zdania.
            Kluczowe jest również wykrycie i oznaczenie potencjalnego błędu interference (kalka językowa, false friends, błędne skojarzenia gramatyczne z języka polskiego). Przypisz krótki, czytelny tag w języku polskim opisujący tę trudność (np. "kalka_konstrukcyjna", "false_friends", "be_vs_have", "rodzajnik", "preposition_trap" lub "none" jeśli brak interferencji).

            Zwróć odpowiedź WYŁĄCZNIE jako obiekt JSON o następującej strukturze:
            {
              "newSentence": "nowe unikalne zdanie w języku obcym",
              "translation": "polskie tłumaczenie tego nowego zdania",
              "interferenceTag": "krótki tag interferencji kognitywnej"
            }
        """.trimIndent()

        val prompt = """
            Słowo: "$word"
            Tłumaczenie: "$translation"
            Język docelowy: $language
            Dotychczasowy kontekst: "$currentContext"
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.85f,
                maxOutputTokens = 500,
                responseMimeType = "application/json"
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext DynamicContextResponse(currentContext, translation, "brak_klucza_api")
            }
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrBlank()) {
                val cleaned = cleanJsonResponse(jsonText)
                val parsed = try {
                    dynamicContextAdapter.fromJson(cleaned)
                } catch (e: Exception) {
                    try {
                        val repaired = repairAndCleanJson(jsonText)
                        dynamicContextAdapter.fromJson(repaired)
                    } catch (e2: Exception) {
                        null
                    }
                }
                parsed ?: DynamicContextResponse(
                    newSentence = currentContext,
                    translation = "Błąd parsowania nowego kontekstu.",
                    interferenceTag = "parsing_error"
                )
            } else {
                DynamicContextResponse(
                    newSentence = currentContext,
                    translation = translation,
                    interferenceTag = "brak_odpowiedzi_ai"
                )
            }
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateDynamicSrsContext failed", e)
            DynamicContextResponse(
                newSentence = currentContext,
                translation = translation,
                interferenceTag = "error_${e.localizedMessage?.take(15) ?: "unknown"}"
            )
        }
    }

    /**
     * Generates a comprehensive, immersive language-cultural study module for a given city, country, and CEFR level,
     * following the expert concentric pedagogical model.
     */
    suspend fun generateCityModule(
        city: String,
        country: String,
        cefrLevel: String
    ): String = withContext(Dispatchers.IO) {
        val levelExplanation = when (cefrLevel.uppercase()) {
            "A1", "A2" -> "Poziom A1-A2 (Mikrośrodowisko - Miasto i Przetrwanie): Treść kulturowa: Topografia $city, lokalna kuchnia, podstawowe zwyczaje, kluczowe punkty orientacyjne, nawigacja. Treść językowa: Czas teraźniejszy, podstawowe czasy przeszłe, słownictwo miejskie, codzienne transakcje."
            "B1", "B2" -> "Poziom B1-B2 (Mezośrodowisko - Region i Historia): Treść kulturowa: Znaczenie regionu dla historii $country, lokalni bohaterowie/twórcy, mniejszości, dialektyzmy i akcent, wpływ ważnych wydarzeń historycznych na dzisiejszą architekturę i kulturę. Treść językowa: Tryb łączący (Subjuntivo), zaawansowane czasy przeszłe, wyrażanie opinii, lokalne idiomy z obszaru $city."
            "C1", "C2" -> "Poziom C1-C2 (Makrośrodowisko - Kraj, Geopolityka i Krytyka): Treść kulturowa: Współczesne problemy społeczno-gospodarcze (gentryfikacja, bezrobocie, polityka) dotyczące $city na tle $country, socjolingwistyka (lokalny slang uliczny vs. język akademicki), analiza literatury lub tekstów publicystycznych z regionu. Treść językowa: Niuanse stylistyczne, pełne spektrum warunkowe, żargon, wyrażenia idiomatyczne, słownictwo abstrakcyjne i analityczne."
            else -> ""
        }

        val prompt = """
            Jesteś Głównym Architektem Edukacji Językowo-Kulturowej (Poziom: Ekspert). Twoim zadaniem jest generowanie kompleksowych, immersyjnych modułów nauki języka hiszpańskiego, w których rozwój gramatyczny i leksykalny jest ściśle powiązany z geografią, historią, kulturą i socjologią wskazanego przez użytkownika MIEJSCA (Miasta i Kraju).

            DANE WEJŚCIOWE:
            - MIASTO: $city
            - KRAJ: $country
            - POZIOM CEFR: $cefrLevel
            - RAMY POZIOMU: $levelExplanation

            INSTRUKCJA:
            Wygeneruj moduł lekcyjny dla wskazanych zmiennych wejściowych. Jeśli MIASTO ($city) jest zbyt małe, aby wygenerować głębokie analizy dla poziomów B2-C2, zjawisko to należy płynnie rozszerzyć na cały region lub KRAJ ($country), informując o tym użytkownika na samym początku tła kulturowego.

            Twoja odpowiedź musi być sformatowana w poniższy sposób (używaj Markdown):

            ## Moduł: $city, $country - Poziom $cefrLevel

            ### 1. Kontekst Kulturowo-Historyczny (Tło)
            [Zwięzły opis zawierający dokładnie 2-3 bogate akapity osadzający lekcję w konkretnym miejscu, realiach, historii i kulturze miasta lub regionu.]

            ### 2. Cel Lingwistyczny
            - **Gramatyka:** [Konkretne struktury gramatyczne dla poziomu $cefrLevel związane z tym tematem]
            - **Słownictwo:** [Zakres tematyczny słownictwa]

            ### 3. Sytuacja / Tekst Źródłowy
            [Wygeneruj pasjonujący, naturalny tekst, dialog, artykuł prasowy lub symulację sytuacji osadzoną w mieście $city, adekwatną do poziomu $cefrLevel, zawierającą lokalne elementy i docelowe struktury gramatyczne.]

            ### 4. Analiza Lokalnego Słownictwa i Dialektu
            | Słowo / Wyrażenie | Znaczenie | Uniwersalny odpowiednik (Estándar) | Wyjaśnienie i kontekst lokalny |
            | --- | --- | --- | --- |
            [Wygeneruj tabelę Markdown zawierającą minimum 5 najważniejszych słów lub wyrażeń charakterystycznych dla miasta $city, regionu lub kraju $country, z ich znaczeniem, uniwersalnym hiszpańskim odpowiednikiem oraz ciekawym wyjaśnieniem kulturowym / kontekstem lokalnym. Upewnij się, że tabela ma dokładnie taki format Markdown.]

            ### 5. Zadanie Analityczne dla Użytkownika
            [Konkretne, angażujące zadanie pisemne lub konwersacyjne zmuszające użytkownika do aktywnego użycia poznanej gramatyki i słownictwa w kontekście wygenerowanego tła historyczno-kulturowego.]
            
            Odpowiedz wyłącznie po polsku (poza tekstami hiszpańskimi w sekcjach 3 i 4). Zadbaj o to, aby cała treść i wszystkie zdania były w 100% kompletne, poprawnie dokończone, bez uciętych wyrazów czy wielokropków (...) na końcach!
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.8f,
                maxOutputTokens = 2000
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext "Błąd: Brak klucza API Gemini. Skonfiguruj go w ustawieniach aplikacji."
            }

            val response = callGeminiWithFallback(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Nie udało się wygenerować modułu lekcyjnego. Spróbuj ponownie."
        } catch (e: Exception) {
            Log.e("TutorAiService", "City module generation failed", e)
            "Wystąpił błąd podczas łączenia z AI: ${e.localizedMessage}. Upewnij się, że masz poprawny klucz API i połączenie z internetem."
        }
    }

    /**
     * Generates a structured City Hub (tagline, cultural overview, 3 key phrases/slang with context, and 3 rich roleplay scenarios)
     * for any requested city in the world, matching the user's target language and CEFR level.
     */
    suspend fun generateStructuredCityHub(
        city: String,
        country: String,
        targetLanguage: String,
        cefrLevel: String
    ): CityHubData = withContext(Dispatchers.IO) {
        val cleanCity = city.trim().replaceFirstChar { it.uppercase() }
        val cleanCountry = if (country.isNotBlank()) country.trim().replaceFirstChar { it.uppercase() } else "Lokalny Region"
        val lang = if (targetLanguage.isNotBlank()) targetLanguage else "Spanish"
        val level = if (cefrLevel.isNotBlank()) cefrLevel else "A2"
        val slug = cleanCity.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")

        val prompt = """
            Jesteś Głównym Architektem Edukacji Językowo-Kulturowej i Przewodnikiem po miastach świata.
            Użytkownik chce uczyć się języka: "$lang" na poziomie CEFR: "$level", eksplorując miasto: "$cleanCity" ($cleanCountry).

            ZADANIE:
            Wygeneruj bogatą, gotową do natychmiastowej rozgrywki kartę eksploracji miasta w formacie JSON (City Hub).

            KRYTYCZNE WYMAGANIA DANYCH:
            1. "keyVocab" - DOKŁADNIE 3 autentyczne, przydatne zwroty, idiomy, regionalizmy lub slang typowe dla tego miasta / kraju / kultury w języku docelowym ("$lang"):
               - "word": wyrażenie w języku docelowym ($lang)
               - "translation": zwięzłe polskie tłumaczenie
               - "context": naturalne zdanie przykładowe w języku docelowym ($lang)
            2. "scenarios" - DOKŁADNIE 3 wciągające, praktyczne i realistyczne scenariusze konwersacyjne do odegrania w $cleanCity:
               - "title": krótki, chwytliwy tytuł po polsku z emoji (np. "Zamawianie w Tradycyjnym Pubie 🍺")
               - "subtitle": 1-2 zdaniowa misja komunikacyjna po polsku (dokładny cel rozmowy)
               - "difficulty": oznaczenie poziomu trudności (np. "$level" lub "A1–A2", "A2–B1", "B1–B2")
               - "emoji": pasujące emoji
               - "key": unikalny identyfikator, np. "custom_city_${slug}_sc1", "custom_city_${slug}_sc2", "custom_city_${slug}_sc3"
               - "initialPrompt": instrukcja po angielsku dla AI tutora, w jakiej roli występuje i jak ma rozpocząć konwersację w języku $lang
            3. "name": "$cleanCity"
            4. "country": "$cleanCountry"
            5. "icon": pasująca flaga lub charakterystyczne emoji miasta
            6. "tagline": krótki, 1-zdaniowy chwytliwy slogan oddający klimat miasta po polsku
            7. "description": 2 zwięzłe zdania wprowadzenia kulturowo-językowego po polsku

            Format wyjściowy (WYŁĄCZNIE czysty JSON):
            {
              "id": "custom_$slug",
              "name": "$cleanCity",
              "country": "$cleanCountry",
              "icon": "🏙️",
              "tagline": "Klimatyczne motto miasta",
              "description": "Zwięzły opis klimatu, historii i specyfiki językowej...",
              "keyVocab": [
                {
                  "word": "Fraza w języku $lang",
                  "translation": "Tłumaczenie po polsku",
                  "context": "Zdanie przykładowe w $lang"
                }
              ],
              "scenarios": [
                {
                  "title": "Tytuł po polsku 🎭",
                  "subtitle": "Opis misji i celu rozmowy po polsku",
                  "key": "custom_city_${slug}_sc1",
                  "difficulty": "$level",
                  "emoji": "🎭",
                  "initialPrompt": "You are a friendly local in $cleanCity. Greet the student warmly in $lang at CEFR $level and ask an engaging opening question."
                }
              ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                maxOutputTokens = 2500,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext fallbackCityHub(cleanCity, cleanCountry, lang, level, slug)
            }
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrBlank()) {
                val cleaned = cleanJsonResponse(jsonText)
                val parsed = cityHubAdapter.fromJson(cleaned)
                if (parsed != null && parsed.scenarios.isNotEmpty() && parsed.keyVocab.isNotEmpty()) {
                    return@withContext parsed
                }
            }
            fallbackCityHub(cleanCity, cleanCountry, lang, level, slug)
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateStructuredCityHub failed", e)
            fallbackCityHub(cleanCity, cleanCountry, lang, level, slug)
        }
    }

    private fun fallbackCityHub(
        city: String,
        country: String,
        targetLanguage: String,
        cefrLevel: String,
        slug: String = city.lowercase().replace(" ", "_")
    ): CityHubData {
        val isEnglish = targetLanguage.equals("English", ignoreCase = true)
        val isGerman = targetLanguage.equals("German", ignoreCase = true)
        val isFrench = targetLanguage.equals("French", ignoreCase = true)
        val isItalian = targetLanguage.equals("Italian", ignoreCase = true)

        val sampleVocab = when {
            isEnglish -> listOf(
                CityVocabItem("Cheers, mate!", "Dzięki / Na zdrowie (nieformalne)", "Here is your coffee. — Cheers, mate!"),
                CityVocabItem("Could I have the bill, please?", "Czy mogę prosić o rachunek?", "Excuse me, could I have the bill, please?"),
                CityVocabItem("How do I get to...", "Jak dojdę do...", "Excuse me, how do I get to the nearest station?")
            )
            isGerman -> listOf(
                CityVocabItem("Servus / Hallo!", "Cześć / Dzień dobry", "Servus! Wie geht es dir heute?"),
                CityVocabItem("Die Rechnung, bitte", "Rachunek, proszę", "Entschuldigung, die Rechnung bitte!"),
                CityVocabItem("Wo ist die Haltestelle?", "Gdzie jest przystanek?", "Entschuldigung, wo ist die nächste Haltestelle?")
            )
            isFrench -> listOf(
                CityVocabItem("L'addition, s'il vous plaît", "Rachunek, proszę", "Pardon, l'addition s'il vous plaît."),
                CityVocabItem("C'est délicieux!", "To jest pyszne!", "Ce plat traditionnel est vraiment délicieux!"),
                CityVocabItem("Où se trouve...", "Gdzie znajduje się...", "Pardon, où se trouve le musée?")
            )
            isItalian -> listOf(
                CityVocabItem("Il conto, per favore", "Rachunek, proszę", "Scusi, possiamo avere il conto, per favore?"),
                CityVocabItem("Che bello!", "Jak pięknie! / Wspaniale!", "Che bello questo posto storico!"),
                CityVocabItem("Un caffè al banco", "Kawa przy barze", "Prendo un caffè al banco, grazie.")
            )
            else -> listOf(
                CityVocabItem("La cuenta, por favor", "Rachunek, proszę", "Perdone, ¿nos trae la cuenta, por favor?"),
                CityVocabItem("¡Qué rico!", "Jakie pyszne!", "Este plato tradicional está muy rico."),
                CityVocabItem("¿Dónde está la estación?", "Gdzie jest stacja?", "Disculpe, ¿dónde está la estación más cercana?")
            )
        }

        val sampleScenarios = listOf(
            CityScenarioItem(
                title = "Kawiarnia i Lokalne Przysmaki ☕",
                subtitle = "Złóż zamówienie w tradycyjnej kawiarni w centrum miasta $city i zapytaj o specjalność dnia.",
                key = "custom_city_${slug}_cafe",
                difficulty = cefrLevel,
                emoji = "☕",
                initialPrompt = "You are a welcoming barista in $city. Greet the customer warmly in $targetLanguage and ask what they would like to order."
            ),
            CityScenarioItem(
                title = "Zwiedzanie i Punkty Widokowe 🏛️",
                subtitle = "Kup bilety na słynną atrakcję w $city, dopytaj o godziny otwarcia i zniżki.",
                key = "custom_city_${slug}_sightseeing",
                difficulty = cefrLevel,
                emoji = "🏛️",
                initialPrompt = "You work at the visitor info desk in $city. Greet the visitor in $targetLanguage and help them plan their tour."
            ),
            CityScenarioItem(
                title = "Wieczorny Spacer i Kolacja 🍽️",
                subtitle = "Zarezerwuj stolik w klimatycznej restauracji i poproś o rekomendację regionalnego dania.",
                key = "custom_city_${slug}_dinner",
                difficulty = cefrLevel,
                emoji = "🍽️",
                initialPrompt = "You are a host at a popular authentic restaurant in $city. Welcome the guest in $targetLanguage and seat them at a table."
            )
        )

        return CityHubData(
            id = "custom_$slug",
            name = city,
            country = country,
            icon = "🏙️",
            tagline = "Odkryj klimat i język $city",
            description = "Eksploruj codzienne życie, kulturę i autentyczne sytuacje komunikacyjne w mieście $city ($country).",
            keyVocab = sampleVocab,
            scenarios = sampleScenarios
        )
    }

    suspend fun fetchSmartDictionaryEntry(
        query: String,
        language: String,
        currentStoryContext: String? = null
    ): SmartDictionaryResponse = withContext(Dispatchers.IO) {
        val contextPrompt = if (!currentStoryContext.isNullOrBlank()) {
            "Kontekst opowiadania lub rozmowy, w którym słowo/fraza występuje: \"$currentStoryContext\""
        } else {
            "Brak dodatkowego kontekstu (ogólny słownik)."
        }

        val prompt = """
            Jesteś wybitnym, ultra-precyzyjnym słownikiem kontekstowym dla języka $language. 
            Użytkownik wyszukuje słowo lub frazę: "$query". 
            
            $contextPrompt

            ZASADA KRYTYCZNA:
            1. Rozpoznaj język wpisanej frazy ("$query").
            2. Jeśli użytkownik wpisał słowo po polsku (np. "kot", "samochód"), MUSISZ przetłumaczyć je na język docelowy ($language) (np. "el gato", "el coche") i umieścić w polu "translated_text".
            3. Jeśli użytkownik wpisał słowo w języku $language, umieść je poprawnie zapisane w polu "translated_text".
            4. NIGDY nie zwracaj w "translated_text" słowa po polsku, musi to być ZAWSZE język docelowy ($language).
            5. Pole "definition_pl" MUSI zawierać WYŁĄCZNIE proste tłumaczenie słowa na język polski (maksymalnie 1-2 słowa, np. "małpa"). Kategorycznie zabrania się umieszczania encyklopedycznych lub opisowych definicji (takich jak "ssak naczelny...").

            Zwróć odpowiedź WYŁĄCZNIE jako czysty obiekt JSON o następującej strukturze (żadnych innych tekstów poza JSON-em):
            {
              "translated_text": "czyste tłumaczenie na język docelowy ($language)",
              "phonetic": "zapis fonetyczny dla słowa w języku docelowym",
              "part_of_speech": "część mowy po polsku (np. rzeczownik, czasownik)",
              "definition_pl": "proste i krótkie tłumaczenie po polsku (1-2 słowa)",
              "example_target": "zdanie przykładowe w języku docelowym",
              "example_pl": "tłumaczenie zdania przykładowego"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.2f,
                maxOutputTokens = 800
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext SmartDictionaryResponse(
                    translated_text = query,
                    definition_pl = "Błąd: Brak klucza API",
                    phonetic = "",
                    part_of_speech = "Skonfiguruj klucz API w ustawieniach.",
                    example_target = "",
                    example_pl = ""
                )
            }
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val cleaned = cleanJsonResponse(jsonText)
            val parsed = try {
                smartDictionaryAdapter.fromJson(cleaned)
            } catch (e: Exception) {
                try {
                    val repaired = repairAndCleanJson(jsonText)
                    smartDictionaryAdapter.fromJson(repaired)
                } catch (e2: Exception) {
                    null
                }
            }
            parsed ?: SmartDictionaryResponse(
                translated_text = query,
                definition_pl = "Błąd parsowania",
                phonetic = "",
                part_of_speech = "Nie udało się sparsować odpowiedzi od AI.",
                example_target = "",
                example_pl = ""
            )
        } catch (e: Exception) {
            Log.e("TutorAiService", "fetchSmartDictionaryEntry failed", e)
            SmartDictionaryResponse(
                translated_text = query,
                definition_pl = "Błąd połączenia",
                phonetic = "",
                part_of_speech = e.localizedMessage ?: "Problem z połączeniem internetowym.",
                example_target = "",
                example_pl = ""
            )
        }
    }

    suspend fun generateShadowingSegment(
        topic: String,
        targetLanguage: String,
        adaptiveContext: String? = null
    ): ShadowingSegmentResponse = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś genialnym przewodnikiem językowym, ekspertem metody Shadowing (cienowania językowego) oraz naturalnej akwizycji językowej (tak jak dziecko przyswaja język – poprzez emocje, rytm i intuicyjne wzorce, a nie suche regułki).

            Twoim zadaniem jest wygenerowanie niesamowicie naturalnego, żywego i autentycznego tekstu w języku obcym ($targetLanguage) na temat: "$topic".

            ZASADY DLA TEKSTU GŁÓWNEGO (foreignText):
            1. 100% NATURAlNOŚĆ: Tekst musi brzmieć tak, jakby wypowiedział go native speaker w swobodnej, emocjonalnej rozmowie z przyjacielem, a NIE w podręczniku.
            2. ELEMENTY MÓWIONEGO JĘZYKA: Wpleć naturalne wtrącenia, skróty, potoczne zwroty i tzw. 'fillers' (np. dla angielskiego: 'well', 'actually', 'you know'; dla hiszpańskiego: 'pues', 'mira', 'bueno', 'a ver'; dla francuskiego: 'en fait', 'bah', 'tu sais'; dla włoskiego: 'allora', 'vabbè', 'insomma').
            3. DŁUGOŚĆ: Krótki i rytmiczny, idealny do powtarzania na głos, trwający 10-25 sekund (ok. 20-45 słów).
            4. TŁUMACZENIE (nativeTranslation): Przetłumacz tekst na język polski w sposób żywy, oddający emocjonalny sens i klimat, unikając dosłowności.

            ZASADY DLA ETAPÓW NAUKI (Zwróć w obiekcie JSON):

            1. KEY PHRASES (Wyspy Językowe):
               Wygeneruj 3-4 gotowe, kompletne i niezwykle przydatne zdania konwersacyjne (Wyspy Językowe / Master chunks) bezpośrednio powiązane z klimatem tego tematu, które użytkownik może natychmiast zapamiętać i użyć w realnym życiu. Żadnych pojedynczych słówek – wyłącznie całe, naturalne zwroty! Podaj ich żywe polskie tłumaczenie.

            2. RETRIEVED QUESTIONS (Conversational Checkpoints):
               Zamiast nudnych szkolnych pytań o suche fakty, wygeneruj 2 intuicyjne, swobodne pytania (w języku $targetLanguage) sprawdzające odczucia, intencje bohatera lub naturalną reakcję na opisaną sytuację wraz z bardzo naturalną, modelową odpowiedzią native speakera.

            3. TRANSFORMATIONS (Organic Phrase Evolution):
               Zbuduj sekwencję 5 wariantów zdań, pokazujących jak rozwija się myśl od prostego impulsu do bogatej, swobodnej wypowiedzi (dokładnie tak jak dziecko rozbudowuje swoje zdania).
               ZAKAZ powtarzania tego samego początku zdania lub czasu w każdym kroku! Każde zdanie musi brzmieć inaczej, świeżo i dynamicznie:
               - Krok 1 (Baza): Krótki, naturalny impuls konwersacyjny.
               - Krok 2 (Rozszerzenie): Dodanie emocji, intencji lub swobodnego okolicznika.
               - Krok 3 (Zmiana perspektywy/czasu): Przeniesienie akcji na inną osobę, zmianę nastroju lub czasu gramatycznego.
               - Krok 4 (Reakcja lub Pytanie): Żywa reakcja, zdziwienie lub pytanie native speakera wynikające z tej sytuacji.
               - Krok 5 (Personalizacja): Odniesienie tego motywu bezpośrednio do codziennego życia użytkownika.

            4. COMMUNICATION SCENARIO (Live Roleplay Scenario):
               Stwórz pasjonujący, konkretny i pobudzający wyobraźnię polski opis scenariusza konwersacyjnego na żywo (np. 'Wcielasz się w rolę klienta, który właśnie odkrył, że...'). Musi on zachęcać do aktywnego użycia poznanych Wysp Językowych w swobodnej konwersacji z AI.

            5. GRAMMAR EXPLANATION (Intuicyjny Przewodnik "Mów jak Native" w formacie Markdown):
               Napisz pasjonujące, żywe i niezwykle praktyczne objaśnienie po polsku w polu "grammarExplanation" (użyj nagłówków ### i wypunktowań). Zastąp nudną teorię szkolną intuicją i melodią:
               - ### 💡 Jak to działa w prawdziwym życiu?
                 Wyjaśnij intencję i emocje stojące za użytymi strukturami. Dlaczego native speaker woli ten zwrot zamiast dosłownego, sztywnego tłumaczenia ze słownika? Jakie niesie to za sobą niuanse kulturowe lub towarzyskie?
               - ### 🎵 Melodia, Łączenie Wyrazów i Akcent
                 Pokaż użytkownikowi, jak połączyć te słowa, aby brzmiały płynnie (np. elizja, ściągnięcia, łączenie spółgłosek z samogłoskami, intonacja wznosząca/opadająca). Napisz, jak uniknąć twardego, nienaturalnego akcentu i brzmieć jak native speaker.
               - ### 🚫 Podręcznik vs. Ulica
                 Przedstaw kontrast: jak uczy sztywny, nudny podręcznik gramatyki, a jak naprawdę mówi się na ulicy w Paryżu, Madrycie, Rzymie czy Londynie. Pokaż tę różnicę!
               - ### ✨ Dodatkowe Kotwice w Akcji
                 Podaj 3-4 gotowe, super-naturalne przykłady użycia tego samego wzorca w zupełnie innych, codziennych sytuacjach (z polskim tłumaczeniem).

            Zwróć odpowiedź WYŁĄCZNIE jako czysty, poprawnie sformatowany obiekt JSON. Nie używaj znaczników ```json ani żadnych innych znaków poza samym obiektem JSON. Upewnij się, że wszystkie cudzysłowy wewnątrz wartości tekstowych są poprawnie ucieczkowe (\"), aby zapobiec błędom parsowania.

            STRUKTURA JSON:
            {
              "foreignText": "tekst w języku obcym ($targetLanguage)",
              "nativeTranslation": "naturalne i żywe tłumaczenie na język polski",
              "keyPhrases": [
                {
                  "phrase": "całe zdanie / naturalna wyspa językowa w języku $targetLanguage",
                  "translation": "precyzyjne, żywe tłumaczenie na język polski"
                }
              ],
              "retrievedQuestions": [
                {
                  "question": "pytanie sprawdzające wyczucie językowe / intencję w języku $targetLanguage",
                  "answer": "naturalna odpowiedź native speakera w języku $targetLanguage"
                }
              ],
              "transformations": [
                {
                  "type": "np. Impuls / Emocje / Zmiana Czasu / Żywa Reakcja / Życie Codzienne",
                  "transformedText": "zdanie w języku $targetLanguage",
                  "translation": "polskie tłumaczenie zdania",
                  "instruction": "żywy, krótki komentarz po polsku dotyczący płynności, zmiany perspektywy lub zastosowania"
                }
              ],
              "communicationScenario": "polski opis scenariusza do konwersacji na żywo",
              "adaptiveAnchorInfo": "opis po polsku wyjaśniający, które osobiste kotwice, zainteresowania lub błędy użytkownika zostały organicznie wplecione w ten materiał",
              "grammarExplanation": "Pełny, fascynujący przewodnik w formacie Markdown (### 💡 Jak to działa..., ### 🎵 Melodia..., ### 🚫 Podręcznik vs. Ulica..., ### ✨ Dodatkowe Kotwice...)"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                maxOutputTokens = 2500,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext ShadowingSegmentResponse(
                    foreignText = "Błąd: Brak klucza API Gemini",
                    nativeTranslation = "Skonfiguruj swój klucz API w ustawieniach aplikacji, aby korzystać z silnika Shadowing.",
                    keyPhrases = emptyList(),
                    retrievedQuestions = emptyList(),
                    transformations = emptyList(),
                    communicationScenario = "Brak połączenia z AI"
                )
            }
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val cleaned = cleanJsonResponse(jsonText)
            
            val parsed = try {
                shadowingSegmentAdapter.fromJson(cleaned)
            } catch (e1: Exception) {
                Log.w("TutorAiService", "Standard shadowing JSON parse failed: ${e1.message}, trying repaired JSON...")
                try {
                    val repaired = repairAndCleanJson(jsonText)
                    shadowingSegmentAdapter.fromJson(repaired)
                } catch (e2: Exception) {
                    Log.w("TutorAiService", "Repaired shadowing JSON parse failed: ${e2.message}, using fallback regex extraction")
                    tryFallbackShadowingParse(jsonText)
                }
            }
            
            parsed ?: tryFallbackShadowingParse(jsonText) ?: ShadowingSegmentResponse(
                foreignText = "Błąd parsowania",
                nativeTranslation = "Nie udało się sparsować wygenerowanego segmentu.",
                keyPhrases = emptyList(),
                retrievedQuestions = emptyList(),
                transformations = emptyList(),
                communicationScenario = "Błąd"
            )
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateShadowingSegment failed", e)
            ShadowingSegmentResponse(
                foreignText = "Błąd generowania: ${e.localizedMessage}",
                nativeTranslation = "Wystąpił problem podczas łączenia z usługą AI.",
                keyPhrases = emptyList(),
                retrievedQuestions = emptyList(),
                transformations = emptyList(),
                communicationScenario = "Błąd połączenia z AI"
            )
        }
    }

    suspend fun processSongLyric(
        lyricsText: String,
        targetLanguage: String
    ): ShadowingSegmentResponse = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś wybitnym pedagogiem językowym, kognitywistą i ekspertem muzycznym.
            Przeanalizuj poniższy tekst piosenki (liryki) w języku obcym ($targetLanguage):
            "$lyricsText"

            Zadania:
            1. Podziel tekst na logiczne, czytelne zwrotki lub refreny (stanzas/choruses) dostosowane do metody shadowing (ok. 20-50 słów). Połącz je w jeden zwięzły tekst do ćwiczeń.
            2. Wygeneruj precyzyjne, literackie i zarazem wierne tłumaczenie tego tekstu na język polski.
            3. Wyjaśnij slangowe zwroty, idiomy, nawiązania kulturowe oraz metafory użyte w tekście piosenki i umieść to wyjaśnienie w polu "poeticContext" po polsku.
            4. AI ACCELERATOR (WYSPY JĘZYKOWE & PEŁNE ZDANIA - MASTER): Wygeneruj 3-4 PEŁNE zdania lub wyspy konwersacyjne oparte na utworze, gotowe do natychmiastowego użycia i zapisu do bazy Wysp.
            5. RETRIEVE: Stwórz 2 pytania po $targetLanguage o treść lub interpretację piosenki wraz z modelowymi odpowiedziami.
            6. TRANSFORM: Przekształć wybrane wersy (np. zmieniając styl z poetyckiego na mowę potoczną, zmieniając czas lub osobę). Podaj typ, przekształcony tekst, tłumaczenie i instrukcję.
            7. COMMUNICATE: Zadaj specjalny zestaw pytań / wyzwanie dyskusyjne (po polsku) poświęcone głębszemu znaczeniu tego utworu i emocjom, które wywołuje (np. "Wyobraź sobie, że rozmawiasz z wykonawcą i pytasz o inspirację do tego utworu...").

            Zwróć odpowiedź WYŁĄCZNIE jako czysty obiekt JSON o strukturze (bez bloku ```json, bez wstępów czy podsumowań). Używaj pojedynczych apostrofów zamiast podwójnych cudzysłowów wewnątrz wartości tekstowych:
            {
              "foreignText": "tekst piosenki w języku obcym ($targetLanguage)",
              "nativeTranslation": "tłumaczenie całego tekstu piosenki na język polski",
              "keyPhrases": [
                {
                  "phrase": "pełne zdanie / wyspa językowa w języku $targetLanguage (poziom Master)",
                  "translation": "precyzyjne tłumaczenie zdania/wyspy na język polski"
                }
              ],
              "retrievedQuestions": [
                {
                  "question": "pytanie w języku obcym",
                  "answer": "krótka modelowa odpowiedź"
                }
              ],
              "transformations": [
                {
                  "type": "np. Uproszczenie poetyckie lub Zmiana osoby lub Zmiana czasu",
                  "transformedText": "przekształcony tekst",
                  "translation": "tłumaczenie",
                  "instruction": "instrukcja"
                }
              ],
              "communicationScenario": "polski opis wyzwania dyskusyjnego poświęconego znaczeniu utworu i emocjom",
              "poeticContext": "Wyjaśnienie slangu, idiomów i metafor (po polsku) - bardzo szczegółowe i edukacyjne."
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.6f,
                maxOutputTokens = 3000,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext ShadowingSegmentResponse(
                    foreignText = "Błąd: Brak klucza API Gemini",
                    nativeTranslation = "Skonfiguruj swój klucz API w ustawieniach aplikacji, aby korzystać z silnika Shadowing.",
                    keyPhrases = emptyList(),
                    retrievedQuestions = emptyList(),
                    transformations = emptyList(),
                    communicationScenario = "Brak połączenia z AI",
                    poeticContext = "Brak połączenia z AI"
                )
            }
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val cleaned = cleanJsonResponse(jsonText)

            val parsed = try {
                shadowingSegmentAdapter.fromJson(cleaned)
            } catch (e1: Exception) {
                Log.w("TutorAiService", "Standard song lyric JSON parse failed: ${e1.message}, trying repaired JSON...")
                try {
                    val repaired = repairAndCleanJson(jsonText)
                    shadowingSegmentAdapter.fromJson(repaired)
                } catch (e2: Exception) {
                    Log.w("TutorAiService", "Repaired song lyric JSON parse failed: ${e2.message}, using fallback regex extraction")
                    tryFallbackShadowingParse(jsonText)
                }
            }

            parsed ?: tryFallbackShadowingParse(jsonText) ?: ShadowingSegmentResponse(
                foreignText = "Błąd parsowania",
                nativeTranslation = "Nie udało się sparsować wygenerowanego segmentu piosenki.",
                keyPhrases = emptyList(),
                retrievedQuestions = emptyList(),
                transformations = emptyList(),
                communicationScenario = "Błąd",
                poeticContext = "Błąd"
            )
        } catch (e: Exception) {
            Log.e("TutorAiService", "processSongLyric failed", e)
            ShadowingSegmentResponse(
                foreignText = "Błąd generowania: ${e.localizedMessage}",
                nativeTranslation = "Wystąpił problem podczas łączenia z usługą AI.",
                keyPhrases = emptyList(),
                retrievedQuestions = emptyList(),
                transformations = emptyList(),
                communicationScenario = "Błąd połączenia z AI",
                poeticContext = "Błąd"
            )
        }
    }

    suspend fun extractPodcastHighlights(
        transcriptText: String,
        targetLanguage: String
    ): List<ImportedAudioSegment> = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś zaawansowanym analitykiem treści edukacyjnych i ekspertem od nauki języków obcych ($targetLanguage).
            Przeanalizuj poniższy długi tekst / transkrypcję podcastu lub prezentacji TED:
            "$transcriptText"

            Zadania:
            1. Wyodrębnij 3 do 5 kluczowych, najbardziej wartościowych edukacyjnie mikrosegmentów (o długości ok. 15-30 sekund każdy, ok. 20-50 słów). Skup się na strukturach gramatycznych, idiomatyce i słownictwie na poziomie B2-C1.
            2. Przypisz każdemu segmentowi przybliżone, narastające znaczniki czasu rozpoczęcia (`startTimeMs`) i zakończenia (`endTimeMs`) (np. segment 1: 0-15000, segment 2: 20000-45000 itp.), tak aby współgrały z odtwarzaniem audio.
            3. Dla każdego segmentu wygeneruj:
               - precyzyjne polskie tłumaczenie (`nativeTranslation`),
               - listę 3-4 PEŁNYCH zdań / wysp językowych poziomu Master (`keyPhrases`),
               - 2 pytania RETRIEVE (w $targetLanguage) wraz z modelową odpowiedzią,
               - 2 transformacje gramatyczne TRANSFORM,
               - wyzwanie sytuacyjne/scenariusz COMMUNICATE (po polsku).

            Zwróć odpowiedź WYŁĄCZNIE jako czysta tablica JSON o strukturze (bez bloku ```json, bez wstępów czy podsumowań):
            [
              {
                "foreignText": "tekst mikrosegmentu 1 w języku $targetLanguage",
                "nativeTranslation": "polskie tłumaczenie",
                "startTimeMs": 0,
                "endTimeMs": 15000,
                "keyPhrases": [
                  {"phrase": "zwrot", "translation": "tłumaczenie"}
                ],
                "retrievedQuestions": [
                  {"question": "pytanie", "answer": "odpowiedź"}
                ],
                "transformations": [
                  {"type": "Zmiana perspektywy", "transformedText": "tekst", "translation": "tłumaczenie", "instruction": "instrukcja"}
                ],
                "communicationScenario": "polski opis wyzwania dyskusyjnego"
              }
            ]
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.5f,
                maxOutputTokens = 2000,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext emptyList()
            }
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val cleaned = cleanJsonResponse(jsonText)
            importedSegmentsAdapter.fromJson(cleaned) ?: emptyList()
        } catch (e: Exception) {
            Log.e("TutorAiService", "extractPodcastHighlights failed", e)
            emptyList()
        }
    }

    /**
     * Generuje dynamiczne transformacje AI Accelerator (Krok 3) dopasowane do całego opowiadania.
     */
    suspend fun generateStoryAccelerations(
        storyContext: String,
        targetLanguage: String
    ): List<ShadowingTransformation> = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś zaawansowanym architektem systemów adaptacyjnych i ekspertem metody Shadowing & AI Accelerator.
            Przeanalizuj poniższe opowiadanie / segment tekstu w języku obcym ($targetLanguage):
            "$storyContext"

            Wygeneruj 5 DYNAMICZNYCH, ZRÓŻNICOWANYCH przekształceń składniowych (AI Accelerator), które pozwolą uczącemu się opanować ten wzorzec w pełnej komunikacji.
            
            Kroki ewolucji:
            1. Krok 1 (Baza): Kluczowe zdanie bazowe wynikające bezpośrednio z opowiadania.
            2. Krok 2 (Rozszerzenie): Rozbudowa o okoliczniki miejsca, czasu, intencji lub przyczyny z tej historii.
            3. Krok 3 (Zmiana Perspektywy/Czasu): Przełączenie perspektywy na inną postać lub inny czas gramatyczny.
            4. Krok 4 (Reakcja/Pytanie): Naturalne pytanie lub reakcja konwersacyjna zadana w kontekście tej historii.
            5. Krok 5 (Personalizacja): Przeniesienie motywu opowiadania do pytania lub stwierdzenia o życiu codziennym.

            KATEGORYCZNY NAKAZ ARCHITEKTONICZNY:
            - Każda transformacja MUSI mieć inny początek i inną strukturę zdania.
            - BEZWZGLĘDNIE ZAKAZANE JEST powtarzanie tego samego słowa/czasownika (np. 'Divido') na początku zdań!
            - Podaj czytelny typ ("Baza", "Rozszerzenie", "Zmiana Perspektywy", "Pytanie / Reakcja", "Personalizacja"), zwięzłą instrukcję po polsku, tekst w $targetLanguage oraz dokładne polskie tłumaczenie.

            Zwróć odpowiedź WYŁĄCZNIE jako czysta tablica JSON (bez ```json):
            [
              {
                "type": "Baza",
                "transformedText": "zdanie 1 w języku $targetLanguage",
                "translation": "tłumaczenie 1",
                "instruction": "opis 1"
              },
              {
                "type": "Rozszerzenie",
                "transformedText": "zdanie 2 w języku $targetLanguage",
                "translation": "tłumaczenie 2",
                "instruction": "opis 2"
              },
              {
                "type": "Zmiana Perspektywy",
                "transformedText": "zdanie 3 w języku $targetLanguage",
                "translation": "tłumaczenie 3",
                "instruction": "opis 3"
              },
              {
                "type": "Pytanie / Reakcja",
                "transformedText": "zdanie 4 w języku $targetLanguage",
                "translation": "tłumaczenie 4",
                "instruction": "opis 4"
              },
              {
                "type": "Personalizacja",
                "transformedText": "zdanie 5 w języku $targetLanguage",
                "translation": "tłumaczenie 5",
                "instruction": "opis 5"
              }
            ]
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.6f,
                maxOutputTokens = 1500,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext emptyList()
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val cleaned = cleanJsonResponse(jsonText)
            transformationsListAdapter.fromJson(cleaned) ?: emptyList()
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateStoryAccelerations failed", e)
            emptyList()
        }
    }

    /**
     * Generates a Master-Level StoryLearning Grammar & Natural Speech Explanation for any text.
     */
    suspend fun generateDeepGrammarExplanation(
        foreignText: String,
        targetLanguage: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś wybitnym profesorem lingwistyki stosowanej, fonetyki i ekspertem metodyki StoryLearning® Olly'ego Richardsa.
            Przeanalizuj poniższy tekst w języku ($targetLanguage) i przygotuj WYJĄTKOWO GŁĘBOKIE, PASJONUJĄCE I BOGATE objaśnienie gramatyczne po polsku, dopasowane do nauki mowy naturalnej na poziomie Masters:
            
            Tekst do analizy: "$foreignText"
            
            Przygotuj odpowiedź w czystym formacie Markdown (używaj nagłówków ### dla poszczególnych sekcji):
            
            ### 🎓 Czas i Konstrukcja: [Nazwa Czasu / Konstrukcji Gramatycznej]
            **Dlaczego Native Speaker używa tej formy (Mowa Naturalna vs. Podręcznik):** 
            Wyjaśnij intencję, emocje i pragmatykę mowy potocznej. Odpowiedz na pytanie: dlaczego native speaker wybrał właśnie to sformułowanie i co dokładnie odczuwa odbiorca? Czym różni się to od podręcznikowej reguły?
            
            ### 📐 Schemat i Anatomia Zdania
            **Wzorzec:** `[Formuła składniowa]`
            Rozłóż zdanie na części składowe (podmiot, operator, czasownik, dopełnienie, okoliczniki) i wyjaśnij szyk wyrazów.
            
            ### 🔍 Głęboka Analiza Słownictwa i Niuanse Leksykalne (Słówko po Słówku)
            Przeanalizuj po kolei KAŻDE kluczowe słowo i frazę ze zdania:
            - **[Słowo/Fraza w $targetLanguage]** -> [Polskie tłumaczenie]
              *Rola w zdaniu:* (np. operator modalny, phrasal verb, partykuła wzmacniająca)
              *Niuans semantyczny:* Dlaczego użyto tego konkretnego słowa, a nie słownikowego odpowiednika? Jakie ma zabarwienie emocjonalne/stylistyczne?
            
            ### 💡 Wskazówka Płynności Native Speakera (Mastery Tip)
            Praktyczna porada dotycząca połączonej mowy (connected speech, elizja, akcent zdaniowy) oraz jak unikać typowych błędów popełnianych przez Polaków przy tej konstrukcji.
            
            ### ⚡ Przykłady Porównawcze w Akcji
            Podaj 3-4 bogate, kontrastowe zdania przykładowe w języku $targetLanguage z dokładnym polskim tłumaczeniem pokazujące ten sam wzorzec w innych sytuacjach życiowych.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.6f,
                maxOutputTokens = 2500
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext "Skonfiguruj swój klucz API Gemini w ustawieniach aplikacji, aby generować analizy gramatyczne."
            }
            val response = callGeminiWithFallback(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Nie udało się wygenerować analizy gramatycznej."
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateDeepGrammarExplanation failed", e)
            "Błąd połączenia z AI podczas generowania analizy gramatycznej: ${e.localizedMessage}"
        }
    }

    suspend fun generateSemanticContrast(
        phrase: String,
        targetLanguage: String
    ): SemanticContrastResponse = withContext(Dispatchers.IO) {
        val prompt = """
            Jesteś ekspertem neurodydaktyki i lingwistyki kognitywnej.
            Dla podanego zwrotu lub słowa: "$phrase" w języku: $targetLanguage, zbuduj bogatą i gęstą sieć semantyczną w celu łatwiejszego przyswojenia i stworzenia mapy mentalnej w mózgu ucznia.
            
            Sieć musi składać się z trzech kategorii połączeń semantycznych (dla każdego połączenia podaj słowo/frazę w $targetLanguage oraz jego jasne polskie tłumaczenie):
            
            1. OPPOSITES & GRADATIONS (Przeciwieństwa i Stopniowanie):
               - Podaj słowa przeciwne lub tworzące naturalne stopniowanie (np. dla 'hot' podaj 'cold', 'warm', 'freezing', 'boiling').
               
            2. COLLOCATIONS & CAUSE-EFFECT (Relacje przyczynowo-skutkowe i kolokacje):
               - Podaj słowa, które naturalnie współwystępują lub wynikają z siebie (np. dla 'rain' podaj 'umbrella', 'soaked', 'forecast').
               
            3. HIERARCHIES & CATEGORIES (Hierarchie i kategorie):
               - Podaj pojęcia nadrzędne, podrzędne lub powiązane tematycznie kategorie (np. dla 'apple' podaj 'fruit', 'grocery', 'orchard').

            Zwróć odpowiedź WYŁĄCZNIE jako czysty obiekt JSON o następującej strukturze (żadnych innych tekstów poza JSON-em, bez bloku ```json):
            {
              "originalWord": "$phrase",
              "opposites": [
                { "word": "słowo/zwrot obcy", "translation": "polskie tłumaczenie", "relationType": "OPPOSITE" }
              ],
              "collocations": [
                { "word": "słowo/zwrot obcy", "translation": "polskie tłumaczenie", "relationType": "COLLOCATION" }
              ],
              "hierarchies": [
                { "word": "słowo/zwrot obcy", "translation": "polskie tłumaczenie", "relationType": "CATEGORY" }
              ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                maxOutputTokens = 1000,
                responseMimeType = "application/json"
            )
        )

        try {
            val apiKey = GeminiClient.getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext SemanticContrastResponse(
                    originalWord = phrase,
                    opposites = emptyList(),
                    collocations = emptyList(),
                    hierarchies = emptyList()
                )
            }
            val response = callGeminiWithFallback(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val cleaned = cleanJsonResponse(jsonText)
            semanticContrastAdapter.fromJson(cleaned) ?: SemanticContrastResponse(
                originalWord = phrase,
                opposites = emptyList(),
                collocations = emptyList(),
                hierarchies = emptyList()
            )
        } catch (e: Exception) {
            Log.e("TutorAiService", "generateSemanticContrast failed", e)
            SemanticContrastResponse(
                originalWord = phrase,
                opposites = emptyList(),
                collocations = emptyList(),
                hierarchies = emptyList()
            )
        }
    }
}

@Immutable
@JsonClass(generateAdapter = true)
data class SmartDictionaryResponse(
    val translated_text: String = "",
    val phonetic: String = "",
    val part_of_speech: String = "",
    val definition_pl: String = "",
    val example_target: String = "",
    val example_pl: String = ""
)

@Immutable
@JsonClass(generateAdapter = true)
data class StoryVocabularyItem(
    val word: String,
    val translation: String,
    val partOfSpeech: String,
    val contextSentence: String,
    val contextSentenceTranslation: String? = null,
    val mnemonic: String? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class StoryComprehensionQuestion(
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class StoryScenario(
    val title: String,
    val description: String,
    val initialPromptInstruction: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class StoryGrammarLesson(
    val lessonTitle: String = "",
    val coreInsight: String = "",
    val rules: List<StoryGrammarRule> = emptyList(),
    val lexicalNuances: List<LexicalNuance> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class StoryGrammarRule(
    val ruleName: String = "",
    val oneLiner: String = "",
    val mentalHook: String = "",
    val storyQuote: String = "",
    val storyQuoteTranslation: String = "",
    val contrasts: List<GrammarContrast> = emptyList(),
    val voicePracticeExamples: List<GrammarVoiceExample> = emptyList(),
    val polishTrap: String = "",
    val interactiveChallenge: StoryGrammarChallenge? = null,
    val deconstruction: SentenceDeconstruction? = null,
    val substitutionDrill: SubstitutionDrill? = null,
    val activeOutputChallenge: ActiveOutputChallenge? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class SentenceDeconstruction(
    val fullSentence: String = "",
    val translation: String = "",
    val tokens: List<DeconstructedToken> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class DeconstructedToken(
    val text: String = "",
    val role: String = "",
    val roleDescription: String = "",
    val isGrammarCore: Boolean = false
)

@Immutable
@JsonClass(generateAdapter = true)
data class SubstitutionDrill(
    val templatePattern: String = "",
    val templateTranslation: String = "",
    val fixedPart: String = "",
    val variableSlotName: String = "",
    val variations: List<SubstitutionVariation> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class SubstitutionVariation(
    val slotValue: String = "",
    val resultingSentence: String = "",
    val translation: String = "",
    val whyFormChanged: String = ""
)

@Immutable
@JsonClass(generateAdapter = true)
data class ActiveOutputChallenge(
    val polishPrompt: String = "",
    val targetSentence: String = "",
    val hintStructure: String = "",
    val scrambledTokens: List<String> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class GrammarContrast(
    val title: String = "",
    val correctForm: String = "",
    val correctTranslation: String = "",
    val commonMistakeOrLiteral: String = "",
    val explanation: String = "",
    val scenarioContext: String = ""
)

@Immutable
@JsonClass(generateAdapter = true)
data class GrammarVoiceExample(
    val sentence: String = "",
    val translation: String = "",
    val speechTip: String = "",
    val situationContext: String = ""
)

@Immutable
@JsonClass(generateAdapter = true)
data class StoryGrammarChallenge(
    val question: String = "",
    val options: List<String> = emptyList(),
    val answerIndex: Int = 0,
    val explanation: String = ""
)

@Immutable
@JsonClass(generateAdapter = true)
data class LexicalNuance(
    val phrase: String = "",
    val translation: String = "",
    val whyUsedInStory: String = "",
    val register: String = "",
    val alternativePhrases: List<String> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class GeneratedStoryResponse(
    val title: String,
    val content: String,
    val translation: String,
    val keyVocabularyList: List<StoryVocabularyItem>? = null,
    val grammarExplanation: String? = null,
    val comprehensionQuestionsList: List<StoryComprehensionQuestion>? = null,
    val scenariosList: List<StoryScenario>? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class WordTranslationResponse(
    val translation: String,
    val partOfSpeech: String,
    val polishExplanation: String,
    val alternativeMeanings: List<String>,
    val contextUsageExplanation: String? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class TutorTurnResponse(
    val correction: TutorCorrection?,
    val reply: String,
    val translation: String?,
    val vocabulary: List<TutorVocab>? = null,
    val suggestions: List<String>? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class TutorCorrection(
    val isCorrected: Boolean,
    val correctedText: String?,
    val explanation: String?,
    val mistakeType: String?,
    val pronunciationScore: Int? = null,
    val pronunciationFeedback: String? = null,
    val phoneticGuide: String? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class TutorVocab(
    val word: String,
    val translation: String,
    val sentenceContext: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class SessionEvaluationResponse(
    val fluencyScore: Int,
    val strengths: String,
    val improvements: String,
    val grammarTips: String,
    val studyPlan: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class RichMnemonicResponse(
    val word: String = "",
    val translation: String = "",
    val contextSentence: String = "",
    val contextTranslation: String = "",
    val situation: String = "",
    val association: String = "",
    val emojis: String = "🧠 💡 ✨ 🎯",
    val visualScene: String = ""
)

@Immutable
@JsonClass(generateAdapter = true)
data class MistakeTranslationResponse(
    val translatedSentence: String,
    val translatedExplanation: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class DynamicContextResponse(
    val newSentence: String,
    val translation: String,
    val interferenceTag: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class ShadowingKeyPhrase(
    val phrase: String,
    val translation: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class ShadowingRetrieveQuestion(
    val question: String,
    val answer: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class ShadowingTransformation(
    val type: String,
    val transformedText: String,
    val translation: String,
    val instruction: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class ShadowingSegmentResponse(
    val foreignText: String,
    val nativeTranslation: String,
    val keyPhrases: List<ShadowingKeyPhrase>,
    val retrievedQuestions: List<ShadowingRetrieveQuestion>? = null,
    val transformations: List<ShadowingTransformation>? = null,
    val communicationScenario: String? = null,
    val poeticContext: String? = null,
    val adaptiveAnchorInfo: String? = null,
    val grammarExplanation: String? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class CityVocabItem(
    val word: String = "",
    val translation: String = "",
    val context: String = ""
)

@Immutable
@JsonClass(generateAdapter = true)
data class CityScenarioItem(
    val title: String = "",
    val subtitle: String = "",
    val key: String = "",
    val difficulty: String = "A2–B1",
    val emoji: String = "🎭",
    val initialPrompt: String = ""
)

@Immutable
@JsonClass(generateAdapter = true)
data class CityHubData(
    val id: String = "",
    val name: String = "",
    val country: String = "",
    val icon: String = "🏙️",
    val tagline: String = "",
    val description: String = "",
    val keyVocab: List<CityVocabItem> = emptyList(),
    val scenarios: List<CityScenarioItem> = emptyList()
)
