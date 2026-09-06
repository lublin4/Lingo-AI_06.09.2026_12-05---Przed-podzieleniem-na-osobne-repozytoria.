package com.example.ui.speech

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

open class TutorSpeechHelper(context: Context, private val onInitSuccess: () -> Unit = {}) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var pendingTextToSpeak: Pair<String, String>? = null
    private var pendingCefrLevel: String = "B1"
    private var pendingSpeed: Float = 1.0f
    private var pendingRateToSpeak: Triple<String, Float, String>? = null
    private var pendingProgressCallback: ((Int, Int) -> Unit)? = null
    private var isInitialized = false
    private val activeUtteranceIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    var onSpeechFinished: (() -> Unit)? = null
    var onProgressCallback: ((Int, Int) -> Unit)? = null
    var isSpeaking: Boolean by mutableStateOf(false)
    var selectedVoice: String by mutableStateOf(
        try {
            context.getSharedPreferences("lingo_prefs", Context.MODE_PRIVATE).getString("selected_voice", "Sulafat") ?: "Sulafat"
        } catch (e: Exception) { "Sulafat" }
    )
    var currentLanguageName: String = "English"

    init {
        // Initialize TextToSpeech asynchronously to avoid Main Thread blocking or crashes
        Thread {
            try {
                tts = TextToSpeech(context.applicationContext) { status ->
                    try {
                        if (status == TextToSpeech.SUCCESS) {
                            isInitialized = true
                            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {
                                    mainHandler.post {
                                        isSpeaking = true
                                    }
                                }
                                override fun onDone(utteranceId: String?) {
                                    if (utteranceId != null) {
                                        activeUtteranceIds.remove(utteranceId)
                                        if (activeUtteranceIds.isEmpty()) {
                                            mainHandler.post {
                                                isSpeaking = false
                                                onSpeechFinished?.invoke()
                                                onProgressCallback?.invoke(-1, -1)
                                            }
                                        }
                                    }
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) {
                                    if (utteranceId != null) {
                                        activeUtteranceIds.remove(utteranceId)
                                        if (activeUtteranceIds.isEmpty()) {
                                            mainHandler.post {
                                                isSpeaking = false
                                                onSpeechFinished?.invoke()
                                                onProgressCallback?.invoke(-1, -1)
                                            }
                                        }
                                    }
                                }
                                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                                    if (utteranceId != null) {
                                        mainHandler.post {
                                            onProgressCallback?.invoke(start, end)
                                        }
                                    }
                                }
                            })
                            mainHandler.post {
                                onInitSuccess()
                            }
                            // Process pending rate speech if queued
                            pendingRateToSpeak?.let { (text, rate, lang) ->
                                val cb = pendingProgressCallback ?: { _, _ -> }
                                pendingRateToSpeak = null
                                pendingProgressCallback = null
                                Thread {
                                    try {
                                        speakWithRate(text, rate, lang, cb)
                                    } catch (e: Exception) {
                                        Log.e("TutorSpeechHelper", "Error speaking pending rate text", e)
                                    }
                                }.start()
                            }
                            // Process pending speech on a background thread if any
                            pendingTextToSpeak?.let { (text, lang) ->
                                pendingTextToSpeak = null
                                Thread {
                                    try {
                                        speak(text, lang, pendingCefrLevel, null, 0, pendingSpeed)
                                    } catch (e: Exception) {
                                        Log.e("TutorSpeechHelper", "Error speaking pending text", e)
                                    }
                                }.start()
                            }
                        } else {
                            Log.e("TutorSpeechHelper", "TextToSpeech initialization failed with status: $status")
                        }
                    } catch (e: Exception) {
                        Log.e("TutorSpeechHelper", "Error in TextToSpeech OnInitListener", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("TutorSpeechHelper", "Fatal error during TextToSpeech initialization", e)
            }
        }.start()
    }

    fun updateSelectedVoice(newVoice: String) {
        selectedVoice = newVoice.trim()
    }

    private fun configureTtsVoiceAndRate(
        languageName: String,
        voiceProfile: String,
        cefrLevel: String = "B1",
        customSpeechRate: Float? = null,
        stressLevel: Int = 0,
        speedMultiplier: Float = 1.0f
    ) {
        val locale = getLocaleForLanguage(languageName)
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val langOnly = Locale.forLanguageTag(locale.language)
            val retryResult = tts?.setLanguage(langOnly)
            if (retryResult == TextToSpeech.LANG_MISSING_DATA || retryResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TutorSpeechHelper", "Language $languageName ($locale) not supported, fallback to US English.")
                tts?.setLanguage(Locale.US)
            }
        }

        // Advanced Voice Selection: prioritize high-fidelity Neural/WaveNet/HD voices and specific gender profiles
        try {
            val voices = tts?.voices
            if (!voices.isNullOrEmpty()) {
                val localeVoices = voices.filter {
                    it.locale.language.equals(locale.language, ignoreCase = true) &&
                    (locale.country.isEmpty() || it.locale.country.equals(locale.country, ignoreCase = true) || it.locale.country.isEmpty())
                }

                if (localeVoices.isNotEmpty()) {
                    val profileKey = voiceProfile.trim().lowercase()

                    val scoredVoices = localeVoices.map { voice ->
                        var score = 0
                        val nameLower = voice.name.lowercase()

                        // Quality metric from Android TTS
                        score += when (voice.quality) {
                            Voice.QUALITY_VERY_HIGH -> 500
                            Voice.QUALITY_HIGH -> 400
                            Voice.QUALITY_NORMAL -> 250
                            else -> 100
                        }

                        // Neural / WaveNet / HD studio indicator bonus
                        if (nameLower.contains("neural") || nameLower.contains("wavenet") ||
                            nameLower.contains("natural") || nameLower.contains("studio") ||
                            nameLower.contains("enhanced") || nameLower.contains("premium")) {
                            score += 400
                        }

                        // Google cloud high-def network voices bonus
                        if (voice.isNetworkConnectionRequired) {
                            score += 200
                        }

                        // Avoid robotic fallback engines
                        if (nameLower.contains("fallback") || nameLower.contains("espeak")) {
                            score -= 300
                        }

                        // Detailed Male vs Female acoustic identifiers in Android/Google TTS
                        val isMale = nameLower.contains("male") || nameLower.contains("-m-") || nameLower.contains("_m_") ||
                                     nameLower.contains("#m") || nameLower.contains("m0") || nameLower.contains("m1") ||
                                     nameLower.contains("m2") || nameLower.contains("iom") || nameLower.contains("tpd") ||
                                     nameLower.contains("deb") || nameLower.contains("jmk") || nameLower.contains("vda") ||
                                     nameLower.contains("eed") || nameLower.contains("man") || nameLower.contains("guy")

                        val isFemale = nameLower.contains("female") || nameLower.contains("-f-") || nameLower.contains("_f_") ||
                                       nameLower.contains("#f") || nameLower.contains("f0") || nameLower.contains("f1") ||
                                       nameLower.contains("f2") || nameLower.contains("iol") || nameLower.contains("ana") ||
                                       nameLower.contains("deg") || nameLower.contains("oda") || nameLower.contains("bld") ||
                                       nameLower.contains("woman") || nameLower.contains("girl")

                        when (profileKey) {
                            "sulafat" -> {
                                // Sulafat: Warm & Calm (Female focus, comforting tone)
                                if (isFemale) score += 700
                                if (isMale) score -= 500
                                if (nameLower.contains("calm") || nameLower.contains("soothing") || nameLower.contains("bld") || nameLower.contains("oda")) score += 350
                            }
                            "erinome" -> {
                                // Erinome: Clear & Precise (Male focus, didactic, articulate)
                                if (isMale) score += 700
                                if (isFemale) score -= 500
                                if (nameLower.contains("clear") || nameLower.contains("firm") || nameLower.contains("tpd") || nameLower.contains("precise")) score += 350
                            }
                            "despina" -> {
                                // Despina: Smooth & Natural (Female focus, melodic, fluent)
                                if (isFemale) score += 700
                                if (isMale) score -= 500
                                if (nameLower.contains("natural") || nameLower.contains("smooth") || nameLower.contains("iol") || nameLower.contains("ana")) score += 350
                            }
                            "achird" -> {
                                // Achird: Friendly & Conversational (Male focus, charismatic)
                                if (isMale) score += 700
                                if (isFemale) score -= 500
                                if (nameLower.contains("deep") || nameLower.contains("studio") || nameLower.contains("iom") || nameLower.contains("deb") || nameLower.contains("natural")) score += 300
                            }
                            else -> {
                                if (isMale) score += 400
                            }
                        }

                        voice to score
                    }

                    val bestVoice = scoredVoices.maxByOrNull { it.second }?.first
                    if (bestVoice != null) {
                        tts?.voice = bestVoice
                        Log.i("TutorSpeechHelper", "Selected premium voice for $voiceProfile in $languageName: ${bestVoice.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TutorSpeechHelper", "Error choosing high quality voice", e)
        }

        // Natural human conversational pacing calibration (matching YouTube 1.0x native conversational tempo ~140 WPM)
        // Standard Android TTS at 1.0 is robotic and lacks human pauses (~175 WPM).
        // A base rate of 0.88f delivers natural, warm, and highly intelligible conversational cadence.
        val baseSpeedRate = when (stressLevel) {
            1 -> 0.96f // Moderate pressure (fluent, active tempo)
            2 -> 1.04f // High pressure (rapid native pace)
            else -> when (cefrLevel.uppercase()) {
                "A1" -> 0.80f // High clarity, gentle pacing
                "A2" -> 0.84f
                "B1" -> 0.88f // Balanced natural conversational flow (1:1 with YouTube native speakers)
                "B2" -> 0.90f
                "C1", "C2" -> 0.93f
                else -> 0.88f
            }
        }

        val profileKey = voiceProfile.trim().lowercase()
        val speedRate = (customSpeechRate ?: when (profileKey) {
            "sulafat" -> baseSpeedRate * 0.95f // Soft, warm, comforting tempo
            "erinome" -> baseSpeedRate * 1.00f // Balanced, clear, articulate cadence
            "despina" -> baseSpeedRate * 0.98f // Natural, fluid melodic tempo
            "achird" -> baseSpeedRate * 1.02f // Lively, friendly conversational pace
            else -> baseSpeedRate * 1.00f
        }) * speedMultiplier

        // Distinct natural pitch modulation creating warm, non-metallic timbre
        val pitch = when (profileKey) {
            "sulafat" -> 0.92f // Velvet warm, soothing lower pitch
            "erinome" -> 1.00f // Crisp, natural neutral articulation
            "despina" -> 1.02f // Smooth, warm, natural female voice
            "achird" -> 0.92f // Friendly conversational charismatic male voice
            else -> 0.96f
        }

        tts?.setSpeechRate(speedRate.coerceIn(0.5f, 2.0f))
        tts?.setPitch(pitch)
    }

    fun speak(text: String, languageName: String, cefrLevel: String = "B1", customSpeechRate: Float? = null, stressLevel: Int = 0, speedMultiplier: Float = 1.0f) {
        currentLanguageName = languageName
        if (!isInitialized) {
            Log.w("TutorSpeechHelper", "TTS not initialized yet. Queuing request.")
            pendingTextToSpeak = text to languageName
            pendingCefrLevel = cefrLevel
            pendingSpeed = speedMultiplier
            return
        }
        tts?.stop()
        isSpeaking = true

        configureTtsVoiceAndRate(
            languageName = languageName,
            voiceProfile = selectedVoice,
            cefrLevel = cefrLevel,
            customSpeechRate = customSpeechRate,
            stressLevel = stressLevel,
            speedMultiplier = speedMultiplier
        )

        // Clean text of brackets, sound tags, markdown, and meta notations
        val cleanedText = sanitizeTextForTts(text)
        if (cleanedText.isEmpty()) return

        val utteranceId = "tutor_speech_${System.currentTimeMillis()}"
        activeUtteranceIds.add(utteranceId)
        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Ultra-low latency sentence streaming:
     * Appends sentences to the TTS queue in real-time as Gemini generates them.
     */
    fun speakStreamingSentence(
        sentence: String,
        isFirstSentence: Boolean,
        isFinalSentence: Boolean,
        languageName: String,
        cefrLevel: String = "B1",
        customSpeechRate: Float? = null,
        stressLevel: Int = 0,
        speedMultiplier: Float = 1.0f
    ) {
        val cleanedText = sanitizeTextForTts(sentence)
        if (cleanedText.isEmpty()) return

        currentLanguageName = languageName
        if (!isInitialized) {
            if (isFirstSentence) {
                pendingTextToSpeak = cleanedText to languageName
                pendingCefrLevel = cefrLevel
                pendingSpeed = speedMultiplier
            }
            return
        }

        val queueMode = if (isFirstSentence) {
            activeUtteranceIds.clear()
            tts?.stop()
            configureTtsVoiceAndRate(
                languageName = languageName,
                voiceProfile = selectedVoice,
                cefrLevel = cefrLevel,
                customSpeechRate = customSpeechRate,
                stressLevel = stressLevel,
                speedMultiplier = speedMultiplier
            )
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }

        isSpeaking = true
        val utteranceId = "tutor_stream_${System.currentTimeMillis()}_${(100..999).random()}"
        activeUtteranceIds.add(utteranceId)
        tts?.speak(cleanedText, queueMode, null, utteranceId)
    }

    private fun sanitizeTextForTts(text: String): String {
        return text
            .replace(Regex("\\[.*?\\]"), "") // Remove sound/action tags like [laughs], [polskie tłumaczenie]
            .replace(Regex("\\(.*?\\)"), "") // Remove parenthetical notes
            .replace(Regex("[*#_`~]"), "") // Remove markdown
            .replace("\\\"", "\"")
            .replace("\\n", " ")
            .replace("\\t", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun speakPreview(
        text: String,
        languageName: String,
        voiceProfile: String,
        cefrLevel: String = "B2",
        customSpeechRate: Float? = null,
        onFinished: (() -> Unit)? = null
    ) {
        if (!isInitialized) {
            Log.w("TutorSpeechHelper", "TTS not initialized for preview.")
            return
        }
        tts?.stop()
        isSpeaking = true

        configureTtsVoiceAndRate(
            languageName = languageName,
            voiceProfile = voiceProfile,
            cefrLevel = cefrLevel,
            customSpeechRate = customSpeechRate ?: getSuggestedSpeedForCefr(cefrLevel),
            stressLevel = 0,
            speedMultiplier = 1.0f
        )

        val cleanedText = text.replace(Regex("\\[.*?\\]"), "")
                              .replace(Regex("\\(.*?\\)"), "")
                              .replace(Regex("\\s+"), " ")
                              .trim()

        val tempUtteranceId = "voice_preview_${System.currentTimeMillis()}"
        activeUtteranceIds.add(tempUtteranceId)
        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, tempUtteranceId)
    }

    companion object {
        fun getSuggestedSpeedForCefr(cefrLevel: String): Float {
            return when (cefrLevel.uppercase().trim()) {
                "A1" -> 0.80f
                "A2" -> 0.86f
                "B1" -> 0.92f
                "B2" -> 0.97f
                "C1", "C2" -> 1.02f
                else -> 0.94f
            }
        }
    }

    open fun speakWithRate(text: String, rate: Float, languageName: String, onSpeechProgress: (Int, Int) -> Unit) {
        currentLanguageName = languageName
        if (!isInitialized) {
            Log.w("TutorSpeechHelper", "TTS not initialized yet. Queuing rate request.")
            pendingRateToSpeak = Triple(text, rate, languageName)
            pendingProgressCallback = onSpeechProgress
            return
        }
        onProgressCallback = onSpeechProgress
        activeUtteranceIds.clear()
        tts?.stop()
        isSpeaking = true

        configureTtsVoiceAndRate(
            languageName = languageName,
            voiceProfile = selectedVoice,
            cefrLevel = "B1",
            customSpeechRate = null,
            stressLevel = 0,
            speedMultiplier = rate
        )

        val cleanedText = text.replace(Regex("\\[.*?\\]"), "")
                              .replace(Regex("\\(.*?\\)"), "")
                              .replace(Regex("\\s+"), " ")
                              .trim()

        val utteranceId = "shadowing_rate_${System.currentTimeMillis()}"
        activeUtteranceIds.add(utteranceId)
        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    open fun speakWithRate(text: String, rate: Float, onSpeechProgress: (Int, Int) -> Unit) {
        speakWithRate(text, rate, currentLanguageName, onSpeechProgress)
    }

    fun playTurnEndEarcon() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
            mainHandler.postDelayed({ toneGen.release() }, 200)
        } catch (e: Exception) {
            Log.e("TutorSpeechHelper", "Error playing earcon: ${e.message}")
        }
    }

    open fun stop() {
        activeUtteranceIds.clear()
        tts?.stop()
        isSpeaking = false
        onProgressCallback?.invoke(-1, -1)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isSpeaking = false
    }

    private fun getLocaleForLanguage(language: String): Locale {
        val clean = language.trim().lowercase()
        return when {
            clean.contains("spanish") || clean.contains("hiszpań") || clean.contains("hiszpan") || clean == "es" -> Locale.forLanguageTag("es-ES")
            clean.contains("french") || clean.contains("francusk") || clean == "fr" -> Locale.FRANCE
            clean.contains("german") || clean.contains("niemiec") || clean == "de" -> Locale.GERMANY
            clean.contains("italian") || clean.contains("włosk") || clean.contains("wlosk") || clean == "it" -> Locale.ITALY
            clean.contains("japanese") || clean.contains("japoń") || clean.contains("japon") || clean == "ja" -> Locale.JAPAN
            clean.contains("chinese") || clean.contains("chiń") || clean.contains("chin") || clean == "zh" -> Locale.CHINA
            clean.contains("portuguese") || clean.contains("portugal") || clean == "pt" -> Locale.forLanguageTag("pt-PT")
            clean.contains("korean") || clean.contains("koreań") || clean.contains("korean") || clean == "ko" -> Locale.KOREA
            clean.contains("russian") || clean.contains("rosyj") || clean == "ru" -> Locale.forLanguageTag("ru-RU")
            clean.contains("polish") || clean.contains("pols") || clean == "pl" -> Locale.forLanguageTag("pl-PL")
            clean.contains("english") || clean.contains("angiel") || clean == "en" -> Locale.US
            else -> {
                Log.w("TutorSpeechHelper", "Unknown language string '$language', defaulting to English/US locale")
                Locale.US
            }
        }
    }
}
