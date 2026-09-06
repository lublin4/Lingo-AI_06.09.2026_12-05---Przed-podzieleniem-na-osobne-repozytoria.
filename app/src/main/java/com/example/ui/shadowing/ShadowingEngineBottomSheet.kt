package com.example.ui.shadowing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.speech.TutorSpeechHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowingEngineBottomSheet(
    viewModel: ShadowingViewModel,
    speechHelper: TutorSpeechHelper,
    onNavigateToChat: (scenarioTitle: String, scenarioKey: String, initialPrompt: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTopic by viewModel.topic.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val inputContent by ShadowingEngine.currentInput.collectAsStateWithLifecycle()

    val tempoCycleActive by viewModel.tempoCycleActive.collectAsStateWithLifecycle()
    val currentRepetition by viewModel.currentRepetition.collectAsStateWithLifecycle()
    val currentTempo by viewModel.currentTempo.collectAsStateWithLifecycle()

    var activeStep by remember { mutableStateOf(1) }
    var showTranslation by remember { mutableStateOf(false) }

    // Manual input tab state (YouTube vs Custom Prompt)
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Wideo YouTube / TED", "Własny Prompt")

    var youtubeUrlInput by remember { mutableStateOf("") }
    var youtubeTitleInput by remember { mutableStateOf("") }
    var customPromptInput by remember { mutableStateOf("") }

    // Trigger generation automatically when engine is launched with an external source
    LaunchedEffect(inputContent) {
        if (inputContent != null) {
            val source = inputContent?.source
            if (source !is ShadowingSource.YouTubeUrl && source !is ShadowingSource.CustomPrompt) {
                viewModel.loadNewSegmentFromInput(inputContent!!)
            } else {
                // Pre-populate fields if manual source is passed
                if (source is ShadowingSource.YouTubeUrl) {
                    youtubeUrlInput = source.videoUrl
                    youtubeTitleInput = source.videoTitle ?: ""
                    selectedTab = 0
                } else if (source is ShadowingSource.CustomPrompt) {
                    customPromptInput = source.userPrompt
                    selectedTab = 1
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF121216),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = activeTopic.ifBlank { "Uniwersalny Silnik Shadowing" },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val sourceLabel = when (inputContent?.source) {
                        is ShadowingSource.StoryParagraph -> "Źródło: Akapit Opowiadania 📖"
                        is ShadowingSource.LiveChatMessage -> "Źródło: Czat AI Lektor 🗣️"
                        is ShadowingSource.VocabularyIsland -> "Źródło: Słowniczek Językowy 🏝️"
                        is ShadowingSource.YouTubeUrl -> "Źródło: YouTube / Video 📺"
                        is ShadowingSource.CustomPrompt -> "Źródło: Własny Prompt ⚡"
                        is ShadowingSource.AiGenerated -> "Źródło: Generowane przez AI 🤖"
                        else -> "Brak określonego źródła"
                    }
                    Text(
                        text = "$sourceLabel • $targetLanguage",
                        color = Color(0xFF64B5F6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij", tint = Color.White)
                }
            }

            Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 20.dp)
            ) {
                when (uiState) {
                    is ShadowingUiState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF64B5F6))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Silnik Lingo generuje Twój trening...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                    is ShadowingUiState.Error -> {
                        val errMsg = (uiState as ShadowingUiState.Error).message
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Error, contentDescription = "Błąd", tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Wystąpił błąd silnika", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(errMsg, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    inputContent?.let { viewModel.loadNewSegmentFromInput(it) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6))
                            ) {
                                Text("Spróbuj ponownie", color = Color.Black)
                            }
                        }
                    }
                    is ShadowingUiState.Success -> {
                        val segment = (uiState as ShadowingUiState.Success).segment
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Stage 1-6 selector using a compact, scrollable LazyRow with a dark background
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1A1A22), RoundedCornerShape(12.dp))
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val stepTitles = listOf("1. Percepcja", "2. Gramatyka", "3. AI Accelerator", "4. Użyj")
                                items(stepTitles.size) { idx ->
                                    val stepNum = idx + 1
                                    val isActive = activeStep == stepNum
                                    val isCompleted = activeStep > stepNum

                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isActive) Color(0xFF64B5F6).copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable { activeStep = stepNum }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(
                                                    when {
                                                        isActive -> Color(0xFF64B5F6)
                                                        isCompleted -> Color(0xFF64B5F6).copy(alpha = 0.5f)
                                                        else -> Color.White.copy(alpha = 0.15f)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCompleted) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "$stepNum",
                                                    color = if (isActive) Color.Black else Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            text = stepTitles[idx],
                                            color = if (isActive) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Active step content
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF15151A)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    when (activeStep) {
                                        1 -> { // 1. PERCEPCJA (Rozumienie Materiału)
                                            Text(
                                                text = segment.foreignText,
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                lineHeight = 22.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            if (showTranslation) {
                                                Text(
                                                    text = segment.nativeTranslation,
                                                    color = Color(0xFF64B5F6).copy(alpha = 0.9f),
                                                    fontSize = 14.sp,
                                                    lineHeight = 20.sp,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                                        .padding(8.dp)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Prędkość odtwarzania:",
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                OutlinedButton(
                                                    onClick = { showTranslation = !showTranslation },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, if (showTranslation) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.3f)),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = if (showTranslation) Color(0xFF64B5F6).copy(alpha = 0.15f) else Color.Transparent
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = if (showTranslation) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null,
                                                        tint = if (showTranslation) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (showTranslation) "Ukryj PL" else "Pokaż PL",
                                                        color = if (showTranslation) Color(0xFF64B5F6) else Color.White,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            var activeBsSpeed by remember { mutableStateOf<Float?>(null) }
                                            LaunchedEffect(speechHelper.isSpeaking) {
                                                if (!speechHelper.isSpeaking) {
                                                    activeBsSpeed = null
                                                }
                                            }

                                            val speeds = listOf(
                                                0.65f to "0.65x",
                                                0.75f to "0.75x",
                                                0.85f to "0.85x",
                                                1.0f to "1.0x"
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                speeds.forEach { (speedValue, speedLabel) ->
                                                    val isThisActive = speechHelper.isSpeaking && activeBsSpeed == speedValue
                                                    Button(
                                                        onClick = {
                                                            if (isThisActive) {
                                                                speechHelper.stop()
                                                                activeBsSpeed = null
                                                            } else {
                                                                speechHelper.stop()
                                                                activeBsSpeed = speedValue
                                                                speechHelper.speakWithRate(segment.foreignText, speedValue, targetLanguage) { _, _ -> }
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(38.dp),
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isThisActive) Color(0xFF64B5F6) else Color(0xFF1E293B),
                                                            contentColor = if (isThisActive) Color.Black else Color.White
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isThisActive) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                                                            contentDescription = null,
                                                            tint = if (isThisActive) Color.Black else Color(0xFF64B5F6),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = speedLabel,
                                                            color = if (isThisActive) Color.Black else Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }

                                            // Cykl Adaptacyjny Tempo Engine
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                color = Color(0xFF1E293B),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.4f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = if (tempoCycleActive) "Cykl Adaptacyjny: Powtórzenie $currentRepetition/4 (${String.format("%.2fx", currentTempo)})" else "Cykl Adaptacyjny (0.65x -> 1.0x)",
                                                            color = Color(0xFFFFC107),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (!tempoCycleActive) {
                                                            Button(
                                                                onClick = { viewModel.startTempoCycle(segment.foreignText, speechHelper) },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text("Start", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        } else {
                                                            Button(
                                                                onClick = { viewModel.stopTempoCycle(speechHelper) },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text("Stop", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                    if (tempoCycleActive) {
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        LinearProgressIndicator(
                                                            progress = { currentRepetition.toFloat() / 4f },
                                                            color = Color(0xFFFFC107),
                                                            trackColor = Color.White.copy(alpha = 0.1f),
                                                            modifier = Modifier.fillMaxWidth().height(4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        2 -> { // 2. GRAMATYKA & PYTANIA (StoryLearning)
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = "Gramatyka & Pytania Sprawdzające (StoryLearning)",
                                                    color = Color(0xFF64B5F6),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Sprawdź zrozumienie tekstu i zobacz kluczowe struktury gramatyczne.",
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 11.sp
                                                )
                                                LazyColumn(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 220.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    val questions = segment.retrievedQuestions
                                                    if (!questions.isNullOrEmpty()) {
                                                        items(questions) { rq ->
                                                            var revealAnswer by remember { mutableStateOf(false) }
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                                                    .padding(8.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(rq.question, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                                    IconButton(
                                                                        onClick = { speechHelper.speak(rq.question, targetLanguage) },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(14.dp))
                                                                    }
                                                                }
                                                                if (revealAnswer) {
                                                                    Text("Wzorcowa odpowiedź: " + rq.answer, color = Color(0xFF64B5F6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                                                                } else {
                                                                    TextButton(
                                                                        onClick = { revealAnswer = true },
                                                                        contentPadding = PaddingValues(0.dp),
                                                                        modifier = Modifier.height(24.dp)
                                                                    ) {
                                                                        Text("Pokaż Odpowiedź", fontSize = 10.sp, color = Color(0xFF64B5F6))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    item {
                                                        Surface(
                                                            color = Color(0xFF0F172A),
                                                            shape = RoundedCornerShape(8.dp),
                                                            border = BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.3f)),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Column(modifier = Modifier.padding(10.dp)) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(14.dp))
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text("Wyłożenie Gramatyki (StoryLearning Masters)", color = Color(0xFF64B5F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = if (!segment.grammarExplanation.isNullOrBlank()) {
                                                                        segment.grammarExplanation.take(280) + "..."
                                                                    } else {
                                                                        "Konstrukcja: \"${segment.foreignText.take(40)}...\" wykorzystuje naturalne zestawienie czasów i spójników. Otwórz główny ekran Shadowing, aby zobaczyć pełne wyłożenie gramatyki Masters."
                                                                    },
                                                                    color = Color.White.copy(alpha = 0.8f),
                                                                    fontSize = 11.sp,
                                                                    lineHeight = 15.sp
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        3 -> { // 3. AI ACCELERATOR (Adaptive Context Evolution)
                                            LazyColumn(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 240.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(segment.transformations ?: emptyList()) { tr ->
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                                            .padding(10.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(tr.type.uppercase(), color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            Text(tr.instruction, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(tr.transformedText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                        Text(tr.translation, color = Color(0xFF38BDF8), fontSize = 11.5.sp)
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End
                                                        ) {
                                                            IconButton(
                                                                onClick = { speechHelper.speak(tr.transformedText, targetLanguage) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        4 -> { // 4. UŻYJ (Conor Quinn Method)
                                            Text(
                                                text = segment.communicationScenario ?: "Wciel się w rolę i użyj poznanych Wysp Językowych w konwersacji na żywo.",
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                lineHeight = 19.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            
                                            // Anchor Badge
                                            Surface(
                                                color = Color(0xFF1E293B),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Anchor, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text("Główna Kotwica Językowa:", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        Text(segment.foreignText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    val prompt = """
                                                        Działaj jako przyjazny lektor AI i rozmówca w treningu aktywnej komunikacji metodą Conora Quinna (Wyspy Językowe).
                                                        
                                                        SCENARIUSZ ODGRYWANIA ROLI:
                                                        ${segment.communicationScenario ?: "Prowadź swobodną rozmowę na temat: ${segment.foreignText}"}
                                                        
                                                        GŁÓWNA WYSPA JĘZYKOWA UŻYTKOWNIKA (KOTWICA):
                                                        "${segment.foreignText}" (Tłumaczenie: "${segment.nativeTranslation}")
                                                        
                                                        INSTRUKCJA DLA LEKTORA AI:
                                                        1. Rozpocznij konwersację po angielsku wcielając się dokładnie w postać ze scenariusza.
                                                        2. Twoje pierwsze zdanie musi zadać pytanie lub zarysować sytuację tak, aby użytkownik miał naturalną okazję wypowiedzieć swoją Wyspę Językową ("${segment.foreignText}").
                                                        3. Bądź cierpliwy, podtrzymuj rozmowę i chwal za użycie kotwicy językowej!
                                                    """.trimIndent()
                                                    
                                                    val cleanTitle = "Konwersacja Shadowing: ${segment.foreignText.take(24)}..."
                                                    onNavigateToChat(
                                                        cleanTitle,
                                                        "shadowing_scenario_" + System.currentTimeMillis(),
                                                        prompt
                                                    )
                                                    onDismissRequest()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color.Black)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Uruchom Czat z AI Lektorem 🎙️", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                            }
                                        }
                                    }

                                    // Bottom navigation inside the Success Card
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { if (activeStep > 1) activeStep-- },
                                            enabled = activeStep > 1,
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                                        ) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Wstecz")
                                        }
                                        if (activeStep < 4) {
                                            Button(
                                                onClick = { activeStep++ },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)
                                            ) {
                                                Text("Dalej")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> { // Setup / Onboarding state if opened manually
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Tab selector for YouTube vs Custom Prompt
                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color(0xFF15151A),
                                contentColor = Color(0xFF64B5F6),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                tabTitles.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selectedTab == index) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.6f)) }
                                    )
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF15151A)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (selectedTab == 0) { // YouTube
                                        OutlinedTextField(
                                            value = youtubeUrlInput,
                                            onValueChange = { youtubeUrlInput = it },
                                            label = { Text("Wklej link YouTube / TED", color = Color.White.copy(alpha = 0.5f)) },
                                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF64B5F6),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                                            )
                                        )
                                        OutlinedTextField(
                                            value = youtubeTitleInput,
                                            onValueChange = { youtubeTitleInput = it },
                                            label = { Text("Tytuł wideo (opcjonalnie)", color = Color.White.copy(alpha = 0.5f)) },
                                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF64B5F6),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                if (youtubeUrlInput.isNotBlank()) {
                                                    val input = ShadowingInputContent(
                                                        source = ShadowingSource.YouTubeUrl(
                                                            videoUrl = youtubeUrlInput,
                                                            videoTitle = youtubeTitleInput.ifBlank { "Wideo YouTube" }
                                                        ),
                                                        targetLanguage = targetLanguage
                                                    )
                                                    ShadowingEngine.launchEngine(input)
                                                    viewModel.loadNewSegmentFromInput(input)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Przetwarzaj z YouTube", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    } else { // Custom Prompt
                                        OutlinedTextField(
                                            value = customPromptInput,
                                            onValueChange = { customPromptInput = it },
                                            label = { Text("Napisz temat lub wklej własny tekst...", color = Color.White.copy(alpha = 0.5f)) },
                                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                            modifier = Modifier.fillMaxWidth().height(120.dp),
                                            maxLines = 5,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF64B5F6),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                if (customPromptInput.isNotBlank()) {
                                                    val input = ShadowingInputContent(
                                                        source = ShadowingSource.CustomPrompt(
                                                            userPrompt = customPromptInput
                                                        ),
                                                        targetLanguage = targetLanguage
                                                    )
                                                    ShadowingEngine.launchEngine(input)
                                                    viewModel.loadNewSegmentFromInput(input)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Wygeneruj Trening z AI", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
