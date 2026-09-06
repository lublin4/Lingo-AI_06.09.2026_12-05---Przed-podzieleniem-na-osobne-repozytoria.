package com.example.ui.shadowing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.*
import com.example.ui.speech.TutorSpeechHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowingScreen(
    viewModel: ShadowingViewModel,
    speechHelper: TutorSpeechHelper,
    onNavigateToChat: (scenarioTitle: String, scenarioKey: String, initialPrompt: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTopic by viewModel.topic.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    
    val tempoCycleActive by viewModel.tempoCycleActive.collectAsStateWithLifecycle()
    val currentRepetition by viewModel.currentRepetition.collectAsStateWithLifecycle()
    val currentTempo by viewModel.currentTempo.collectAsStateWithLifecycle()
    val speechProgressRange by viewModel.speechProgressRange.collectAsStateWithLifecycle()

    val shadowingMode by viewModel.shadowingMode.collectAsStateWithLifecycle()
    
    // Internal State
    var activeStage by remember { mutableStateOf(1) } // 1 to 6
    var topicInputText by remember { mutableStateOf(activeTopic) }
    var showTranslation by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTempoCycle(speechHelper)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is ShadowingUiState.Success) {
            activeStage = 1
            showTranslation = false
        }
    }

    // Design Tokens (Immersive Dark Mode)
    val darkCanvas = Brush.verticalGradient(
        colors = listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF020617))
    )
    val accentCyan = Color(0xFF38BDF8)
    val accentIndigo = Color(0xFF818CF8)
    val accentEmerald = Color(0xFF10B981)
    val accentAmber = Color(0xFFF59E0B)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkCanvas)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "UNIVERSAL TRAINING ENGINE",
                        color = accentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = targetLanguage.uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stage Navigation (Only when we have a segment)
            if (uiState is ShadowingUiState.Success) {
                ShadowingStageIndicator(activeStage = activeStage, onStageSelected = { activeStage = it }, accentCyan = accentCyan)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is ShadowingUiState.Loading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accentCyan)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Generowanie materiału treningowego...", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    is ShadowingUiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(state.message, color = Color.Red, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadNewSegment() }, colors = ButtonDefaults.buttonColors(containerColor = accentCyan)) {
                                Text("Spróbuj Ponownie", color = Color.Black)
                            }
                        }
                    }
                    is ShadowingUiState.Idle -> {
                        // Setup Screen
                        ShadowingSetupView(
                            topicInputText = topicInputText,
                            onTopicChange = { topicInputText = it },
                            shadowingMode = shadowingMode,
                            onModeChange = { viewModel.setShadowingMode(it) },
                            onStart = {
                                if (topicInputText.isNotBlank()) viewModel.setTopic(topicInputText)
                                viewModel.loadNewSegment()
                            },
                            accentCyan = accentCyan,
                            accentAmber = accentAmber
                        )
                    }
                    is ShadowingUiState.Success -> {
                        val segment = state.segment
                        // Display the active stage
                        AnimatedContent(
                            targetState = activeStage,
                            transitionSpec = {
                                slideInHorizontally(initialOffsetX = { fullWidth -> if (targetState > initialState) fullWidth else -fullWidth }) + fadeIn() togetherWith
                                        slideOutHorizontally(targetOffsetX = { fullWidth -> if (targetState > initialState) -fullWidth else fullWidth }) + fadeOut()
                            },
                            label = "StageTransition"
                        ) { stage ->
                            when (stage) {
                                1 -> Stage1Listen(segment, speechHelper, speechProgressRange, targetLanguage, showTranslation, onToggleTranslation = { showTranslation = !showTranslation }, accentCyan)
                                2 -> Stage2Shadow(segment, speechHelper, speechProgressRange, targetLanguage, accentIndigo)
                                3 -> Stage3Automate(segment, speechHelper, viewModel, tempoCycleActive, currentRepetition, currentTempo, speechProgressRange, targetLanguage, accentAmber)
                                4 -> Stage4Retrieve(segment, speechHelper, targetLanguage, accentEmerald)
                                5 -> Stage5Transform(segment, speechHelper, targetLanguage, accentCyan)
                                6 -> Stage6Communicate(segment, onNavigateToChat, accentIndigo)
                            }
                        }
                    }
                }
            }

            // Bottom Navigation (Next/Prev Stage)
            if (uiState is ShadowingUiState.Success) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (activeStage > 1) {
                        TextButton(onClick = { activeStage-- }) {
                            Text("← Poprzedni", color = Color.White.copy(alpha = 0.7f))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (activeStage < 6) {
                        Button(
                            onClick = { activeStage++ },
                            colors = ButtonDefaults.buttonColors(containerColor = accentCyan)
                        ) {
                            Text("Kolejny Etap →", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { 
                                activeStage = 1
                                viewModel.loadNewSegment() 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentEmerald)
                        ) {
                            Text("Nowy Trening", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// STAGE 1: LISTEN
@Composable
fun Stage1Listen(
    segment: ShadowingSegmentResponse, 
    speechHelper: TutorSpeechHelper, 
    speechProgressRange: Pair<Int, Int>?, 
    targetLanguage: String,
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "1. Słuchaj",
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = "Zrozumiały kontekst",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        HighlightedTextDisplay(segment.foreignText, speechProgressRange, accentColor, blur = false)

        Spacer(modifier = Modifier.height(24.dp))
        
        AnimatedVisibility(visible = showTranslation) {
            Text(
                text = segment.nativeTranslation,
                color = accentColor.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { speechHelper.speakWithRate(segment.foreignText, 1.0f, targetLanguage) { _, _ -> } },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Odtwórz (1.0x)", color = Color.Black)
            }
            OutlinedButton(
                onClick = onToggleTranslation,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text(if (showTranslation) "Ukryj Tłumaczenie" else "Pokaż Tłumaczenie", color = Color.White)
            }
        }
    }
}

// STAGE 2: SHADOW
@Composable
fun Stage2Shadow(
    segment: ShadowingSegmentResponse, 
    speechHelper: TutorSpeechHelper, 
    speechProgressRange: Pair<Int, Int>?, 
    targetLanguage: String,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "2. Naśladuj",
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = "Rytm, artykulacja, intonacja",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        HighlightedTextDisplay(segment.foreignText, speechProgressRange, accentColor, blur = false)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Melic Waveform Visualizer
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(60.dp)
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            val width = size.width
            val height = size.height
            val path = Path()
            path.moveTo(0f, height * 0.5f)
            val points = 8
            for (i in 1..points) {
                val x = (width / points) * i
                val y = if (i % 2 == 0) height * 0.2f else height * 0.8f
                path.quadraticTo((x - width / points / 2), if (i % 2 == 0) height * 0.8f else height * 0.2f, x, y)
            }
            drawPath(path = path, color = accentColor, style = Stroke(width = 3.dp.toPx()))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { speechHelper.speakWithRate(segment.foreignText, 0.8f, targetLanguage) { _, _ -> } },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Slow (0.8x)", color = Color.Black)
            }
            Button(
                onClick = { speechHelper.speakWithRate(segment.foreignText, 1.0f, targetLanguage) { _, _ -> } },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Normal (1.0x)", color = Color.Black)
            }
        }
    }
}

// STAGE 3: AUTOMATE
@Composable
fun Stage3Automate(
    segment: ShadowingSegmentResponse, 
    speechHelper: TutorSpeechHelper, 
    viewModel: ShadowingViewModel,
    tempoCycleActive: Boolean,
    currentRepetition: Int,
    currentTempo: Float,
    speechProgressRange: Pair<Int, Int>?, 
    targetLanguage: String,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "3. Automatyzuj",
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = "Powtórzenia i adaptacyjne tempo",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        HighlightedTextDisplay(segment.foreignText, speechProgressRange, accentColor, blur = false)

        Spacer(modifier = Modifier.height(32.dp))

        if (tempoCycleActive) {
            Text("Cykl aktywny: Powtórzenie $currentRepetition/4", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Obecne Tempo: ${currentTempo}x", color = accentColor)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.stopTempoCycle(speechHelper) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
            ) {
                Text("Zatrzymaj Cykl", color = Color.White)
            }
        } else {
            Text("System odtworzy zdanie ze wzrastającą prędkością (0.75x → 1.0x → 1.2x).", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.startTempoCycle(segment.foreignText, speechHelper) },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Uruchom Adaptacyjne Powtórzenia", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// STAGE 4: RETRIEVE
@Composable
fun Stage4Retrieve(
    segment: ShadowingSegmentResponse, 
    speechHelper: TutorSpeechHelper, 
    targetLanguage: String,
    accentColor: Color
) {
    var isBlurred by remember { mutableStateOf(true) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "4. Odtwórz z pamięci",
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = "Tarcza pamięci aktywna (Retrieval practice)",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Shielded Text
        Box(
            modifier = Modifier
                .clickable { isBlurred = !isBlurred }
                .padding(16.dp)
        ) {
            Text(
                text = segment.foreignText,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = if (isBlurred) Modifier.blur(12.dp) else Modifier
            )
            if (isBlurred) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                )
            }
        }
        Text("(Kliknij tekst, aby odsłonić tarczę)", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)

        Spacer(modifier = Modifier.height(32.dp))

        val questions = segment.retrievedQuestions
        if (questions.isNullOrEmpty()) {
            Text("Brak pytań sprawdzających dla tego segmentu.", color = Color.White.copy(alpha = 0.6f))
        } else {
            val q = questions[currentQuestionIndex]
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(q.question, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (showAnswer) {
                        Text(q.answer, color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    } else {
                        Button(
                            onClick = { showAnswer = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f))
                        ) {
                            Text("Pokaż Odpowiedź", color = accentColor)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = { 
                                if (currentQuestionIndex > 0) {
                                    currentQuestionIndex--
                                    showAnswer = false
                                }
                            },
                            enabled = currentQuestionIndex > 0
                        ) { Text("Poprzednie", color = Color.White) }
                        
                        Text("${currentQuestionIndex + 1} / ${questions.size}", color = Color.White.copy(alpha = 0.5f))
                        
                        TextButton(
                            onClick = { 
                                if (currentQuestionIndex < questions.size - 1) {
                                    currentQuestionIndex++
                                    showAnswer = false
                                }
                            },
                            enabled = currentQuestionIndex < questions.size - 1
                        ) { Text("Następne", color = Color.White) }
                    }
                }
            }
        }
    }
}

// STAGE 5: TRANSFORM
@Composable
fun Stage5Transform(
    segment: ShadowingSegmentResponse, 
    speechHelper: TutorSpeechHelper, 
    targetLanguage: String,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "5. Przekształć",
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = "Zmień osobę, czas, szczegół",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Oryginał: ${segment.foreignText}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        val transformations = segment.transformations
        if (transformations.isNullOrEmpty()) {
            Text("Brak przekształceń dla tego segmentu.", color = Color.White.copy(alpha = 0.6f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(0.95f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(transformations) { tf ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = accentColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                    Text(tf.type.uppercase(), color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tf.instruction, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(tf.transformedText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(tf.translation, color = accentColor.copy(alpha = 0.8f), fontSize = 12.sp)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { speechHelper.speak(tf.transformedText, targetLanguage) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Posłuchaj", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// STAGE 6: COMMUNICATE
@Composable
fun Stage6Communicate(
    segment: ShadowingSegmentResponse, 
    onNavigateToChat: (scenarioTitle: String, scenarioKey: String, initialPrompt: String) -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "6. Użyj",
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = "Aktywna komunikacja na żywo",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        Icon(Icons.Default.Mic, contentDescription = null, tint = accentColor, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = segment.communicationScenario ?: "Scenariusz konwersacyjny oparty na przećwiczonym materiale.",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                val title = "Rozmowa z Shadowing"
                val key = "shadowing_chat_${System.currentTimeMillis()}"
                val prompt = segment.communicationScenario ?: "Porozmawiajmy o tym: ${segment.foreignText}"
                onNavigateToChat(title, key, prompt)
            },
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Text("Przejdź do Hands-Free Live Chat", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ShadowingSetupView(
    topicInputText: String,
    onTopicChange: (String) -> Unit,
    shadowingMode: ShadowingMode,
    onModeChange: (ShadowingMode) -> Unit,
    onStart: () -> Unit,
    accentCyan: Color,
    accentAmber: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accentCyan, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Wprowadź materiał lub temat, aby wygenerować ścieżkę treningową.", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = topicInputText,
            onValueChange = onTopicChange,
            label = { Text("O czym chcesz się uczyć?", color = Color.White.copy(alpha = 0.6f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentCyan,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Mode selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = shadowingMode == ShadowingMode.STANDARD,
                onClick = { onModeChange(ShadowingMode.STANDARD) },
                label = { Text("Standard", color = Color.White) }
            )
            FilterChip(
                selected = shadowingMode == ShadowingMode.SONGS,
                onClick = { onModeChange(ShadowingMode.SONGS) },
                label = { Text("Piosenki", color = Color.White) }
            )
            FilterChip(
                selected = shadowingMode == ShadowingMode.PODCAST,
                onClick = { onModeChange(ShadowingMode.PODCAST) },
                label = { Text("TED/Podcast", color = Color.White) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = accentCyan),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Rozpocznij Trening", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ShadowingStageIndicator(activeStage: Int, onStageSelected: (Int) -> Unit, accentCyan: Color) {
    val stages = listOf("Słuchaj", "Naśladuj", "Automatyzuj", "Odtwórz", "Przekształć", "Użyj")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, label ->
            val stageNum = index + 1
            val isActive = activeStage == stageNum
            val isPassed = activeStage > stageNum
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { if (isPassed || isActive) onStageSelected(stageNum) }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (isActive) accentCyan else if (isPassed) accentCyan.copy(alpha = 0.3f) else Color(0xFF0F172A),
                            shape = CircleShape
                        )
                        .border(1.dp, if (isActive || isPassed) accentCyan else Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stageNum.toString(),
                        color = if (isActive) Color.Black else Color.White.copy(alpha = if (isPassed) 1f else 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = if (isActive) accentCyan else Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
            if (index < stages.size - 1) {
                Box(modifier = Modifier.weight(1f).height(2.dp).background(if (isPassed) accentCyan.copy(alpha=0.5f) else Color.White.copy(alpha=0.1f)))
            }
        }
    }
}

@Composable
fun HighlightedTextDisplay(text: String, speechProgressRange: Pair<Int, Int>?, accentColor: Color, blur: Boolean) {
    val annotatedString = buildAnnotatedString {
        if (speechProgressRange != null && speechProgressRange.first in 0..text.length && speechProgressRange.second in 0..text.length && speechProgressRange.first <= speechProgressRange.second) {
            append(text.substring(0, speechProgressRange.first))
            withStyle(style = SpanStyle(color = Color.Black, background = accentColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(speechProgressRange.first, speechProgressRange.second))
            }
            append(text.substring(speechProgressRange.second))
        } else {
            append(text)
        }
    }

    Text(
        text = annotatedString,
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        lineHeight = 32.sp,
        modifier = if (blur) Modifier.blur(12.dp) else Modifier
    )
}
