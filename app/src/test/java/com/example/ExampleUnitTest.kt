package com.example

import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiRequest
import com.example.data.api.Part
import com.example.data.api.TutorAiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import java.net.HttpURLConnection
import java.net.URL

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun test_retrofit_client_with_invalid_key() = runBlocking {
        println("=== TEST RETROFIT CLIENT ===")
        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = "Hello"))))
        )
        try {
            val response = GeminiClient.apiService.generateContent("gemini-3.1-flash-live-preview", "FAKE_KEY_FOR_TEST", request)
            println("Response: $response")
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            println("HTTP Error Code: ${e.code()}")
            println("HTTP Error Body: $errorBody")
            assertTrue("Expected API key error", errorBody.contains("API_KEY_INVALID") || errorBody.contains("not valid") || e.code() == 400)
        } catch (e: Exception) {
            println("Generic Error: ${e.message}")
            e.printStackTrace()
            fail("Should not throw generic exception: ${e.message}")
        }
        println("=============================")
    }

    @Test
    fun diagnose_gemini_connection() = runBlocking {
        println("=== DIAGNOSE GEMINI CONNECTION ===")
        println("FORCE RUN TO BYPASS CACHE")
        val sysKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "ERROR: ${e.message}" }
        println("BuildConfig.GEMINI_API_KEY: $sysKey")
        println("Is default placeholder: ${sysKey == "MY_GEMINI_API_KEY"}")
        println("Is empty: ${sysKey.isEmpty()}")

        // Try direct DNS & Ping to Google API
        try {
            val url = URL("https://generativelanguage.googleapis.com/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val code = connection.responseCode
            println("Direct connection to GenerativeLanguage API HTTP Code: $code")
        } catch (e: Exception) {
            println("Direct connection error: ${e.message}")
            e.printStackTrace()
        }

        // Try using the actual testApiConnection method with whatever key is configured
        if (sysKey.isNotEmpty() && sysKey != "MY_GEMINI_API_KEY") {
            println("Testing with system key...")
            val result = TutorAiService.testApiConnection(sysKey)
            if (result.isSuccess) {
                println("System key test success: ${result.getOrNull()}")
            } else {
                println("System key test failure: ${result.exceptionOrNull()?.message}")
                result.exceptionOrNull()?.printStackTrace()
            }
        } else {
            println("Skipping system key test (key is empty or placeholder)")
        }
        println("===============================")
    }
}


