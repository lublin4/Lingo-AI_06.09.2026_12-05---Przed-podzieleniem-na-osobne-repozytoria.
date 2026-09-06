package com.example.ui.shadowing

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.repository.ShadowingRepository
import com.example.data.repository.VocabularyRepository
import com.example.ui.speech.TutorSpeechHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShadowingViewModelTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var shadowingRepository: ShadowingRepository
    private lateinit var vocabularyRepository: VocabularyRepository
    private lateinit var viewModel: ShadowingViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        shadowingRepository = ShadowingRepository(db.shadowingDao())
        vocabularyRepository = VocabularyRepository(db.tutorDao())
        viewModel = ShadowingViewModel(app, shadowingRepository, vocabularyRepository)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testDefaultInitialStates() {
        assertEquals("Technologia i przyszłość", viewModel.topic.value)
        assertEquals("Spanish", viewModel.targetLanguage.value)
        assertFalse(viewModel.tempoCycleActive.value)
        assertEquals(0, viewModel.currentRepetition.value)
        assertEquals(1.0f, viewModel.currentTempo.value)
        assertEquals(ShadowingMode.STANDARD, viewModel.shadowingMode.value)
    }

    @Test
    fun testTempoKineticsTransitions() = runTest {
        val fakeSpeechHelper = object : TutorSpeechHelper(app) {
            val speeds = mutableListOf<Float>()
            override fun speakWithRate(text: String, rate: Float, onSpeechProgress: (Int, Int) -> Unit) {
                speeds.add(rate)
                onSpeechFinished?.invoke()
            }
            override fun stop() {}
        }

        // Trigger startTempoCycle in ViewModel
        viewModel.startTempoCycle("Test text", fakeSpeechHelper)
        
        // Let background coroutines run. Since delay is simulated inside runTest, 
        // advanceUntilIdle() will instantly skip delays and execute the full cycle.
        testScheduler.advanceUntilIdle()

        // Verify the 4 speed levels: 0.65x -> 0.75x -> 0.85x -> 1.0x
        assertEquals(4, fakeSpeechHelper.speeds.size)
        assertEquals(0.65f, fakeSpeechHelper.speeds[0], 0.01f)
        assertEquals(0.75f, fakeSpeechHelper.speeds[1], 0.01f)
        assertEquals(0.85f, fakeSpeechHelper.speeds[2], 0.01f)
        assertEquals(1.0f, fakeSpeechHelper.speeds[3], 0.01f)
        
        // After finishing, tempoCycleActive must turn false and tempo resets to default
        assertFalse(viewModel.tempoCycleActive.value)
        assertEquals(1.0f, viewModel.currentTempo.value)
        assertEquals(0, viewModel.currentRepetition.value)
    }

    @Test
    fun testModeSwitchingAndParameters() {
        viewModel.setTopic("Muzyka")
        assertEquals("Muzyka", viewModel.topic.value)

        viewModel.setLanguage("French")
        assertEquals("French", viewModel.targetLanguage.value)

        viewModel.setLyricsInput("Coucou")
        assertEquals("Coucou", viewModel.lyricsInput.value)

        viewModel.setPodcastInput("Welcome podcast")
        assertEquals("Welcome podcast", viewModel.podcastInput.value)
    }

    @Test
    fun testStopTempoCycle() {
        val fakeSpeechHelper = object : TutorSpeechHelper(app) {
            var stopped = false
            override fun stop() {
                stopped = true
            }
        }
        
        viewModel.stopTempoCycle(fakeSpeechHelper)
        assertFalse(viewModel.tempoCycleActive.value)
        assertEquals(0, viewModel.currentRepetition.value)
        assertEquals(1.0f, viewModel.currentTempo.value)
        assertTrue(fakeSpeechHelper.stopped)
    }
}
