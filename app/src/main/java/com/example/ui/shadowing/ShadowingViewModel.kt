package com.example.ui.shadowing

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Vocabulary
import com.example.data.AppDatabase
import com.example.data.context.UserProfileRepository
import com.example.data.context.AdaptiveContextEngine
import com.example.data.context.SemanticContrastResponse
import com.example.data.context.ContrastItem
import com.example.data.api.ShadowingSegmentResponse
import com.example.data.api.TutorAiService
import com.example.data.audio.AudioImportManager
import com.example.data.audio.ImportedAudioSegment
import com.example.data.audio.ShadowingAudioPlayer
import com.example.data.audio.TranscriptionService
import com.example.data.local.entities.ShadowingEntity
import com.example.data.repository.IShadowingRepository
import com.example.data.repository.IVocabularyRepository
import com.example.ui.speech.TutorSpeechHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ShadowingMode {
    STANDARD,
    SONGS,
    PODCAST
}

sealed interface ShadowingUiState {
    object Idle : ShadowingUiState
    object Loading : ShadowingUiState
    data class Success(val segment: ShadowingSegmentResponse) : ShadowingUiState
    data class Error(val message: String) : ShadowingUiState
}

class ShadowingViewModel(
    application: Application,
    private val shadowingRepository: IShadowingRepository,
    private val vocabularyRepository: IVocabularyRepository
) : AndroidViewModel(application) {

    private val audioImportManager = AudioImportManager(application)
    val shadowingAudioPlayer = ShadowingAudioPlayer(application)
    private val transcriptionService = TranscriptionService()

    private val adaptiveContextEngine = AdaptiveContextEngine(
        userProfileRepository = UserProfileRepository(AppDatabase.getDatabase(application).tutorDao()),
        vocabularyRepository = vocabularyRepository
    )

    private val _semanticContrast = MutableStateFlow<SemanticContrastResponse?>(null)
    val semanticContrast: StateFlow<SemanticContrastResponse?> = _semanticContrast.asStateFlow()

    private val _adaptiveAnchorInfo = MutableStateFlow<String?>(null)
    val adaptiveAnchorInfo: StateFlow<String?> = _adaptiveAnchorInfo.asStateFlow()

    private val _isImportMode = MutableStateFlow(false)
    val isImportMode: StateFlow<Boolean> = _isImportMode.asStateFlow()

    private val _importedSegments = MutableStateFlow<List<ImportedAudioSegment>>(emptyList())
    val importedSegments: StateFlow<List<ImportedAudioSegment>> = _importedSegments.asStateFlow()

    private val _currentImportedSegmentIndex = MutableStateFlow(0)
    val currentImportedSegmentIndex: StateFlow<Int> = _currentImportedSegmentIndex.asStateFlow()

    private val _importProgress = MutableStateFlow<String?>(null)
    val importProgress: StateFlow<String?> = _importProgress.asStateFlow()

    private var importedAudioPath: String? = null

    private val _uiState = MutableStateFlow<ShadowingUiState>(ShadowingUiState.Idle)
    val uiState: StateFlow<ShadowingUiState> = _uiState.asStateFlow()

    private val _topic = MutableStateFlow("Technologia i przyszłość")
    val topic: StateFlow<String> = _topic.asStateFlow()

    private val _targetLanguage = MutableStateFlow("Spanish")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _tempoCycleActive = MutableStateFlow(false)
    val tempoCycleActive: StateFlow<Boolean> = _tempoCycleActive.asStateFlow()

    private val _currentRepetition = MutableStateFlow(0)
    val currentRepetition: StateFlow<Int> = _currentRepetition.asStateFlow()

    private val _currentTempo = MutableStateFlow(1.0f)
    val currentTempo: StateFlow<Float> = _currentTempo.asStateFlow()

    private val _speechProgressRange = MutableStateFlow<Pair<Int, Int>?>(null)
    val speechProgressRange: StateFlow<Pair<Int, Int>?> = _speechProgressRange.asStateFlow()

    // Tryb kognitywny & metadane
    private val _shadowingMode = MutableStateFlow(ShadowingMode.STANDARD)
    val shadowingMode: StateFlow<ShadowingMode> = _shadowingMode.asStateFlow()

    private val _poeticContext = MutableStateFlow<String?>(null)
    val poeticContext: StateFlow<String?> = _poeticContext.asStateFlow()

    private val _metadataText = MutableStateFlow<String?>(null)
    val metadataText: StateFlow<String?> = _metadataText.asStateFlow()

    private val _lyricsInput = MutableStateFlow("")
    val lyricsInput: StateFlow<String> = _lyricsInput.asStateFlow()

    private val _podcastInput = MutableStateFlow("")
    val podcastInput: StateFlow<String> = _podcastInput.asStateFlow()

    fun setSpeechProgressRange(start: Int, end: Int) {
        _speechProgressRange.value = start to end
    }

    fun clearSpeechProgressRange() {
        _speechProgressRange.value = null
    }

    val history: StateFlow<List<ShadowingEntity>> = shadowingRepository.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            val savedModeStr = shadowingRepository.getCurrentMode()
            _shadowingMode.value = try { ShadowingMode.valueOf(savedModeStr) } catch (e: Exception) { ShadowingMode.STANDARD }
            _metadataText.value = shadowingRepository.getCurrentMetadata()
            // loadNewSegment()
        }
    }

    fun resetToIdle() {
        _uiState.value = ShadowingUiState.Idle
    }

    fun setLanguage(language: String) {
        _targetLanguage.value = language
        loadNewSegment()
    }

    fun setTopic(newTopic: String) {
        _topic.value = newTopic
        loadNewSegment()
    }

    fun setShadowingMode(mode: ShadowingMode) {
        _shadowingMode.value = mode
        stopTempoCycle()
        viewModelScope.launch {
            shadowingRepository.saveCurrentMode(mode.name)
            loadNewSegment()
        }
    }

    fun setLyricsInput(text: String) {
        _lyricsInput.value = text
    }

    fun setPodcastInput(text: String) {
        _podcastInput.value = text
    }

    fun startTempoCycle(text: String, speechHelper: TutorSpeechHelper) {
        viewModelScope.launch {
            _tempoCycleActive.value = true
            val isSongMode = _shadowingMode.value == ShadowingMode.SONGS
            for (rep in 1..4) {
                if (!_tempoCycleActive.value) break
                _currentRepetition.value = rep
                val tempo = if (isSongMode) 1.0f else when (rep) {
                    1 -> 0.65f
                    2 -> 0.75f
                    3 -> 0.85f
                    4 -> 1.0f
                    else -> 1.0f
                }
                _currentTempo.value = tempo
                
                var speechDone = false
                speechHelper.onSpeechFinished = {
                    speechDone = true
                }
                speechHelper.speakWithRate(text, tempo, _targetLanguage.value) { start, end ->
                    _speechProgressRange.value = start to end
                }
                
                while (!speechDone && _tempoCycleActive.value) {
                    kotlinx.coroutines.delay(100)
                }
                
                _speechProgressRange.value = null
                
                if (rep < 4 && _tempoCycleActive.value) {
                    kotlinx.coroutines.delay(1500)
                }
            }
            _tempoCycleActive.value = false
            _currentRepetition.value = 0
            _currentTempo.value = 1.0f
        }
    }

    fun stopTempoCycle(speechHelper: TutorSpeechHelper? = null) {
        _tempoCycleActive.value = false
        speechHelper?.stop()
        _speechProgressRange.value = null
        _currentRepetition.value = 0
        _currentTempo.value = 1.0f
    }

    fun loadNewSegment() {
        stopTempoCycle()
        _poeticContext.value = null
        _adaptiveAnchorInfo.value = null
        viewModelScope.launch {
            _uiState.value = ShadowingUiState.Loading
            try {
                val result: ShadowingSegmentResponse = when (_shadowingMode.value) {
                    ShadowingMode.STANDARD -> {
                        val adaptiveContext = adaptiveContextEngine.buildAdaptivePromptSegment(_targetLanguage.value)
                        TutorAiService.generateShadowingSegment(_topic.value, _targetLanguage.value, adaptiveContext)
                    }
                    ShadowingMode.SONGS -> {
                        val textToProcess = _lyricsInput.value.ifBlank { getSampleLyrics(_targetLanguage.value) }
                        val response = TutorAiService.processSongLyric(textToProcess, _targetLanguage.value)
                        _poeticContext.value = response.poeticContext
                        val meta = "Lyrics excerpt for: " + textToProcess.lineSequence().firstOrNull()?.take(25)
                        _metadataText.value = meta
                        shadowingRepository.saveCurrentMetadata(meta ?: "Piosenka")
                        response
                    }
                    ShadowingMode.PODCAST -> {
                        val textToProcess = _podcastInput.value.ifBlank { getSamplePodcastTranscript(_targetLanguage.value) }
                        _importProgress.value = "Ekstrakcja kluczowych momentów z podcastu..."
                        val highlights = TutorAiService.extractPodcastHighlights(textToProcess, _targetLanguage.value)
                        _importProgress.value = null
                        if (highlights.isNotEmpty()) {
                            _importedSegments.value = highlights
                            _currentImportedSegmentIndex.value = 0
                            _isImportMode.value = true
                            val firstSegment = highlights[0]
                            val meta = "Podcast highlights extractor"
                            _metadataText.value = meta
                            shadowingRepository.saveCurrentMetadata(meta)
                            
                            ShadowingSegmentResponse(
                                foreignText = firstSegment.foreignText,
                                nativeTranslation = firstSegment.nativeTranslation,
                                keyPhrases = firstSegment.keyPhrases ?: emptyList(),
                                retrievedQuestions = firstSegment.retrievedQuestions,
                                transformations = firstSegment.transformations,
                                communicationScenario = firstSegment.communicationScenario,
                                adaptiveAnchorInfo = null,
                                grammarExplanation = firstSegment.grammarExplanation
                            )
                        } else {
                            ShadowingSegmentResponse(
                                foreignText = "Błąd: Nie udało się wyodrębnić segmentów z transkrypcji.",
                                nativeTranslation = "Wystąpił problem przy ekstrakcji momentów z podcastu.",
                                keyPhrases = emptyList()
                            )
                        }
                    }
                }
                
                if (result.foreignText.startsWith("Błąd")) {
                    _uiState.value = ShadowingUiState.Error(result.foreignText)
                } else {
                    _adaptiveAnchorInfo.value = result.adaptiveAnchorInfo
                    _uiState.value = ShadowingUiState.Success(result)
                }
            } catch (e: Exception) {
                if (e is java.io.IOException || e is java.util.concurrent.TimeoutException || e is java.net.SocketTimeoutException || e.javaClass.name.contains("Timeout") || e.javaClass.name.contains("IO")) {
                    try {
                        val cachedSessions = shadowingRepository.getAllSessions().first()
                        val lastSession = cachedSessions.firstOrNull()
                        if (lastSession != null) {
                            val fallbackResponse = ShadowingSegmentResponse(
                                foreignText = lastSession.foreignPhrase,
                                nativeTranslation = lastSession.nativeTranslation,
                                keyPhrases = listOf(
                                    com.example.data.api.ShadowingKeyPhrase(lastSession.foreignPhrase, lastSession.nativeTranslation)
                                ),
                                retrievedQuestions = listOf(
                                    com.example.data.api.ShadowingRetrieveQuestion(
                                        question = "Co oznacza '${lastSession.foreignPhrase}'?",
                                        answer = lastSession.nativeTranslation
                                    )
                                ),
                                transformations = listOf(
                                    com.example.data.api.ShadowingTransformation(
                                        type = "Polite",
                                        transformedText = lastSession.foreignPhrase,
                                        translation = lastSession.nativeTranslation,
                                        instruction = "Brak dostępnych transformacji w trybie offline."
                                    )
                                ),
                                communicationScenario = "Wczytano z lokalnej pamięci podręcznej (Offline Fallback)",
                                poeticContext = lastSession.poeticContext,
                                adaptiveAnchorInfo = "Wczytano z lokalnej pamięci podręcznej (Offline Fallback)"
                            )
                            _adaptiveAnchorInfo.value = fallbackResponse.adaptiveAnchorInfo
                            _uiState.value = ShadowingUiState.Success(fallbackResponse)
                            return@launch
                        }
                    } catch (fallbackEx: Exception) {
                        // ignore and fall through to original error
                    }
                }
                _uiState.value = ShadowingUiState.Error(e.localizedMessage ?: "Nieznany błąd podczas generowania segmentu.")
            }
        }
    }

    fun loadNewSegmentFromInput(input: ShadowingInputContent) {
        stopTempoCycle()
        _poeticContext.value = null
        _adaptiveAnchorInfo.value = null
        _targetLanguage.value = input.targetLanguage
        viewModelScope.launch {
            _uiState.value = ShadowingUiState.Loading
            try {
                val promptText = when (val source = input.source) {
                    is ShadowingSource.StoryParagraph -> {
                        "Użyj poniższego akapitu z opowiadania '${source.storyTitle}' jako bazy do nauki i shadowing: ${source.paragraphText}"
                    }
                    is ShadowingSource.LiveChatMessage -> {
                        "Użyj poniższej wiadomości od lektora (${source.speakerName}) z czatu na temat '${source.contextTopic}' do nauki i shadowing: ${source.messageText}"
                    }
                    is ShadowingSource.VocabularyIsland -> {
                        "Użyj poniższego zwrotu/słówka ze słownika (Kategoria: ${source.category}) do nauki i shadowing: '${source.phrase}' (tłumaczenie: ${source.translation})"
                    }
                    is ShadowingSource.YouTubeUrl -> {
                        val titleHint = source.videoTitle?.let { " o tytule '$it'" } ?: ""
                        val transcriptHint = source.rawTranscriptHint?.let { " ze wskazówką transkrypcji: $it" } ?: ""
                        "Przeanalizuj film YouTube (${source.videoUrl})${titleHint}${transcriptHint} i wygeneruj na jego podstawie segment do shadowing."
                    }
                    is ShadowingSource.CustomPrompt -> {
                        "Wygeneruj segment na podstawie własnego promptu użytkownika (Domena: ${source.contextDomain}): ${source.userPrompt}"
                    }
                    is ShadowingSource.AiGenerated -> {
                        "Wygeneruj segment na temat: ${source.topic}"
                    }
                }
                
                val newTopicText = when (val source = input.source) {
                    is ShadowingSource.StoryParagraph -> "Opowiadanie: ${source.storyTitle}"
                    is ShadowingSource.LiveChatMessage -> "Konwersacja: ${source.contextTopic}"
                    is ShadowingSource.VocabularyIsland -> "Słówko: ${source.phrase}"
                    is ShadowingSource.YouTubeUrl -> source.videoTitle ?: "Wideo YouTube"
                    is ShadowingSource.CustomPrompt -> source.contextDomain
                    is ShadowingSource.AiGenerated -> source.topic
                }
                _topic.value = newTopicText
                
                val result = com.example.data.api.TutorAiService.generateShadowingSegment(promptText, input.targetLanguage, "Generowane z uniwersalnego silnika shadowing. Poziom użytkownika: ${input.userLevel}.")
                
                try {
                    shadowingRepository.saveSession(
                        phrase = result.foreignText,
                        translation = result.nativeTranslation,
                        language = input.targetLanguage,
                        learningMode = "STANDARD",
                        poeticContext = result.poeticContext ?: "",
                        metadata = newTopicText
                    )
                } catch (dbEx: Exception) {
                    android.util.Log.e("ShadowingViewModel", "Failed to save generated session to db", dbEx)
                }
                
                _adaptiveAnchorInfo.value = result.adaptiveAnchorInfo
                _uiState.value = ShadowingUiState.Success(result)
            } catch (e: Exception) {
                if (e is java.io.IOException || e is java.util.concurrent.TimeoutException || e is java.net.SocketTimeoutException || e.javaClass.name.contains("Timeout") || e.javaClass.name.contains("IO")) {
                    try {
                        val cachedSessions = shadowingRepository.getAllSessions().first()
                        val lastSession = cachedSessions.firstOrNull()
                        if (lastSession != null) {
                            val fallbackResponse = ShadowingSegmentResponse(
                                foreignText = lastSession.foreignPhrase,
                                nativeTranslation = lastSession.nativeTranslation,
                                keyPhrases = listOf(
                                    com.example.data.api.ShadowingKeyPhrase(lastSession.foreignPhrase, lastSession.nativeTranslation)
                                ),
                                retrievedQuestions = listOf(
                                    com.example.data.api.ShadowingRetrieveQuestion(
                                        question = "Co oznacza '${lastSession.foreignPhrase}'?",
                                        answer = lastSession.nativeTranslation
                                    )
                                ),
                                transformations = listOf(
                                    com.example.data.api.ShadowingTransformation(
                                        type = "Polite",
                                        transformedText = lastSession.foreignPhrase,
                                        translation = lastSession.nativeTranslation,
                                        instruction = "Brak dostępnych transformacji w trybie offline."
                                    )
                                ),
                                communicationScenario = "Wczytano z lokalnej pamięci podręcznej (Offline Fallback)",
                                poeticContext = lastSession.poeticContext,
                                adaptiveAnchorInfo = "Wczytano z lokalnej pamięci podręcznej (Offline Fallback)"
                            )
                            _adaptiveAnchorInfo.value = fallbackResponse.adaptiveAnchorInfo
                            _uiState.value = ShadowingUiState.Success(fallbackResponse)
                            return@launch
                        }
                    } catch (fallbackEx: Exception) {
                        // ignore and fall through
                    }
                }
                _uiState.value = ShadowingUiState.Error(e.localizedMessage ?: "Nieznany błąd silnika.")
            }
        }
    }

    private fun getSampleLyrics(lang: String): String {
        return when (lang.lowercase()) {
            "es", "spanish" -> """
                Bésame, bésame mucho
                Como si fuera esta noche la última vez
                Bésame, bésame mucho
                Que tengo miedo a perderte, perderte después
            """.trimIndent()
            "fr", "french" -> """
                Je ne veux pas travailler
                Je ne veux pas déjeuner
                Je veux seulement l'oublier
                Et puis je fume
            """.trimIndent()
            "de", "german" -> """
                Du hast mich gefragt und ich hab nichts gesagt
                Willst du bis der Tod euch scheidet
                Treu ihr sein für alle Tage
                Nein, nein
            """.trimIndent()
            "it", "italian" -> """
                Bella ciao, bella ciao, bella ciao ciao ciao
                Una mattina mi sono alzato
                E ho trovato l'invasore
            """.trimIndent()
            else -> """
                Yesterday, all my troubles seemed so far away
                Now it looks as though they're here to stay
                Oh, I believe in yesterday
            """.trimIndent()
        }
    }

    private fun getSamplePodcastTranscript(lang: String): String {
        return when (lang.lowercase()) {
            "es", "spanish" -> """
                Bienvenidos a este episodio especial de tecnología. Hoy hablaremos sobre el impacto real de la inteligencia artificial en la vida cotidiana de las personas. No cabe duda de que estamos viviendo una época de cambios sumamente acelerados, donde el aprendizaje continuo ya no es opcional, sino una necesidad imperiosa para mantenerse vigente en el mercado laboral. No obstante, debemos tener cautela para no caer en la exageración tecnológica y perder el toque humano que nos define.
            """.trimIndent()
            "fr", "french" -> """
                Bonjour et bienvenue dans notre podcast hebdomadaire. Aujourd'hui, nous explorons les merveilles de la gastronomie et de l'art de vivre à la française. Ce qui frappe les visiteurs de passage, c'est cette attention méticuleuse portée aux détails, cette quête insatiable de l'accord parfait entre mets et vins, et cette gastronomie chaleureuse qui transcende les simples barrières linguistiques.
            """.trimIndent()
            "de", "german" -> """
                Hallo und herzlich willkommen zu unserem täglichen Podcast. Heute beschäftigen wir uns mit dem Thema Nachhaltigkeit und Umweltschutz im modernen Alltag. Es ist von entscheidender Bedeutung, dass wir nicht nur theoretisch über den Klimawandel debattieren, sondern im Kleinen anfangen, wie etwa durch die Reduzierung von Plastikmüll und die bewusste Förderung lokaler, ökologischer Kreisläufe.
            """.trimIndent()
            "it", "italian" -> """
                Benvenuti a tutti nel nostro appuntamento quotidiano con la cultura. Oggi viaggiamo attraverso le strade storiche di Firenze, culla del Rinascimento italiano. Passeggiando per queste vie ricche di storia, si ha la netta sensazione che il tempo si sia quasi fermato, permettendo all'arte e alla bellezza di dialogare direttamente con l'animo del viaggiatore moderno.
            """.trimIndent()
            else -> """
                Hello and welcome to the TED Global Ideas Podcast. Today we are talking with top neuroscientists about cognitive load and modern learning design. We explore why standard rote memorization is so incredibly inefficient compared to multimodal active recall, spaced repetition, and physical-tactile feedback loops like hand gestures, rhythm tracking, and active shadowing practice.
            """.trimIndent()
        }
    }

    fun saveShadowingSession(phrase: String, translation: String) {
        viewModelScope.launch(Dispatchers.IO.limitedParallelism(1)) {
            shadowingRepository.saveSession(
                phrase = phrase,
                translation = translation,
                language = _targetLanguage.value,
                learningMode = _shadowingMode.value.name,
                poeticContext = _poeticContext.value,
                metadata = _metadataText.value
            )
        }
    }

    fun saveKeyPhraseToVocabulary(phrase: String, translation: String) {
        viewModelScope.launch(Dispatchers.IO.limitedParallelism(1)) {
            val cleanedWord = phrase.trim()
            val existing = vocabularyRepository.getVocabularyByWordAndLanguage(cleanedWord, _targetLanguage.value)
            if (existing == null) {
                val vocab = Vocabulary(
                    word = cleanedWord,
                    translation = translation.trim(),
                    language = _targetLanguage.value,
                    sentenceContext = "Shadowing AI Accelerator Island (Master Level)",
                    language_code = _targetLanguage.value,
                    polishWordTranslation = translation.trim(),
                    polishContextTranslation = "Wyspa Językowa Shadowing (Poziom Master)",
                    interference_tag = "LANGUAGE_ISLAND"
                )
                vocabularyRepository.insertVocabulary(vocab)
            } else if (existing.interference_tag != "LANGUAGE_ISLAND") {
                vocabularyRepository.updateVocabulary(
                    existing.copy(
                        interference_tag = "LANGUAGE_ISLAND",
                        polishContextTranslation = "Wyspa Językowa Shadowing (Poziom Master)"
                    )
                )
            }
        }
    }

    fun deleteShadowingSession(id: Long) {
        viewModelScope.launch(Dispatchers.IO.limitedParallelism(1)) {
            shadowingRepository.deleteSession(id)
        }
    }

    fun importAudioFile(uri: Uri) {
        viewModelScope.launch {
            _importProgress.value = "Kopiowanie pliku audio..."
            val localPath = audioImportManager.importAudio(uri)
            if (localPath == null) {
                _importProgress.value = "Nie udało się skopiować pliku."
                kotlinx.coroutines.delay(2000)
                _importProgress.value = null
                return@launch
            }
            
            importedAudioPath = localPath
            _importProgress.value = "Transkrypcja i segmentacja AI..."
            val segments = transcriptionService.transcribeAndSegmentAudio(localPath, _targetLanguage.value)
            
            if (segments.isEmpty()) {
                _importProgress.value = "Nie udało się wygenerować segmentów."
                kotlinx.coroutines.delay(2000)
                _importProgress.value = null
                return@launch
            }
            
            _importedSegments.value = segments
            _currentImportedSegmentIndex.value = 0
            _isImportMode.value = true
            
            // Prepare player with local path and current loop range
            shadowingAudioPlayer.prepare(localPath)
            val firstSegment = segments[0]
            shadowingAudioPlayer.setLoopRange(firstSegment.startTimeMs, firstSegment.endTimeMs, enableLoop = true)
            
            // Update UI State with first segment data
            _uiState.value = ShadowingUiState.Success(
                ShadowingSegmentResponse(
                    foreignText = firstSegment.foreignText,
                    nativeTranslation = firstSegment.nativeTranslation,
                    keyPhrases = firstSegment.keyPhrases ?: emptyList(),
                    retrievedQuestions = firstSegment.retrievedQuestions,
                    transformations = firstSegment.transformations,
                    communicationScenario = firstSegment.communicationScenario,
                    grammarExplanation = firstSegment.grammarExplanation
                )
            )
            
            _importProgress.value = null
        }
    }

    fun selectImportedSegment(index: Int) {
        val segments = _importedSegments.value
        if (index in segments.indices) {
            _currentImportedSegmentIndex.value = index
            val segment = segments[index]
            shadowingAudioPlayer.setLoopRange(segment.startTimeMs, segment.endTimeMs, enableLoop = true)
            
            // Update UI State with current segment
            _uiState.value = ShadowingUiState.Success(
                ShadowingSegmentResponse(
                    foreignText = segment.foreignText,
                    nativeTranslation = segment.nativeTranslation,
                    keyPhrases = segment.keyPhrases ?: emptyList(),
                    retrievedQuestions = segment.retrievedQuestions,
                    transformations = segment.transformations,
                    communicationScenario = segment.communicationScenario,
                    grammarExplanation = segment.grammarExplanation
                )
            )
        }
    }

    private val _isGeneratingGrammar = MutableStateFlow(false)
    val isGeneratingGrammar: StateFlow<Boolean> = _isGeneratingGrammar.asStateFlow()

    private val _isGeneratingAccelerator = MutableStateFlow(false)
    val isGeneratingAccelerator: StateFlow<Boolean> = _isGeneratingAccelerator.asStateFlow()

    fun generateGrammarExplanationForCurrentSegment() {
        val currentState = _uiState.value
        if (currentState is ShadowingUiState.Success) {
            val currentSegment = currentState.segment
            if (_isGeneratingGrammar.value) return
            _isGeneratingGrammar.value = true
            viewModelScope.launch {
                try {
                    val explanation = TutorAiService.generateDeepGrammarExplanation(
                        foreignText = currentSegment.foreignText,
                        targetLanguage = _targetLanguage.value
                    )
                    if (explanation.isNotBlank()) {
                        val updatedSegment = currentSegment.copy(grammarExplanation = explanation)
                        _uiState.value = ShadowingUiState.Success(updatedSegment)
                    }
                } finally {
                    _isGeneratingGrammar.value = false
                }
            }
        }
    }

    fun generateAcceleratorForCurrentSegment() {
        val currentState = _uiState.value
        if (currentState is ShadowingUiState.Success) {
            val currentSegment = currentState.segment
            if (_isGeneratingAccelerator.value) return
            _isGeneratingAccelerator.value = true
            viewModelScope.launch {
                try {
                    val newTransformations = TutorAiService.generateStoryAccelerations(
                        storyContext = currentSegment.foreignText,
                        targetLanguage = _targetLanguage.value
                    )
                    if (newTransformations.isNotEmpty()) {
                        val updatedSegment = currentSegment.copy(transformations = newTransformations)
                        _uiState.value = ShadowingUiState.Success(updatedSegment)
                    }
                } finally {
                    _isGeneratingAccelerator.value = false
                }
            }
        }
    }

    fun saveImportedSession(phrase: String, translation: String) {
        viewModelScope.launch(Dispatchers.IO.limitedParallelism(1)) {
            shadowingRepository.saveSession(
                phrase = phrase,
                translation = translation,
                language = _targetLanguage.value,
                audioPath = importedAudioPath,
                learningMode = _shadowingMode.value.name,
                poeticContext = _poeticContext.value,
                metadata = _metadataText.value
            )
        }
    }

    fun exitImportMode() {
        _isImportMode.value = false
        _importedSegments.value = emptyList()
        importedAudioPath = null
        shadowingAudioPlayer.stop()
        loadNewSegment()
    }

    override fun onCleared() {
        super.onCleared()
        shadowingAudioPlayer.release()
    }

    fun generateSemanticContrast(phrase: String) {
        viewModelScope.launch {
            _semanticContrast.value = null
            val response = TutorAiService.generateSemanticContrast(phrase, _targetLanguage.value)
            _semanticContrast.value = response
        }
    }

    fun saveContrastItemToVocabulary(item: ContrastItem) {
        viewModelScope.launch(Dispatchers.IO.limitedParallelism(1)) {
            val cleanedWord = item.word.trim()
            val existing = vocabularyRepository.getVocabularyByWordAndLanguage(cleanedWord, _targetLanguage.value)
            if (existing == null) {
                val vocab = Vocabulary(
                    word = cleanedWord,
                    translation = item.translation.trim(),
                    language = _targetLanguage.value,
                    sentenceContext = "Semantic Contrast Link (${item.relationType})",
                    language_code = _targetLanguage.value,
                    polishWordTranslation = item.translation.trim(),
                    polishContextTranslation = "Contrast Engine"
                )
                vocabularyRepository.insertVocabulary(vocab)
            }
        }
    }
}

class ShadowingViewModelFactory(
    private val application: Application,
    private val shadowingRepository: IShadowingRepository,
    private val vocabularyRepository: IVocabularyRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShadowingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShadowingViewModel(application, shadowingRepository, vocabularyRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
