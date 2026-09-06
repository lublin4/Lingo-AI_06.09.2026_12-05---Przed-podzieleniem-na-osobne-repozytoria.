package com.example.ui.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class TutorSpeechRecognizer(
    private val context: Context,
    private val onResults: (String) -> Unit,
    private val onErrorCallback: (String, Int) -> Unit = { _, _ -> }
) {
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening by mutableStateOf(false)
    var rmsLevel by mutableStateOf(0f)
    var isAvailable by mutableStateOf(false)
    private var currentLanguageCode: String = "en-US"
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        runOnMainThread {
            createRecognizerInternal()
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("TutorSpeechRecognizer", "Ready for speech (listening)")
                isListening = true
                rmsLevel = 0f
            }

            override fun onBeginningOfSpeech() {
                Log.d("TutorSpeechRecognizer", "Beginning of speech detected")
                isListening = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                rmsLevel = rmsdB
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("TutorSpeechRecognizer", "End of speech detected")
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                rmsLevel = 0f
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input timeout"
                    else -> "Unknown speech recognition error"
                }
                Log.w("TutorSpeechRecognizer", "Recognizer error code: $error ($message)")

                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || 
                    error == SpeechRecognizer.ERROR_CLIENT) {
                    recreateRecognizer()
                }

                onErrorCallback(message, error)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                rmsLevel = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestMatch = matches?.firstOrNull()
                if (!bestMatch.isNullOrBlank()) {
                    Log.d("TutorSpeechRecognizer", "Speech recognized: $bestMatch")
                    onResults(bestMatch)
                } else {
                    onErrorCallback("No match found", SpeechRecognizer.ERROR_NO_MATCH)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun createRecognizerInternal() {
        try {
            isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
            if (speechRecognizer != null) {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (e: Exception) {
                    Log.w("TutorSpeechRecognizer", "Error destroying existing recognizer: ${e.message}")
                }
                speechRecognizer = null
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
            isAvailable = true
            Log.d("TutorSpeechRecognizer", "SpeechRecognizer successfully initialized")
        } catch (e: Exception) {
            Log.e("TutorSpeechRecognizer", "Failed to create SpeechRecognizer: ${e.message}", e)
            isAvailable = false
        }
    }

    fun recreateRecognizer() {
        runOnMainThread {
            createRecognizerInternal()
        }
    }

    fun startListening(languageCode: String, force: Boolean = false) {
        currentLanguageCode = languageCode
        runOnMainThread {
            if (speechRecognizer == null) {
                createRecognizerInternal()
            }

            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("TutorSpeechRecognizer", "Cancel before start: ${e.message}")
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                // Silence thresholds allowing natural pauses without killing the mic
                putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2000L)
                putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1500L)
                putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 250L)
            }

            try {
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d("TutorSpeechRecognizer", "startListening called for language $languageCode")
            } catch (e: Exception) {
                Log.w("TutorSpeechRecognizer", "Start listening error: ${e.message}")
                createRecognizerInternal()
                try {
                    speechRecognizer?.startListening(intent)
                    isListening = true
                } catch (e2: Exception) {
                    Log.e("TutorSpeechRecognizer", "Retry start listening failed: ${e2.message}")
                }
            }
        }
    }

    fun stop() {
        runOnMainThread {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e("TutorSpeechRecognizer", "Error stopping: ${e.message}")
            }
            isListening = false
            rmsLevel = 0f
        }
    }

    fun shutdown() {
        runOnMainThread {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("TutorSpeechRecognizer", "Error destroying: ${e.message}")
            }
            speechRecognizer = null
            isListening = false
            rmsLevel = 0f
        }
    }
}
