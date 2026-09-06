package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.TutorApplication
import com.example.data.*
import com.example.data.repository.*
import com.example.data.api.SessionEvaluationResponse
import com.example.data.api.WordTranslationResponse
import com.example.data.api.TutorAiService
import com.example.data.api.TutorTurnResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import androidx.lifecycle.SavedStateHandle

class TutorViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    val liveChatRepository: ILiveChatRepository,
    val acceleratorRepository: IAcceleratorRepository,
    val dictionaryRepository: IDictionaryRepository,
    val contentRepository: IContentRepository,
    val vocabularyRepository: IVocabularyRepository,
    val mnemonicRepository: IMnemonicRepository,
    val shadowingRepository: IShadowingRepository
) : AndroidViewModel(application) {

    // --- State Observables ---
    val goal = liveChatRepository.goal.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val activeLanguage: StateFlow<String> = goal
        .map { it?.targetLanguage ?: "Spanish" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Spanish"
        )

    val allSessions = liveChatRepository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val languageIslands = activeLanguage
        .flatMapLatest { language ->
            vocabularyRepository.getLanguageIslands(language)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allVocabulary = activeLanguage
        .flatMapLatest { language ->
            vocabularyRepository.getAllVocabulary(language)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allMistakes = activeLanguage
        .flatMapLatest { language ->
            vocabularyRepository.getAllMistakes(language)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allMnemonics = activeLanguage
        .flatMapLatest { language ->
            mnemonicRepository.getMnemonicsForLanguage(language)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allStories = activeLanguage
        .flatMapLatest { language ->
            contentRepository.getStoriesForLanguage(language)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isGeneratingStory = MutableStateFlow(false)
    val isGeneratingStory: StateFlow<Boolean> = _isGeneratingStory.asStateFlow()

    private val _isExtractingUrl = MutableStateFlow(false)
    val isExtractingUrl: StateFlow<Boolean> = _isExtractingUrl.asStateFlow()

    private val _urlExtractionError = MutableStateFlow<String?>(null)
    val urlExtractionError: StateFlow<String?> = _urlExtractionError.asStateFlow()

    fun clearUrlExtractionError() {
        _urlExtractionError.value = null
    }

    private val _isGeneratingGrammar = MutableStateFlow(false)
    val isGeneratingGrammar: StateFlow<Boolean> = _isGeneratingGrammar.asStateFlow()

    private val _activeStory = MutableStateFlow<Story?>(null)
    val activeStory: StateFlow<Story?> = _activeStory.asStateFlow()

    private val _wordTranslation = MutableStateFlow<WordTranslationResponse?>(null)
    val wordTranslation: StateFlow<WordTranslationResponse?> = _wordTranslation.asStateFlow()

    private val _isTranslatingWord = MutableStateFlow(false)
    val isTranslatingWord: StateFlow<Boolean> = _isTranslatingWord.asStateFlow()

    private val _isGeneratingMnemonic = MutableStateFlow(false)
    val isGeneratingMnemonic: StateFlow<Boolean> = _isGeneratingMnemonic.asStateFlow()

    private val _generatedMnemonic = MutableStateFlow<String?>(null)
    val generatedMnemonic: StateFlow<String?> = _generatedMnemonic.asStateFlow()

    // --- Active Session States ---
    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession: StateFlow<Session?> = _activeSession.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _isEndingSession = MutableStateFlow(false)
    val isEndingSession: StateFlow<Boolean> = _isEndingSession.asStateFlow()

    private val _currentEvaluation = MutableStateFlow<SessionEvaluationResponse?>(null)
    val currentEvaluation: StateFlow<SessionEvaluationResponse?> = _currentEvaluation.asStateFlow()

    private val _textToSpeak = MutableStateFlow<String?>(null)
    val textToSpeak: StateFlow<String?> = _textToSpeak.asStateFlow()

    data class SpeechChunkEvent(
        val sentence: String,
        val isFirst: Boolean,
        val isFinal: Boolean,
        val id: Long = System.currentTimeMillis()
    )
    private val _streamingSpeechChunk = MutableStateFlow<SpeechChunkEvent?>(null)
    val streamingSpeechChunk: StateFlow<SpeechChunkEvent?> = _streamingSpeechChunk.asStateFlow()

    fun clearStreamingSpeechChunk() {
        _streamingSpeechChunk.value = null
    }

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    private val _activeSessionVibe = MutableStateFlow("normal")
    val activeSessionVibe: StateFlow<String> = _activeSessionVibe.asStateFlow()

    private val _turnLengthMode = MutableStateFlow("fast") // "fast", "balanced", "detailed"
    val turnLengthMode: StateFlow<String> = _turnLengthMode.asStateFlow()

    fun changeTurnLengthMode(mode: String) {
        val validModes = setOf("fast", "balanced", "detailed", "micro", "long")
        if (validModes.contains(mode.lowercase().trim())) {
            _turnLengthMode.value = mode.lowercase().trim()
        }
    }

    private val _latestSuggestions = MutableStateFlow<List<String>>(emptyList())
    val latestSuggestions: StateFlow<List<String>> = _latestSuggestions.asStateFlow()

    // --- City Exploration Module States ---
    private val _isGeneratingCityModule = MutableStateFlow(false)
    val isGeneratingCityModule: StateFlow<Boolean> = _isGeneratingCityModule.asStateFlow()

    private val _generatedCityModule = MutableStateFlow<String?>(null)
    val generatedCityModule: StateFlow<String?> = _generatedCityModule.asStateFlow()

    private val _generatedCityHub = MutableStateFlow<com.example.data.api.CityHubData?>(null)
    val generatedCityHub: StateFlow<com.example.data.api.CityHubData?> = _generatedCityHub.asStateFlow()

    fun changeSessionVibe(vibe: String) {
        _activeSessionVibe.value = vibe
    }

    // --- Key Connection Test States ---
    private val _testConnectionResult = MutableStateFlow<String?>(null)
    val testConnectionResult: StateFlow<String?> = _testConnectionResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _selectedVoice = MutableStateFlow("Sulafat")
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    val connectionStatus: StateFlow<com.example.data.api.ConnectionStatus> = com.example.data.api.NetworkMonitor.status

    val recentSearches = dictionaryRepository.recentSearches.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _dictionaryResult = MutableStateFlow<DictionaryCacheEntity?>(null)
    val dictionaryResult: StateFlow<DictionaryCacheEntity?> = _dictionaryResult.asStateFlow()

    private val _isSearchingDictionary = MutableStateFlow(false)
    val isSearchingDictionary: StateFlow<Boolean> = _isSearchingDictionary.asStateFlow()

    private val _acceleratorElements = MutableStateFlow<List<com.example.data.api.TutorAiService.BuzanNode>>(emptyList())
    val acceleratorElements: StateFlow<List<com.example.data.api.TutorAiService.BuzanNode>> = _acceleratorElements.asStateFlow()

    private val _isGeneratingElements = MutableStateFlow(false)
    val isGeneratingElements: StateFlow<Boolean> = _isGeneratingElements.asStateFlow()

    fun generateCommunicationElements(intentionPL: String, languageCode: String, cefrLevel: String) {
        if (intentionPL.isBlank()) return
        acceleratorJob?.cancel()
        acceleratorJob = viewModelScope.launch {
            _isGeneratingElements.value = true
            _acceleratorElements.value = emptyList()
            try {
                val response = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    TutorAiService.generateCommunicationElements(intentionPL, languageCode, cefrLevel)
                }
                if (response != null && response.elements.isNotEmpty()) {
                    _acceleratorElements.value = response.elements
                } else if (response != null) {
                    _acceleratorElements.value = emptyList()
                } else {
                    _acceleratorElements.value = listOf(
                        com.example.data.api.TutorAiService.BuzanNode(
                            core_pattern = "Błąd",
                            core_translationPL = "Przekroczono limit czasu lub brak sieci",
                            radial_branches = emptyList(),
                            buzan_anchor = "error",
                            explanation = "Spróbuj ponownie"
                        )
                    )
                }
                
            } catch (e: Exception) {
                Log.e("TutorViewModel", "Failed to generate accelerator elements", e)
            } finally {
                _isGeneratingElements.value = false
            }
        }
    }

    private var acceleratorJob: kotlinx.coroutines.Job? = null

    fun clearAcceleratorElements() {
        acceleratorJob?.cancel()
        _isGeneratingElements.value = false
        _acceleratorElements.value = emptyList()
    }

    fun searchDictionary(query: String, languageCode: String, contextText: String? = null) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearchingDictionary.value = true
            val normalized = query.trim().lowercase()
            try {
                val cached = dictionaryRepository.getDictionaryEntry(languageCode, normalized)
                if (cached != null) {
                    val updated = cached.copy(timestamp = System.currentTimeMillis())
                    dictionaryRepository.insertDictionaryEntry(updated)
                    _dictionaryResult.value = updated
                } else {
                    val response = TutorAiService.fetchSmartDictionaryEntry(query, languageCode, contextText)
                    val entry = DictionaryCacheEntity(
                        language_code = languageCode,
                        normalized_query = response.translated_text.trim().lowercase(),
                        translated_text = response.definition_pl.trim(),
                        phonetic = response.phonetic,
                        part_of_speech = response.part_of_speech,
                        definition_pl = response.definition_pl,
                        example_target = response.example_target,
                        example_pl = response.example_pl,
                        timestamp = System.currentTimeMillis()
                    )
                    dictionaryRepository.insertDictionaryEntry(entry)
                    _dictionaryResult.value = entry
                }
            } catch (e: Exception) {
                Log.e("TutorViewModel", "searchDictionary failed", e)
                _dictionaryResult.value = DictionaryCacheEntity(
                    language_code = languageCode,
                    normalized_query = normalized,
                    definition_pl = "Błąd połączenia",
                    part_of_speech = "Nie udało się uzyskać tłumaczenia: ${e.localizedMessage}",
                    timestamp = System.currentTimeMillis()
                )
            } finally {
                _isSearchingDictionary.value = false
            }
        }
    }

    fun clearDictionaryResult() {
        _dictionaryResult.value = null
    }

    fun deleteRecentSearch(id: Long) {
        viewModelScope.launch {
            dictionaryRepository.deleteDictionaryEntryById(id)
        }
    }

    fun testConnection(tempKey: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testConnectionResult.value = "Testowanie połączenia..."
            val trimmedKey = tempKey.trim()
            val keyToUse = if (trimmedKey.isNotEmpty()) trimmedKey else getSavedApiKey()
            
            // Auto-save key to make it instantly active when testing
            if (trimmedKey.isNotEmpty()) {
                saveApiKey(trimmedKey)
            }
            
            val result = TutorAiService.testApiConnection(keyToUse)
            _isTestingConnection.value = false
            if (result.isSuccess) {
                _testConnectionResult.value = "SUCCESS:Połączenie udane! Twój klucz API został zapisany i jest aktywny."
            } else {
                _testConnectionResult.value = "ERROR:${result.exceptionOrNull()?.message ?: "Nieznany błąd."}"
            }
        }
    }

    fun clearTestConnectionResult() {
        _testConnectionResult.value = null
    }

    // Observe active session chat messages reactively
    val activeSessionMessages: StateFlow<List<ChatMessage>> = _activeSession
        .flatMapLatest { session ->
            if (session != null) {
                liveChatRepository.getMessagesForSession(session.id, session.language)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        ensureGoalInitialized()
        val savedKey = getSavedApiKey()
        _apiKey.value = savedKey
        com.example.data.api.GeminiClient.dynamicApiKey = savedKey
        _selectedModel.value = getSavedModel()
        _selectedVoice.value = getSavedVoice()
        
        // Restore from SavedStateHandle
        viewModelScope.launch {
            val restoredSessionId = savedStateHandle.get<Long>("active_session_id")
            if (restoredSessionId != null) {
                _activeSession.value = liveChatRepository.getSessionById(restoredSessionId)
            }
            
            val restoredStoryId = savedStateHandle.get<Long>("active_story_id")
            if (restoredStoryId != null) {
                _activeStory.value = contentRepository.getStoryById(restoredStoryId)
            }
        }
    }

    fun getSavedApiKey(): String {
        val sharedPref = getApplication<Application>().getSharedPreferences("lingo_prefs", android.content.Context.MODE_PRIVATE)
        return sharedPref.getString("gemini_api_key", "") ?: ""
    }

    fun saveApiKey(key: String) {
        val sharedPref = getApplication<Application>().getSharedPreferences("lingo_prefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().putString("gemini_api_key", key.trim()).apply()
        _apiKey.value = key.trim()
        com.example.data.api.GeminiClient.dynamicApiKey = key.trim()
    }

    fun clearApiKey() {
        val sharedPref = getApplication<Application>().getSharedPreferences("lingo_prefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().remove("gemini_api_key").apply()
        _apiKey.value = ""
        com.example.data.api.GeminiClient.dynamicApiKey = ""
        _testConnectionResult.value = null
    }

    fun getSavedModel(): String {
        val sharedPref = getApplication<Application>().getSharedPreferences("lingo_prefs", android.content.Context.MODE_PRIVATE)
        return sharedPref.getString("gemini_model", "gemini-3.5-flash") ?: "gemini-3.5-flash"
    }

    fun saveModel(model: String) {
        val sharedPref = getApplication<Application>().getSharedPreferences("lingo_prefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().putString("gemini_model", model.trim()).apply()
        _selectedModel.value = model.trim()
    }

    fun getSavedVoice(): String {
        val sharedPref = getApplication<Application>().getSharedPreferences("lingo_prefs", android.content.Context.MODE_PRIVATE)
        return sharedPref.getString("selected_voice", "Sulafat") ?: "Sulafat"
    }

    fun saveVoice(voice: String) {
        val sharedPref = getApplication<Application>().getSharedPreferences("lingo_prefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().putString("selected_voice", voice.trim()).apply()
        _selectedVoice.value = voice.trim()
    }

    private fun ensureGoalInitialized() {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = liveChatRepository.getGoalSync()
            if (existing == null) {
                liveChatRepository.insertGoal(UserGoal())
            }
        }
    }

    fun updateGoal(
        targetLanguage: String,
        cefrLevel: String,
        focusArea: String,
        dailyMinutes: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = liveChatRepository.getGoalSync() ?: UserGoal()
            val newPlan = when (cefrLevel) {
                "A1", "A2" -> "Focus on primary expressions, travel vocabulary, and build simple active dialogues. Practice 10-15 mins daily."
                "B1", "B2" -> "Transition into conversational structures, focus on compound tenses and storytelling flow. Practice 15 mins daily."
                "C1", "C2" -> "Engage in open discussions, expand idioms, professional context vocabulary, and syntax logic. Practice 20 mins daily."
                else -> current.studyPlan
            }
            liveChatRepository.insertGoal(
                current.copy(
                    targetLanguage = targetLanguage,
                    cefrLevel = cefrLevel,
                    focusArea = focusArea,
                    dailyGoalMinutes = dailyMinutes,
                    studyPlan = newPlan
                )
            )
            // Instantly cut off previous language chat history in the view
            _activeSession.value = null
            savedStateHandle.set("active_session_id", null)
        }
    }

    // --- Session Operations ---

    private suspend fun buildStudentLongTermProfile(targetLanguage: String): String {
        return withContext(Dispatchers.IO) {
            val sb = java.lang.StringBuilder()
            
            // 1. Fetch recent completed sessions (for context)
            val recentSessions = liveChatRepository.getRecentSessionsSync().filter { it.language.equals(targetLanguage, ignoreCase = true) }
            if (recentSessions.isNotEmpty()) {
                sb.append("- Topics previously practiced in $targetLanguage:\n")
                recentSessions.take(5).forEach { session ->
                    sb.append("  * '${session.title}' (Fluency Score: ${session.fluencyScore}%)\n")
                }
            } else {
                sb.append("- No previous sessions recorded yet. This is your first interaction on this topic.\n")
            }
            
            // 2. Fetch unresolved mistakes / recent mistakes (for vigilance and personalized review)
            val recentMistakes = vocabularyRepository.getRecentMistakesSync(targetLanguage)
            if (recentMistakes.isNotEmpty()) {
                sb.append("- Common mistakes previously made by the user in $targetLanguage (Crucial to reference or subtly test again to see if they resolved them!):\n")
                recentMistakes.take(8).forEach { mistake ->
                    sb.append("  * Mistake Type: ${mistake.type}\n")
                    sb.append("    Original wrong sentence: \"${mistake.originalSentence}\"\n")
                    sb.append("    Corrected sentence: \"${mistake.correctedSentence}\"\n")
                    sb.append("    Explanation: ${mistake.explanation}\n")
                }
            }
            
            // 3. Fetch active vocabulary (to check what words they know or are learning)
            val recentVocab = vocabularyRepository.getRecentVocabularySync(targetLanguage).filter { it.language.equals(targetLanguage, ignoreCase = true) }
            if (recentVocab.isNotEmpty()) {
                sb.append("- Vocabulary the student is learning or has recently saved (Use these words or praise their usage!):\n")
                recentVocab.take(10).forEach { vocab ->
                    val status = when (vocab.masteryLevel) {
                        0 -> "New"
                        1 -> "Learning"
                        else -> "Mastered"
                    }
                    sb.append("  * \"${vocab.word}\" (Translation: \"${vocab.translation}\", Status: $status)\n")
                }
            }
            
            sb.toString()
        }
    }

    fun startNewSession(scenarioKey: String, scenarioTitle: String, stressLevel: Int = 0, initialVibe: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiLoading.value = true
            _currentEvaluation.value = null
            val validVibes = setOf("normal", "gossip", "deeptalk", "humor", "survival", "standard")
            val currentVibe = if (initialVibe != null && validVibes.contains(initialVibe.lowercase().trim())) {
                initialVibe.lowercase().trim()
            } else {
                if (validVibes.contains(_activeSessionVibe.value.lowercase().trim())) _activeSessionVibe.value else "normal"
            }
            _activeSessionVibe.value = currentVibe
            _latestSuggestions.value = emptyList()
            
            val currentGoal = liveChatRepository.getGoalSync() ?: UserGoal()
            val newSession = Session(
                title = scenarioTitle,
                scenarioKey = scenarioKey,
                language = currentGoal.targetLanguage,
                cefrLevel = currentGoal.cefrLevel,
                stressLevel = stressLevel
            )
            
            val sessionId = liveChatRepository.insertSession(newSession)
            _activeSession.value = newSession.copy(id = sessionId)
            savedStateHandle.set("active_session_id", sessionId)

            var profile = buildStudentLongTermProfile(currentGoal.targetLanguage)
            val activeStoryVal = _activeStory.value
            if (scenarioKey.startsWith("story_") && activeStoryVal != null) {
                profile = "$profile\n\nACTIVE STORY INFORMATION:\n" +
                        "Story Title: ${activeStoryVal.title}\n" +
                        "Story Content:\n${activeStoryVal.content}\n" +
                        "Story Key Vocabulary JSON:\n${activeStoryVal.keyVocabulary}\n" +
                        "Story Translation:\n${activeStoryVal.translation}\n"
            }

            // Prompt Gemini to generate a realistic greeting / context setting dialogue turn
            val initialPrompt = if (!initialVibe.isNullOrBlank()) {
                if (scenarioKey.startsWith("shadowing")) {
                    initialVibe
                } else if (scenarioKey.startsWith("custom_city") || scenarioKey.startsWith("city_")) {
                    "Greet me as a native language tutor or roleplay partner for the scenario: '$scenarioTitle'. " +
                    "Context and instructions: $initialVibe. " +
                    "Welcome me warmly, adopt your roleplay persona in ${currentGoal.targetLanguage} (strictly tailored for CEFR level ${currentGoal.cefrLevel}), and ask the first opening question to begin our dialogue."
                } else {
                    initialVibe
                }
            } else if (scenarioKey.startsWith("story_") && activeStoryVal != null) {
                var customInstruction = ""
                if (scenarioKey.contains("_sc_")) {
                    try {
                        val parts = scenarioKey.split("_sc_")
                        if (parts.size > 1) {
                            val scIdx = parts[1].toIntOrNull()
                            if (scIdx != null) {
                                val moshi = com.squareup.moshi.Moshi.Builder()
                                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                                    .build()
                                val scenariosAdapter = moshi.adapter<List<com.example.data.api.StoryScenario>>(
                                    com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryScenario::class.java)
                                )
                                val scenarios = scenariosAdapter.fromJson(activeStoryVal.scenariosJson)
                                if (scenarios != null && scIdx in scenarios.indices) {
                                    customInstruction = scenarios[scIdx].initialPromptInstruction
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TutorViewModel", "Error parsing custom story scenario", e)
                    }
                }
                
                if (customInstruction.isNotBlank()) {
                    "Greet me as a native language tutor starting a specialized discussion about the short story: '${activeStoryVal.title}'. " +
                    "Welcome me warmly and start the conversation based on this scenario instruction: '$customInstruction'. " +
                    "The conversation must flow naturally in ${currentGoal.targetLanguage} specifically tailored for my level (${currentGoal.cefrLevel}). Keep your greeting strictly at the CEFR level ${currentGoal.cefrLevel}."
                } else {
                    "Greet me as a native language tutor starting to discuss the short story: '$scenarioTitle'. " +
                    "Welcome me warmly and ask an open-ended question in ${currentGoal.targetLanguage} specifically about the beginning of the story " +
                    "to get me talking. Keep your greeting and conversation strictly at the CEFR level ${currentGoal.cefrLevel}. Also, mention that we will focus on using the story's vocabulary, and invite me to share my first impression of the characters or plot."
                }
            } else {
                "Greet me as a native language tutor starting the scenario: '$scenarioTitle'. " +
                "Welcome me warmly, adopt your roleplay character if applicable, and ask an open-ended question " +
                "to get me talking in ${currentGoal.targetLanguage}. Keep your greeting strictly at the CEFR level ${currentGoal.cefrLevel}. " +
                "IMPORTANT: Review my profile, and if I have previous sessions or mistakes, welcome me back and refer to my previous topic or previous mistake in a friendly, natural tutor voice!"
            }

            try {
                val turnResponse = TutorAiService.getTutorResponse(
                    language = currentGoal.targetLanguage,
                    cefrLevel = currentGoal.cefrLevel,
                    scenarioKey = scenarioKey,
                    scenarioTitle = scenarioTitle,
                    history = emptyList(),
                    newUserMessage = initialPrompt,
                    studentProfile = profile,
                    stressLevel = stressLevel,
                    vibe = currentVibe,
                    turnLengthMode = _turnLengthMode.value,
                    focusArea = currentGoal.focusArea
                )

                val greetingMessage = ChatMessage(
                    sessionId = sessionId,
                    sender = "tutor",
                    originalText = turnResponse.reply,
                    translatedText = turnResponse.translation,
                    language_code = currentGoal.targetLanguage
                )
                liveChatRepository.insertMessage(greetingMessage)
                
                // Save suggestions
                _latestSuggestions.value = turnResponse.suggestions ?: emptyList()
                
                // Save vocabulary recommendations
                val addedInBatch = mutableSetOf<String>()
                turnResponse.vocabulary?.distinctBy { it.word.trim().lowercase() }?.forEach { item ->
                    val cleanWord = item.word.trim().lowercase()
                    if (!isVocabularyDuplicate(item.word, currentGoal.targetLanguage) && !addedInBatch.contains(cleanWord)) {
                        addedInBatch.add(cleanWord)
                        vocabularyRepository.insertVocabulary(
                            Vocabulary(
                                word = item.word,
                                translation = item.translation,
                                language = currentGoal.targetLanguage,
                                sentenceContext = item.sentenceContext,
                                masteryLevel = 0,
                                language_code = currentGoal.targetLanguage
                            )
                        )
                    }
                }

                // Trigger complete Text-to-Speech playback of the greeting
                if (!turnResponse.reply.isNullOrBlank()) {
                    _textToSpeak.value = turnResponse.reply
                }

            } catch (e: Exception) {
                val greetingMessage = ChatMessage(
                    sessionId = sessionId,
                    sender = "tutor",
                    originalText = "Hello! Let's start practicing speaking ${currentGoal.targetLanguage}! How are you feeling today?",
                    language_code = currentGoal.targetLanguage
                )
                liveChatRepository.insertMessage(greetingMessage)
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun submitUserMessage(userText: String) {
        val session = _activeSession.value ?: return
        if (userText.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isAiLoading.value = true

            // 1. Log the user's message locally
            val userMsg = ChatMessage(
                sessionId = session.id,
                sender = "user",
                originalText = userText,
                language_code = session.language
            )
            val insertedId = liveChatRepository.insertMessage(userMsg)

            // 2. Fetch history for context
            val currentHistory = liveChatRepository.getMessagesForSessionSync(session.id, session.language)

            // 3. Call Tutor AI service
            try {
                val currentGoal = liveChatRepository.getGoalSync() ?: UserGoal()
                var profile = buildStudentLongTermProfile(session.language)
                val activeStoryVal = _activeStory.value
                if (session.scenarioKey.startsWith("story_") && activeStoryVal != null) {
                    profile = "$profile\n\nACTIVE STORY INFORMATION:\n" +
                            "Story Title: ${activeStoryVal.title}\n" +
                            "Story Content:\n${activeStoryVal.content}\n" +
                            "Story Key Vocabulary JSON:\n${activeStoryVal.keyVocabulary}\n" +
                            "Story Translation:\n${activeStoryVal.translation}\n"
                }
                val turnResponse = withTimeoutOrNull(20000L) {
                    TutorAiService.getTutorResponse(
                        language = session.language,
                        cefrLevel = session.cefrLevel,
                        scenarioKey = session.scenarioKey,
                        scenarioTitle = session.title,
                        history = currentHistory.dropLast(1), // Exclude the message we just added since it's sent separately
                        newUserMessage = userText,
                        studentProfile = profile,
                        stressLevel = session.stressLevel,
                        vibe = _activeSessionVibe.value,
                        turnLengthMode = _turnLengthMode.value,
                        focusArea = currentGoal.focusArea
                    )
                } ?: TutorTurnResponse(
                    reply = "Przepraszam, połączenie sieciowe jest chwilowo obciążone. Powtórz proszę zdanie.",
                    translation = "Przepraszam, połączenie sieciowe jest chwilowo obciążone. Powtórz proszę zdanie.",
                    correction = null,
                    suggestions = listOf("Mogę powtórzyć", "Spróbujmy jeszcze raz")
                )

                // 4. Update user's message locally with grammar corrections and pronunciation feedback
                val correction = turnResponse.correction
                if (correction != null) {
                    val isRealCorrection = correction.isCorrected && 
                        !correction.correctedText.isNullOrBlank() && 
                        !isOnlyPunctuationOrQuestionMarkDifference(userText, correction.correctedText)

                    val updatedUserMsg = userMsg.copy(
                        id = insertedId,
                        isCorrected = isRealCorrection,
                        correctedText = if (isRealCorrection) correction.correctedText else null,
                        correctionExplanation = if (isRealCorrection) correction.explanation else null,
                        pronunciationScore = correction.pronunciationScore ?: 100,
                        pronunciationFeedback = correction.pronunciationFeedback,
                        phoneticGuide = correction.phoneticGuide
                    )
                    liveChatRepository.insertMessage(updatedUserMsg)

                    if (isRealCorrection) {
                        val correctedText = correction.correctedText!!
                        if (!isMistakeDuplicate(userText, correctedText)) {
                            // Store historical mistake for statistical tracking
                            vocabularyRepository.insertMistake(
                                Mistake(
                                    sessionId = session.id,
                                    originalSentence = userText,
                                    correctedSentence = correctedText,
                                    explanation = correction.explanation ?: "Grammatical adjustment.",
                                    type = correction.mistakeType ?: "Grammar",
                                    language_code = session.language
                                )
                            )
                        }
                    }
                }

                // Update latest suggestions
                _latestSuggestions.value = turnResponse.suggestions ?: emptyList()

                // 5. Store tutor's response
                val tutorMsg = ChatMessage(
                    sessionId = session.id,
                    sender = "tutor",
                    originalText = turnResponse.reply,
                    translatedText = turnResponse.translation,
                    language_code = session.language
                )
                liveChatRepository.insertMessage(tutorMsg)

                // 6. Save recommended vocabulary items dynamically
                val addedInBatchDyn = mutableSetOf<String>()
                turnResponse.vocabulary?.distinctBy { item -> item.word.trim().lowercase() }?.forEach { item ->
                    val cleanWord = item.word.trim().lowercase()
                    if (!isVocabularyDuplicate(item.word, session.language) && !addedInBatchDyn.contains(cleanWord)) {
                        addedInBatchDyn.add(cleanWord)
                        vocabularyRepository.insertVocabulary(
                            Vocabulary(
                                word = item.word,
                                translation = item.translation,
                                language = session.language,
                                sentenceContext = item.sentenceContext,
                                masteryLevel = 0,
                                language_code = session.language
                            )
                        )
                    }
                }

                // 7. Request complete speech synthesis in UI layer
                if (!turnResponse.reply.isNullOrBlank()) {
                    _textToSpeak.value = turnResponse.reply
                }

            } catch (e: Exception) {
                Log.e("TutorViewModel", "Error in submitUserMessage after retries", e)
                val isNetworkError = e is java.io.IOException || e.message?.contains("połączenia") == true || e.message?.contains("network") == true || e.message?.contains("failed to connect") == true
                val errorMessage = if (isNetworkError) {
                    "⚠️ [Błąd połączenia] Straciłem połączenie z internetem. Twoja ostatnia wiadomość została bezpiecznie zapisana. Połączenie zostanie wznowione automatycznie."
                } else {
                    "Ups! Napotkałem problem z połączeniem: ${e.localizedMessage}. Twoja wiadomość została zachowana, spróbuj wysłać ponownie."
                }
                val tutorMsg = ChatMessage(
                    sessionId = session.id,
                    sender = "tutor",
                    originalText = errorMessage,
                    language_code = session.language
                )
                liveChatRepository.insertMessage(tutorMsg)
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun clearSpeechText() {
        _textToSpeak.value = null
    }

    fun endActiveSession() {
        val session = _activeSession.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isEndingSession.value = true
            _isAiLoading.value = true
            try {
                val messages = liveChatRepository.getMessagesForSessionSync(session.id, session.language)
                val evaluation = TutorAiService.evaluateSession(session, messages)

                val elapsed = ((System.currentTimeMillis() - session.timestamp) / 1000).toInt()
                val userMsgCount = messages.count { it.sender == "user" }
                val estimatedDuration = if (elapsed < 60 && userMsgCount > 0) {
                    userMsgCount * 30 // 30 seconds per message if elapsed is extremely small/unreliable
                } else {
                    elapsed
                }
                val finalDuration = maxOf(30, estimatedDuration)

                // Save feedback details in the DB Session record
                val updatedSession = session.copy(
                    isCompleted = true,
                    durationSeconds = finalDuration,
                    fluencyScore = evaluation.fluencyScore,
                    feedbackStrength = evaluation.strengths,
                    feedbackImprovement = evaluation.improvements,
                    feedbackGrammar = evaluation.grammarTips,
                    feedbackStudyPlan = evaluation.studyPlan
                )
                liveChatRepository.updateSession(updatedSession)

                // Update user streak and last active timestamp
                val goalProfile = liveChatRepository.getGoalSync() ?: UserGoal()
                val now = System.currentTimeMillis()
                val dayMillis = 24 * 60 * 60 * 1000L
                val isNextDay = now - goalProfile.lastActiveTimestamp in dayMillis..(dayMillis * 2)
                val isSameDay = now - goalProfile.lastActiveTimestamp < dayMillis
                val newStreak = when {
                    goalProfile.lastActiveTimestamp == 0L -> 1
                    isNextDay -> goalProfile.streak + 1
                    isSameDay -> goalProfile.streak
                    else -> 1 // Broken streak reset
                }

                liveChatRepository.insertGoal(
                    goalProfile.copy(
                        streak = newStreak,
                        lastActiveTimestamp = now,
                        studyPlan = evaluation.studyPlan
                    )
                )

                // Transition states to display feedback
                _currentEvaluation.value = evaluation
                _activeSession.value = null
            savedStateHandle.set("active_session_id", null)
            } catch (e: Exception) {
                Log.e("TutorViewModel", "Error ending session and evaluating: ${e.message}", e)
                // Even on error, we should exit the active session state so the user isn't stuck
                _activeSession.value = null
            savedStateHandle.set("active_session_id", null)
            } finally {
                _isAiLoading.value = false
                _isEndingSession.value = false
            }
        }
    }

    fun dismissFeedback() {
        _currentEvaluation.value = null
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            liveChatRepository.deleteSessionById(sessionId)
        }
    }

    // --- Helper for Duplicates and Punctuation filtering ---

    private suspend fun isVocabularyDuplicate(word: String, language: String): Boolean {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty()) return true
        
        // 1. Direct database check (rigorous case-insensitive & trimmed)
        val dbMatch = vocabularyRepository.getVocabularyByWordAndLanguage(cleanWord, language)
        if (dbMatch != null) return true
        
        // 2. In-memory backup check
        val cleanWordLower = cleanWord.lowercase()
        return allVocabulary.value.any {
            it.word.trim().lowercase() == cleanWordLower &&
            it.language.equals(language, ignoreCase = true)
        }
    }

    private fun isMistakeDuplicate(original: String, corrected: String): Boolean {
        val cleanOrig = original.trim().lowercase()
        val cleanCorr = corrected.trim().lowercase()
        return allMistakes.value.any {
            it.originalSentence.trim().lowercase() == cleanOrig &&
            it.correctedSentence.trim().lowercase() == cleanCorr
        }
    }

    private fun isOnlyPunctuationOrQuestionMarkDifference(original: String, corrected: String): Boolean {
        val cleanOriginal = original.replace(Regex("[?.,!'\"\\s¡¿]"), "").lowercase()
        val cleanCorrected = corrected.replace(Regex("[?.,!'\"\\s¡¿]"), "").lowercase()
        return cleanOriginal == cleanCorrected
    }

    // --- Vocabulary Management ---

    fun toggleVocabMastery(vocab: Vocabulary) {
        viewModelScope.launch(Dispatchers.IO) {
            val newLevel = if (vocab.masteryLevel >= 2) 0 else vocab.masteryLevel + 1
            vocabularyRepository.updateVocabulary(vocab.copy(masteryLevel = newLevel))
        }
    }

    fun processVocabSrsReview(vocab: Vocabulary, rating: Int, latencyMs: Long = 0) { // 1 = Again, 4 = Good, 5 = Easy
        viewModelScope.launch(Dispatchers.IO) {
            val wasCorrect = rating >= 4
            
            val newRepetitions: Int
            val newIntervalDays: Int
            val newEaseFactor: Double
            
            if (wasCorrect) {
                newRepetitions = vocab.repetitions + if (rating == 5) 2 else 1
                newIntervalDays = when (newRepetitions) {
                    1 -> 1
                    2 -> 3
                    3 -> 7
                    else -> {
                        val multiplier = if (rating == 5) 1.5 else 1.0
                        Math.max(1, Math.round(vocab.intervalDays * vocab.easeFactor * multiplier).toInt())
                    }
                }
                val efChange = if (rating == 5) 0.15 else 0.0
                newEaseFactor = Math.max(1.3, vocab.easeFactor + efChange)
            } else {
                newRepetitions = 0
                newIntervalDays = 1
                newEaseFactor = Math.max(1.3, vocab.easeFactor - 0.2)
            }
            
            val latencyMultiplier = if (wasCorrect && latencyMs > 0) {
                if (latencyMs > 5000) {
                    0.75
                } else if (latencyMs < 2000) {
                    1.25
                } else {
                    1.0
                }
            } else {
                1.0
            }
            
            val finalIntervalDays = if (wasCorrect && latencyMs > 0) {
                Math.max(1, Math.round(newIntervalDays * latencyMultiplier).toInt())
            } else {
                newIntervalDays
            }
            
            val updatedNextReview = System.currentTimeMillis() + (finalIntervalDays * 24 * 60 * 60 * 1000L)
            
            val updatedVocab = vocab.copy(
                repetitions = newRepetitions,
                intervalDays = finalIntervalDays,
                easeFactor = newEaseFactor,
                nextReviewTimestamp = updatedNextReview,
                masteryLevel = if (wasCorrect) {
                    if (rating == 5) 2 else Math.min(2, vocab.masteryLevel + 1)
                } else {
                    0
                },
                last_response_latency_ms = latencyMs
            )
            vocabularyRepository.updateVocabulary(updatedVocab)
        }
    }

    fun deleteVocabulary(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            vocabularyRepository.deleteVocabularyById(id)
        }
    }

    fun updateVocabulary(vocab: Vocabulary) {
        viewModelScope.launch(Dispatchers.IO) {
            vocabularyRepository.updateVocabulary(vocab)
        }
    }

    fun addCustomVocabulary(word: String, translation: String, context: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentGoal = liveChatRepository.getGoalSync() ?: UserGoal()
            if (isVocabularyDuplicate(word, currentGoal.targetLanguage)) {
                return@launch
            }
            
            // Asynchronously pre-fetch Polish translations to avoid waiting later
            var polishWord = ""
            var polishCtx = ""
            try {
                polishWord = TutorAiService.translateSentenceToPolish(word, currentGoal.targetLanguage)
                if (context.isNotBlank()) {
                    polishCtx = TutorAiService.translateSentenceToPolish(context, currentGoal.targetLanguage)
                }
            } catch (e: Exception) {
                Log.e("TutorViewModel", "addCustomVocabulary background translation failed", e)
            }

            vocabularyRepository.insertVocabulary(
                Vocabulary(
                    word = word,
                    translation = translation,
                    language = currentGoal.targetLanguage,
                    sentenceContext = context,
                    polishWordTranslation = polishWord,
                    polishContextTranslation = polishCtx,
                    language_code = currentGoal.targetLanguage
                )
            )
        }
    }

    fun addDictionaryEntryToVocabulary(entry: DictionaryCacheEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val languageCode = entry.language_code
            if (isVocabularyDuplicate(entry.normalized_query, languageCode)) {
                return@launch
            }
            try {
                vocabularyRepository.insertVocabulary(
                    Vocabulary(
                        word = entry.normalized_query,
                        translation = entry.definition_pl,
                        language = languageCode,
                        sentenceContext = entry.example_target,
                        polishWordTranslation = entry.translated_text,
                        polishContextTranslation = entry.part_of_speech,
                        language_code = languageCode,
                        interference_tag = "none"
                    )
                )
                withContext(Dispatchers.Main) {
                    Log.d("TutorViewModel", "Successfully saved Dictionary Entry to room")
                }
            } catch (e: Exception) {
                Log.e("TutorViewModel", "Error saving Dictionary Entry to room on 64-bit device", e)
            }
        }
    }

    fun addLanguageIslandToVocabulary(element: com.example.data.api.TutorAiService.BuzanNode, languageCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isVocabularyDuplicate(element.core_pattern, languageCode)) {
                return@launch
            }
            
            // Build context from radial branches and buzan anchor
            val branchesContext = element.radial_branches.joinToString("\n") { "• ${it.branch_pattern} (${it.translationPL}) - ${it.context_tag}" }
            val ctx = "Hak Buzana: ${element.buzan_anchor}\n\nGałęzie:\n$branchesContext"
            
            try {
                vocabularyRepository.insertVocabulary(
                    Vocabulary(
                        word = element.core_pattern,
                        translation = element.core_translationPL,
                        language = languageCode,
                        sentenceContext = ctx,
                        polishWordTranslation = element.core_translationPL,
                        polishContextTranslation = element.explanation,
                        language_code = languageCode,
                        interference_tag = "LANGUAGE_ISLAND"
                    )
                )
                withContext(Dispatchers.Main) {
                    Log.d("TutorViewModel", "Successfully saved Language Island to room")
                }
            } catch (e: Exception) {
                Log.e("TutorViewModel", "Error saving Language Island to room on 64-bit device", e)
            }
        }
    }

    fun autoTranslateVocabulary(vocab: Vocabulary) {
        viewModelScope.launch(Dispatchers.IO) {
            if (vocab.polishWordTranslation.isBlank() || (vocab.sentenceContext.isNotBlank() && vocab.polishContextTranslation.isBlank())) {
                try {
                    val translatedWord = if (vocab.polishWordTranslation.isBlank()) {
                        TutorAiService.translateSentenceToPolish(vocab.word, vocab.language)
                    } else {
                        vocab.polishWordTranslation
                    }
                    val translatedCtx = if (vocab.sentenceContext.isNotBlank() && vocab.polishContextTranslation.isBlank()) {
                        TutorAiService.translateSentenceToPolish(vocab.sentenceContext, vocab.language)
                    } else {
                        vocab.polishContextTranslation
                    }
                    
                    val updatedVocab = vocab.copy(
                        polishWordTranslation = translatedWord,
                        polishContextTranslation = translatedCtx
                    )
                    vocabularyRepository.updateVocabulary(updatedVocab)
                } catch (e: Exception) {
                    Log.e("TutorViewModel", "autoTranslateVocabulary failed for word: ${vocab.word}", e)
                }
            }
        }
    }

    // --- Mistakes Resolution ---

    fun resolveMistake(mistake: Mistake) {
        viewModelScope.launch(Dispatchers.IO) {
            vocabularyRepository.updateMistake(mistake.copy(isResolved = !mistake.isResolved))
        }
    }

    fun deleteMistake(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            vocabularyRepository.deleteMistakeById(id)
        }
    }

    fun processMistakeSrsReview(mistake: Mistake, rating: Int, latencyMs: Long = 0) { // 1 = Again, 4 = Good, 5 = Easy
        viewModelScope.launch(Dispatchers.IO) {
            val wasCorrect = rating >= 4
            
            val newRepetitions: Int
            val newIntervalDays: Int
            val newEaseFactor: Double
            
            if (wasCorrect) {
                newRepetitions = mistake.repetitions + if (rating == 5) 2 else 1
                newIntervalDays = when (newRepetitions) {
                    1 -> 1
                    2 -> 3
                    3 -> 7
                    else -> {
                        val multiplier = if (rating == 5) 1.5 else 1.0
                        Math.max(1, Math.round(mistake.intervalDays * mistake.easeFactor * multiplier).toInt())
                    }
                }
                val efChange = if (rating == 5) 0.15 else 0.0
                newEaseFactor = Math.max(1.3, mistake.easeFactor + efChange)
            } else {
                newRepetitions = 0
                newIntervalDays = 1
                newEaseFactor = Math.max(1.3, mistake.easeFactor - 0.2)
            }
            
            val latencyMultiplier = if (wasCorrect && latencyMs > 0) {
                if (latencyMs > 5000) {
                    0.75
                } else if (latencyMs < 2000) {
                    1.25
                } else {
                    1.0
                }
            } else {
                1.0
            }
            
            val finalIntervalDays = if (wasCorrect && latencyMs > 0) {
                Math.max(1, Math.round(newIntervalDays * latencyMultiplier).toInt())
            } else {
                newIntervalDays
            }
            
            val updatedNextReview = System.currentTimeMillis() + (finalIntervalDays * 24 * 60 * 60 * 1000L)
            
            val updatedMistake = mistake.copy(
                repetitions = newRepetitions,
                intervalDays = finalIntervalDays,
                easeFactor = newEaseFactor,
                nextReviewTimestamp = updatedNextReview,
                isResolved = wasCorrect,
                last_response_latency_ms = latencyMs
            )
            vocabularyRepository.updateMistake(updatedMistake)
        }
    }

    fun generateAndSaveMnemonic(wordOrPhrase: String, language: String, contextSentence: String? = null) {
        generateAndSaveRichMnemonic(wordOrPhrase, language, contextSentence)
    }

    fun deleteMnemonic(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            mnemonicRepository.deleteMnemonicById(id)
        }
    }

    fun clearGeneratedMnemonic() {
        _generatedMnemonic.value = null
    }

    fun findExistingMnemonic(query: String, language: String? = null): Mnemonic? {
        val q = query.trim().lowercase()
        if (q.isBlank()) return null
        return allMnemonics.value.firstOrNull { mnem ->
            val langMatch = language.isNullOrBlank() || mnem.language.equals(language, ignoreCase = true)
            if (!langMatch) return@firstOrNull false

            if (mnem.wordOrPhrase.trim().lowercase() == q) return@firstOrNull true
            
            val assoc = mnem.association.trim()
            if (assoc.startsWith("{")) {
                try {
                    val moshi = com.squareup.moshi.Moshi.Builder()
                        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                        .build()
                    val adapter = moshi.adapter(com.example.data.api.RichMnemonicResponse::class.java)
                    val rich = adapter.fromJson(assoc)
                    if (rich != null) {
                        if (rich.word.trim().lowercase() == q) return@firstOrNull true
                        if (rich.translation.trim().lowercase() == q) return@firstOrNull true
                        if (rich.translation.lowercase().contains(q) || q.contains(rich.translation.lowercase())) return@firstOrNull true
                    }
                } catch (e: Exception) { }
            } else if (assoc.contains(q, ignoreCase = true)) {
                return@firstOrNull true
            }
            false
        }
    }

    fun generateAndSaveRichMnemonic(
        wordOrPhrase: String,
        language: String,
        contextSentence: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (wordOrPhrase.isBlank()) {
                onComplete(false)
                return@launch
            }
            _isGeneratingMnemonic.value = true
            _generatedMnemonic.value = "Generowanie mnemotechniki przez AI..."
            val richMnemonic = com.example.data.api.TutorAiService.generateRichMnemonic(wordOrPhrase, language, contextSentence)
            _isGeneratingMnemonic.value = false
            if (richMnemonic != null) {
                val moshi = com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                val adapter = moshi.adapter(com.example.data.api.RichMnemonicResponse::class.java)
                val jsonStr = adapter.toJson(richMnemonic)
                
                _generatedMnemonic.value = jsonStr
                
                val primaryWord = richMnemonic.word.trim().ifBlank { wordOrPhrase.trim() }
                
                mnemonicRepository.insertMnemonic(
                    Mnemonic(
                        wordOrPhrase = primaryWord,
                        association = jsonStr,
                        language = language
                    )
                )
                onComplete(true)
            } else {
                _generatedMnemonic.value = "Nie udało się wygenerować mnemotechniki. Sprawdź połączenie lub klucz API."
                onComplete(false)
            }
        }
    }

    // --- AI Story Reader and Translation Methods ---

    fun generateReadingStory(topic: String, language: String, difficulty: String) {
        viewModelScope.launch {
            if (topic.isBlank()) return@launch
            _isGeneratingStory.value = true
            val storyResp = TutorAiService.generateReadingStory(topic, language, difficulty)
            _isGeneratingStory.value = false
            
            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            
            val vocabAdapter = moshi.adapter<List<com.example.data.api.StoryVocabularyItem>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryVocabularyItem::class.java)
            )
            val questionsAdapter = moshi.adapter<List<com.example.data.api.StoryComprehensionQuestion>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryComprehensionQuestion::class.java)
            )
            val scenariosAdapter = moshi.adapter<List<com.example.data.api.StoryScenario>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryScenario::class.java)
            )
            
            val vocabJson = storyResp.keyVocabularyList?.let { vocabAdapter.toJson(it) } ?: "[]"
            val questionsJson = storyResp.comprehensionQuestionsList?.let { questionsAdapter.toJson(it) } ?: "[]"
            val scenariosJson = storyResp.scenariosList?.let { scenariosAdapter.toJson(it) } ?: "[]"

            val newStory = Story(
                title = storyResp.title,
                content = storyResp.content,
                translation = storyResp.translation,
                language = language,
                difficulty = difficulty,
                keyVocabulary = vocabJson,
                grammarExplanation = storyResp.grammarExplanation ?: "Gramatyka w kontekście uczy się bezpośrednio z tekstu.",
                comprehensionQuestions = questionsJson,
                scenariosJson = scenariosJson,
                language_code = language
            )
            val id = contentRepository.insertStory(newStory)
            _activeStory.value = newStory.copy(id = id)
            savedStateHandle.set("active_story_id", id)
        }
    }

    fun generateStoryFromExternalContext(
        rawText: String,
        imageBase64: String?,
        imageMimeType: String?,
        language: String,
        difficulty: String
    ) {
        viewModelScope.launch {
            _isGeneratingStory.value = true
            val storyResp = TutorAiService.generateStoryFromExternalContext(
                rawText = rawText,
                imageBase64 = imageBase64,
                imageMimeType = imageMimeType,
                language = language,
                difficulty = difficulty
            )
            _isGeneratingStory.value = false

            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()

            val vocabAdapter = moshi.adapter<List<com.example.data.api.StoryVocabularyItem>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryVocabularyItem::class.java)
            )
            val questionsAdapter = moshi.adapter<List<com.example.data.api.StoryComprehensionQuestion>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryComprehensionQuestion::class.java)
            )
            val scenariosAdapter = moshi.adapter<List<com.example.data.api.StoryScenario>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryScenario::class.java)
            )

            val vocabJson = storyResp.keyVocabularyList?.let { vocabAdapter.toJson(it) } ?: "[]"
            val questionsJson = storyResp.comprehensionQuestionsList?.let { questionsAdapter.toJson(it) } ?: "[]"
            val scenariosJson = storyResp.scenariosList?.let { scenariosAdapter.toJson(it) } ?: "[]"

            val newStory = Story(
                title = storyResp.title,
                content = storyResp.content,
                translation = storyResp.translation,
                language = language,
                difficulty = difficulty,
                keyVocabulary = vocabJson,
                grammarExplanation = storyResp.grammarExplanation ?: "Gramatyka w kontekście uczy się bezpośrednio z tekstu.",
                comprehensionQuestions = questionsJson,
                scenariosJson = scenariosJson,
                language_code = language
            )
            val id = contentRepository.insertStory(newStory)
            _activeStory.value = newStory.copy(id = id)
            savedStateHandle.set("active_story_id", id)
        }
    }

    fun generateStoryFromUrl(
        url: String,
        language: String,
        difficulty: String,
        onExtractionFailure: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _urlExtractionError.value = null
            _isExtractingUrl.value = true
            _isGeneratingStory.value = true

            val extractionResult = com.example.data.util.WebArticleExtractor.extractFromUrl(url)
            _isExtractingUrl.value = false

            extractionResult.onSuccess { article ->
                val enrichedContext = buildString {
                    appendLine("ŹRÓDŁO ARTYKUŁU: ${article.title} (${article.domain})")
                    appendLine("URL: ${article.url}")
                    appendLine("TREŚĆ ARTYKUŁU:")
                    append(article.cleanContent)
                }

                val storyResp = TutorAiService.generateArticleSummaryFromUrl(
                    rawText = enrichedContext,
                    language = language,
                    difficulty = difficulty
                )
                _isGeneratingStory.value = false

                val moshi = com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()

                val vocabAdapter = moshi.adapter<List<com.example.data.api.StoryVocabularyItem>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryVocabularyItem::class.java)
                )
                val questionsAdapter = moshi.adapter<List<com.example.data.api.StoryComprehensionQuestion>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryComprehensionQuestion::class.java)
                )
                val scenariosAdapter = moshi.adapter<List<com.example.data.api.StoryScenario>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryScenario::class.java)
                )

                val vocabJson = storyResp.keyVocabularyList?.let { vocabAdapter.toJson(it) } ?: "[]"
                val questionsJson = storyResp.comprehensionQuestionsList?.let { questionsAdapter.toJson(it) } ?: "[]"
                val scenariosJson = storyResp.scenariosList?.let { scenariosAdapter.toJson(it) } ?: "[]"

                val displayTitle = if (!storyResp.title.contains("•") && article.domain.isNotBlank()) {
                    "${storyResp.title} • ${article.domain}"
                } else {
                    storyResp.title
                }

                val newStory = Story(
                    title = displayTitle,
                    content = storyResp.content,
                    translation = storyResp.translation,
                    language = language,
                    difficulty = difficulty,
                    keyVocabulary = vocabJson,
                    grammarExplanation = storyResp.grammarExplanation ?: "Gramatyka w kontekście uczy się bezpośrednio z tekstu artykułu.",
                    comprehensionQuestions = questionsJson,
                    scenariosJson = scenariosJson,
                    language_code = language
                )
                val id = contentRepository.insertStory(newStory)
                _activeStory.value = newStory.copy(id = id)
                savedStateHandle.set("active_story_id", id)
            }.onFailure { ex ->
                _isGeneratingStory.value = false
                val errorMsg = ex.message ?: "Nie udało się pobrać artykułu z podanego adresu URL."
                _urlExtractionError.value = errorMsg
                onExtractionFailure?.invoke(errorMsg)
            }
        }
    }

    fun upgradeStoryToStoryLearning(story: Story) {
        viewModelScope.launch {
            _isGeneratingStory.value = true
            try {
                val storyResp = TutorAiService.analyzeStoryForStoryLearning(story.title, story.content, story.language)
                
                val moshi = com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                
                val vocabAdapter = moshi.adapter<List<com.example.data.api.StoryVocabularyItem>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryVocabularyItem::class.java)
                )
                val questionsAdapter = moshi.adapter<List<com.example.data.api.StoryComprehensionQuestion>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryComprehensionQuestion::class.java)
                )
                val scenariosAdapter = moshi.adapter<List<com.example.data.api.StoryScenario>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryScenario::class.java)
                )
                
                val vocabJson = storyResp.keyVocabularyList?.let { vocabAdapter.toJson(it) } ?: "[]"
                val questionsJson = storyResp.comprehensionQuestionsList?.let { questionsAdapter.toJson(it) } ?: "[]"
                val scenariosJson = storyResp.scenariosList?.let { scenariosAdapter.toJson(it) } ?: "[]"

                val updatedStory = story.copy(
                    keyVocabulary = vocabJson,
                    grammarExplanation = storyResp.grammarExplanation ?: "Gramatyka w kontekście uczy się bezpośrednio z tekstu.",
                    comprehensionQuestions = questionsJson,
                    scenariosJson = scenariosJson
                )
                contentRepository.insertStory(updatedStory)
                _activeStory.value = updatedStory
                savedStateHandle.set("active_story_id", updatedStory.id)
            } catch (e: Exception) {
                Log.e("TutorViewModel", "Error upgrading story", e)
            } finally {
                _isGeneratingStory.value = false
            }
        }
    }

    fun upgradeStoryGrammarToPro(story: Story) {
        viewModelScope.launch {
            _isGeneratingGrammar.value = true
            try {
                val grammarLesson = TutorAiService.generateRichGrammarLesson(
                    title = story.title,
                    content = story.content,
                    language = story.language,
                    difficulty = story.difficulty
                )
                val grammarJson = TutorAiService.storyGrammarLessonAdapter.toJson(grammarLesson)
                val updatedStory = story.copy(grammarExplanation = grammarJson)
                contentRepository.insertStory(updatedStory)
                _activeStory.value = updatedStory
                savedStateHandle.set("active_story_id", updatedStory.id)
            } catch (e: Exception) {
                Log.e("TutorViewModel", "Error upgrading grammar lesson to Pro", e)
            } finally {
                _isGeneratingGrammar.value = false
            }
        }
    }

    fun generateDynamicScenariosAndQuestions(story: Story) {
        viewModelScope.launch {
            _isGeneratingStory.value = true
            try {
                val storyResp = TutorAiService.generateMoreQuestionsAndScenarios(
                    title = story.title,
                    content = story.content,
                    grammarExplanation = story.grammarExplanation,
                    language = story.language,
                    difficulty = story.difficulty
                )
                
                val moshi = com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                
                val questionsAdapter = moshi.adapter<List<com.example.data.api.StoryComprehensionQuestion>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryComprehensionQuestion::class.java)
                )
                val scenariosAdapter = moshi.adapter<List<com.example.data.api.StoryScenario>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.StoryScenario::class.java)
                )
                
                val existingQuestions = try {
                    questionsAdapter.fromJson(story.comprehensionQuestions) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                val existingScenarios = try {
                    scenariosAdapter.fromJson(story.scenariosJson) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                val newQuestions = storyResp.comprehensionQuestionsList ?: emptyList()
                val newScenarios = storyResp.scenariosList ?: emptyList()

                val mergedQuestions = (existingQuestions + newQuestions).distinctBy { it.question }
                val mergedScenarios = (existingScenarios + newScenarios).distinctBy { it.title }

                val questionsJson = questionsAdapter.toJson(mergedQuestions)
                val scenariosJson = scenariosAdapter.toJson(mergedScenarios)

                val updatedStory = story.copy(
                    comprehensionQuestions = questionsJson,
                    scenariosJson = scenariosJson
                )
                contentRepository.insertStory(updatedStory)
                _activeStory.value = updatedStory
                savedStateHandle.set("active_story_id", updatedStory.id)
            } catch (e: Exception) {
                Log.e("TutorViewModel", "Error generating dynamic scenarios/questions", e)
            } finally {
                _isGeneratingStory.value = false
            }
        }
    }

    // --- City Exploration Module Methods ---

    fun generateCityModule(city: String, country: String, cefrLevel: String) {
        viewModelScope.launch {
            if (city.isBlank() || country.isBlank()) return@launch
            _isGeneratingCityModule.value = true
            _generatedCityModule.value = "Generowanie Twojego immersyjnego modułu kulturowo-językowego dla miasta $city ($country)... Może to zająć chwilę."
            val targetLang = goal.value?.targetLanguage ?: "Spanish"
            try {
                val structuredHub = TutorAiService.generateStructuredCityHub(city, country, targetLang, cefrLevel)
                _generatedCityHub.value = structuredHub
                _generatedCityModule.value = structuredHub.description
            } catch (e: Exception) {
                Log.e("TutorViewModel", "Error generating city module", e)
                _generatedCityModule.value = "Błąd podczas generowania: ${e.localizedMessage}"
            } finally {
                _isGeneratingCityModule.value = false
            }
        }
    }

    fun clearCityModule() {
        _generatedCityModule.value = null
        _generatedCityHub.value = null
    }

    suspend fun getPronunciationTip(sentence: String, language: String): String {
        return try {
            TutorAiService.getPronunciationTip(sentence, language)
        } catch (e: Exception) {
            Log.e("TutorViewModel", "Error getting pronunciation tip", e)
            "Błąd połączenia z serwerem AI."
        }
    }

    fun selectStory(story: Story?) {
        _activeStory.value = story
        savedStateHandle.set("active_story_id", story?.id)
        _wordTranslation.value = null
    }

    fun deleteStory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            contentRepository.deleteStoryById(id)
            if (_activeStory.value?.id == id) {
                _activeStory.value = null
                savedStateHandle.set("active_story_id", null)
            }
        }
    }

    private val _translationCache = mutableMapOf<String, WordTranslationResponse>()

    fun translateWordFromStory(word: String, sentenceContext: String, language: String) {
        val existingMnem = findExistingMnemonic(word, language)
        _generatedMnemonic.value = existingMnem?.association

        val cacheKey = "${language.lowercase()}_${word.trim().lowercase()}"
        val cached = _translationCache[cacheKey]
        if (cached != null) {
            _wordTranslation.value = cached
            if (_generatedMnemonic.value == null) {
                val mnemByTrans = findExistingMnemonic(cached.translation, language)
                if (mnemByTrans != null) {
                    _generatedMnemonic.value = mnemByTrans.association
                }
            }
            return
        }
        viewModelScope.launch {
            if (word.isBlank()) return@launch
            _isTranslatingWord.value = true
            try {
                val normalized = word.trim().lowercase()
                val cachedDb = dictionaryRepository.getDictionaryEntry(language, normalized)
                val entry = if (cachedDb != null) {
                    val updated = cachedDb.copy(timestamp = System.currentTimeMillis())
                    dictionaryRepository.insertDictionaryEntry(updated)
                    updated
                } else {
                    val response = TutorAiService.fetchSmartDictionaryEntry(word, language, sentenceContext)
                    val newEntry = DictionaryCacheEntity(
                        language_code = language,
                        normalized_query = response.translated_text.trim().lowercase(),
                        translated_text = response.definition_pl.trim(),
                        phonetic = response.phonetic,
                        part_of_speech = response.part_of_speech,
                        definition_pl = response.definition_pl,
                        example_target = response.example_target,
                        example_pl = response.example_pl,
                        timestamp = System.currentTimeMillis()
                    )
                    dictionaryRepository.insertDictionaryEntry(newEntry)
                    newEntry
                }

                val mappedResponse = WordTranslationResponse(
                    translation = entry.definition_pl,
                    partOfSpeech = if (entry.phonetic.isNotEmpty()) "Wymowa: ${entry.phonetic} | ${entry.part_of_speech}" else entry.part_of_speech,
                    polishExplanation = entry.example_pl,
                    alternativeMeanings = if (entry.example_target.isNotEmpty()) listOf(entry.example_target) else emptyList(),
                    contextUsageExplanation = "Tłumaczenie docelowe: ${entry.translated_text}"
                )

                _translationCache[cacheKey] = mappedResponse
                _wordTranslation.value = mappedResponse

                if (_generatedMnemonic.value == null) {
                    val mnemByTrans = findExistingMnemonic(entry.definition_pl, language) ?: findExistingMnemonic(entry.translated_text, language)
                    if (mnemByTrans != null) {
                        _generatedMnemonic.value = mnemByTrans.association
                    }
                }
            } catch (e: Exception) {
                Log.e("TutorViewModel", "translateWordFromStory smart fallback failed", e)
                val fallback = TutorAiService.translateSingleWord(word, sentenceContext, language)
                _wordTranslation.value = fallback
            } finally {
                _isTranslatingWord.value = false
            }
        }
    }

    fun clearWordTranslation() {
        _wordTranslation.value = null
        _generatedMnemonic.value = null
    }

    fun saveStoryWordToVocabulary(word: String, translation: String, language: String, sentenceContext: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isVocabularyDuplicate(word, language)) {
                return@launch
            }
            var polishWord = ""
            var polishCtx = ""
            try {
                polishWord = TutorAiService.translateSentenceToPolish(word.trim(), language)
                if (sentenceContext.isNotBlank()) {
                    polishCtx = TutorAiService.translateSentenceToPolish(sentenceContext.trim(), language)
                }
            } catch (e: Exception) {
                Log.e("TutorViewModel", "saveStoryWordToVocabulary background translation failed", e)
            }

            val vocab = Vocabulary(
                word = word.trim(),
                translation = translation.trim(),
                language = language,
                sentenceContext = sentenceContext.trim(),
                polishWordTranslation = polishWord,
                polishContextTranslation = polishCtx,
                language_code = language
            )
            try {
                vocabularyRepository.insertVocabulary(vocab)
                withContext(Dispatchers.Main) {
                    Log.d("TutorViewModel", "Successfully saved Story Word to room")
                }
            } catch (e: Exception) {
                Log.e("TutorViewModel", "Error saving Story Word to room on 64-bit device", e)
            }
        }
    }

    fun populateLegendarySamples() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentGoal = liveChatRepository.getGoalSync() ?: UserGoal(targetLanguage = "English")
            val targetLang = if (currentGoal.targetLanguage.isNotBlank()) currentGoal.targetLanguage else "English"

            // 1. awkward
            val awkJson = """{"word":"awkward","translation":"niezręczny","contextSentence":"He gave an awkward smile during the photo shoot.","contextTranslation":"Posłał niezręczny uśmiech podczas sesji zdjęciowej.","situation":"Używamy tego słowa, gdy sytuacja jest krępująca, społecznie niewygodna lub gdy czujemy się zakłopotani.","association":"Awkward brzmi trochę jak 'A, Kocur!'. Wyobraź sobie, że na eleganckim weselu nagle na stół wskakuje wielki, gruby kocur i zrzuca tort. Nastaje niesamowicie 'awkward' (niezręczna) cisza, a goście posyłają sobie zakłopotane spojrzenia!","emojis":"🐈🎂😬🤵","visualScene":"Wielki, puszysty kocur siedzący na środku rozbitego tortu weselnego, podczas gdy panna młoda patrzy na niego z otwartą buzią."}"""

            // 2. throw under the bus
            val busJson = """{"word":"throw under the bus","translation":"poświęcić kogoś dla własnej korzyści","contextSentence":"My colleague threw me under the bus in front of the boss.","contextTranslation":"Mój kolega z pracy poświęcił mnie (rzucił pod autobus) przed szefem.","situation":"Używane, gdy ktoś zrzuca winę na inną osobę lub zdradza jej zaufanie, aby samemu uniknąć kłopotów lub zyskać aprobatę.","association":"Dosłownie 'rzucić kogoś pod autobus'. Wyobraź sobie, jak w biurze szef pyta kto zepsuł ekspres do kawy, a Twój kolega nagle otwiera okno i dosłownie rzuca Cię pod pędzący żółty autobus miejski, żeby samemu wyjść na niewiniątko!","emojis":"🚌🏢☕🤫","visualScene":"Żółty autobus szkolny zatrzymujący się gwałtownie przed biurowcem, z którego wylatuje zszokowany pracownik z kubkiem kawy."}"""

            // 3. make out
            val makeJson = """{"word":"make out","translation":"dostrzec / usłyszeć coś z trudem","contextSentence":"I could barely make out the shapes in the heavy fog.","contextTranslation":"Ledwo mogłem dostrzec kształty w gęstej mgle.","situation":"Gdy próbujemy coś zobaczyć lub usłyszeć w trudnych warunkach (np. we mgle, w hałasie, z daleka).","association":"Make out kojarzy się z 'Makijaż out' (makijaż spłynął). Wyobraź sobie, że dziewczyna stoi w gęstej mgle, a deszcz zmył jej cały makijaż. Jej chłopak mruży oczy i z wielkim trudem próbuje 'make out' (dostrzec) jej prawdziwą twarz bez makijażu!","emojis":"🌫️🌧️💄👁️","visualScene":"Postać z rozmazanym czarnym tuszem pod oczami, wyłaniająca się z gęstej, szarej mgły pełnej kropli deszczu."}"""

            // 4. hang out
            val hangJson = """{"word":"hang out","translation":"spędzać wspólnie czas / relaksować się","contextSentence":"We should hang out sometime after work.","contextTranslation":"Powinniśmy kiedyś spędzić razem czas po pracy.","situation":"Używane w nieformalnych sytuacjach, gdy chcemy spędzić czas ze znajomymi bez konkretnego, poważnego celu - po prostu odpocząć razem.","association":"Hang out dosłownie oznacza 'wisieć na zewnątrz'. Wyobraź sobie, że Ty i Twoi przyjaciele po ciężkim dniu w pracy nie idziecie do pubu, tylko wieszacie się na wieszakach na pranie na tarasie i rozmawiacie, pijąc kawę na świeżym powietrzu!","emojis":"🧺👕🛋️🍻","visualScene":"Grupa roześmianych przyjaciół w garniturach, wiszących beztrosko na wielkim sznurze do prania z kubkami piwa w dłoniach."}"""

            // 5. espeto
            val espJson = """{"word":"espeto","translation":"grillowana sardynka na kiju plażowym","contextSentence":"We ate a delicious traditional espeto at the beach chiringuito.","contextTranslation":"Zjedliśmy pyszną tradycyjną sardynkę z grilla w plażowej knajpce.","situation":"Tradycyjne danie z Malagi (Andaluzja), gdzie świeże ryby nadziewa się na trzcinowe kije i grilluje na piasku nad żarem z drewna oliwnego.","association":"Espeto kojarzy się ze 'szpadą' lub 'szpitem'. Wyobraź sobie, że kelner w Maladze zamiast talerza przynosi Ci wielką piracką szpadę (espeto), na którą nadziane są srebrzyste ryby, i musisz je zjeść prosto z tego ostrza!","emojis":"🍢🐟⚔️🔥🏖️","visualScene":"Płonący grill w starej drewnianej łodzi rybackiej na plaży, a nad nim wbite w piasek kije z pieczącymi się rybami."}"""

            val samples = listOf(
                Pair(Vocabulary(word = "awkward", translation = "niezręczny", language = targetLang, sentenceContext = "He gave an awkward smile during the photo shoot.", language_code = targetLang), awkJson),
                Pair(Vocabulary(word = "throw under the bus", translation = "poświęcić kogoś dla własnej korzyści", language = targetLang, sentenceContext = "My colleague threw me under the bus in front of the boss.", language_code = targetLang), busJson),
                Pair(Vocabulary(word = "make out", translation = "dostrzec / usłyszeć coś z trudem", language = targetLang, sentenceContext = "I could barely make out the shapes in the heavy fog.", language_code = targetLang), makeJson),
                Pair(Vocabulary(word = "hang out", translation = "spędzać wspólnie czas / relaksować się", language = targetLang, sentenceContext = "We should hang out sometime after work.", language_code = targetLang), hangJson),
                Pair(Vocabulary(word = "espeto", translation = "grillowana sardynka na kiju plażowym", language = "Spanish", sentenceContext = "We ate a delicious traditional espeto at the beach chiringuito.", language_code = "Spanish"), espJson)
            )

            samples.forEach { (vocab, json) ->
                val vocabExists = vocabularyRepository.getRecentVocabularySync(vocab.language).any { it.word.equals(vocab.word, ignoreCase = true) }
                if (!vocabExists) {
                    vocabularyRepository.insertVocabulary(vocab)
                }
                val mnemExists = allMnemonics.value.any { it.wordOrPhrase.equals(vocab.word, ignoreCase = true) }
                if (!mnemExists) {
                    mnemonicRepository.insertMnemonic(
                        Mnemonic(
                            wordOrPhrase = vocab.word,
                            association = json,
                            language = vocab.language
                        )
                    )
                }
            }
        }
    }

    suspend fun getLanguageResourceStatsSync(langName: String): Triple<Int, Int, Int> {
        return withContext(Dispatchers.IO) {
            val sessionsCount = liveChatRepository.getRecentSessionsSync().count { it.language.equals(langName, ignoreCase = true) }
            val vocabCount = vocabularyRepository.getRecentVocabularySync(langName).size
            val mistakesCount = vocabularyRepository.getRecentMistakesSync(langName).size
            Triple(sessionsCount, vocabCount, mistakesCount)
        }
    }
}


class TutorViewModelFactory(
    private val application: Application,
    private val liveChatRepository: ILiveChatRepository,
    private val acceleratorRepository: IAcceleratorRepository,
    private val dictionaryRepository: IDictionaryRepository,
    private val contentRepository: IContentRepository,
    private val vocabularyRepository: IVocabularyRepository,
    private val mnemonicRepository: IMnemonicRepository,
    private val shadowingRepository: IShadowingRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(TutorViewModel::class.java)) {
            val savedStateHandle = extras.createSavedStateHandle()
            @Suppress("UNCHECKED_CAST")
            return TutorViewModel(
                application,
                savedStateHandle,
                liveChatRepository,
                acceleratorRepository,
                dictionaryRepository,
                contentRepository,
                vocabularyRepository,
                mnemonicRepository,
                shadowingRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
