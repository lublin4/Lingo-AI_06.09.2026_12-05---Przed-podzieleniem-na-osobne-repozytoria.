package com.example.ui.shadowing

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.*
import com.example.data.audio.ImportedAudioSegment
import com.example.data.local.entities.ShadowingEntity
import com.example.ui.speech.TutorSpeechHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowingScreen(
    viewModel: ShadowingViewModel,
    speechHelper: TutorSpeechHelper,
    targetLanguageFromGoal: String = "Spanish",
    onNavigateToChat: (scenarioTitle: String, scenarioKey: String, initialPrompt: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTopic by viewModel.topic.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val shadowingMode by viewModel.shadowingMode.collectAsStateWithLifecycle()
    
    val tempoCycleActive by viewModel.tempoCycleActive.collectAsStateWithLifecycle()
    val currentRepetition by viewModel.currentRepetition.collectAsStateWithLifecycle()
    val currentTempo by viewModel.currentTempo.collectAsStateWithLifecycle()
    val speechProgressRange by viewModel.speechProgressRange.collectAsStateWithLifecycle()

    val isImportMode by viewModel.isImportMode.collectAsStateWithLifecycle()
    val importedSegments by viewModel.importedSegments.collectAsStateWithLifecycle()
    val currentImportedIndex by viewModel.currentImportedSegmentIndex.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val isGeneratingGrammar by viewModel.isGeneratingGrammar.collectAsStateWithLifecycle()
    val isGeneratingAccelerator by viewModel.isGeneratingAccelerator.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    // Internal Navigation State
    var activeStage by remember { mutableIntStateOf(1) } // 1 to 4
    var showSetupBottomSheet by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(true) }

    LaunchedEffect(targetLanguageFromGoal) {
        if (targetLanguageFromGoal.isNotBlank() && !targetLanguageFromGoal.equals(targetLanguage, ignoreCase = true)) {
            viewModel.setLanguage(targetLanguageFromGoal)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTempoCycle(speechHelper)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is ShadowingUiState.Success) {
            activeStage = 1
            showTranslation = true
            showSetupBottomSheet = false
        }
    }

    // Modern Deep-Work Dark Canvas Palette
    val darkCanvas = Brush.verticalGradient(
        colors = listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF020617))
    )
    val accentCyan = Color(0xFF38BDF8)
    val accentIndigo = Color(0xFF818CF8)
    val accentEmerald = Color(0xFF10B981)
    val accentAmber = Color(0xFFF59E0B)
    val accentRose = Color(0xFFF43F5E)

    // Audio Import Picker
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importAudioFile(it) }
    }

    // Material Setup Bottom Sheet
    if (showSetupBottomSheet) {
        ShadowingMaterialSetupSheet(
            currentTopic = activeTopic,
            currentLanguage = targetLanguage,
            currentMode = shadowingMode,
            lyricsInput = viewModel.lyricsInput.collectAsStateWithLifecycle().value,
            podcastInput = viewModel.podcastInput.collectAsStateWithLifecycle().value,
            onLanguageSelected = { viewModel.setLanguage(it) },
            onModeSelected = { viewModel.setShadowingMode(it) },
            onTopicSubmitted = { topic ->
                viewModel.setTopic(topic)
                viewModel.loadNewSegment()
            },
            onLyricsSubmitted = { text ->
                viewModel.setLyricsInput(text)
                viewModel.loadNewSegment()
            },
            onPodcastSubmitted = { text ->
                viewModel.setPodcastInput(text)
                viewModel.loadNewSegment()
            },
            onPickAudio = { audioPicker.launch("audio/*") },
            onDismiss = { showSetupBottomSheet = false },
            accentCyan = accentCyan
        )
    }

    // History Dialog
    if (showHistoryDialog) {
        ShadowingHistoryDialog(
            history = history,
            onSelectSession = { entity ->
                showHistoryDialog = false
                viewModel.setLanguage(entity.targetLanguage)
                viewModel.setTopic(entity.metadata ?: entity.foreignPhrase)
            },
            onDeleteSession = { id -> viewModel.deleteShadowingSession(id) },
            onDismiss = { showHistoryDialog = false },
            accentCyan = accentCyan
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkCanvas)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            ShadowingHeaderBar(
                targetLanguage = targetLanguage,
                shadowingMode = shadowingMode,
                topicTitle = activeTopic,
                onOpenSetup = { showSetupBottomSheet = true },
                onOpenHistory = { showHistoryDialog = true },
                onExitImport = if (isImportMode) { { viewModel.exitImportMode() } } else null,
                onResetToIdle = { viewModel.resetToIdle() },
                accentCyan = accentCyan
            )

            Spacer(modifier = Modifier.height(16.dp))

            

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is ShadowingUiState.Idle -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            var inputTopic by remember { mutableStateOf("") }
                            val quickTopics = listOf("Wywiad w pracy", "Podróże i hotel", "Kawiarnia", "Spotkanie biznesowe", "Rozmowa ze znajomym")
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = accentCyan.copy(alpha = 0.08f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = accentCyan,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Generator Shadowing",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentCyan
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Wpisz temat, o którym chcesz porozmawiać. AI wygeneruje dedykowany materiał do treningu.",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            
                            OutlinedTextField(
                                value = inputTopic,
                                onValueChange = { inputTopic = it },
                                placeholder = { Text("Wpisz temat (np. Rozmowa w restauracji)", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Button(
                                onClick = {
                                    if (inputTopic.isNotBlank()) {
                                        viewModel.setTopic(inputTopic)
                                        viewModel.loadNewSegment()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Wygeneruj Trening Shadowing", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Szybkie tematy:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(quickTopics.size) { index ->
                                    val topic = quickTopics[index]
                                    SuggestionChip(
                                        onClick = {
                                            viewModel.setTopic(topic)
                                            viewModel.loadNewSegment()
                                        },
                                        label = { Text(topic, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f)) },
                                        border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.3f)),
                                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF1E293B))
                                    )
                                }
                            }
                        }
                    }
                    is ShadowingUiState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = accentCyan, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = importProgress ?: "Generowanie adaptacyjnego segmentu...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        }
                    }
                    is ShadowingUiState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = accentRose,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.loadNewSegment() },
                                colors = ButtonDefaults.buttonColors(containerColor = accentCyan)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Spróbuj Ponownie", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    is ShadowingUiState.Success -> {
                        val segment = state.segment
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            
                            // Top Prompt Banner
                            if (!segment.communicationScenario.isNullOrBlank()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = accentCyan.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Kontekst treningu", color = accentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(segment.communicationScenario, color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }

                            ShadowingStageIndicator(
                                activeStage = activeStage,
                                onStageSelected = { activeStage = it },
                                accentCyan = accentCyan
                            )
                            
                            when (activeStage) {
                                1 -> Stage1ListenView(
                                    segment = segment,
                                    speechHelper = speechHelper,
                                    speechProgressRange = speechProgressRange,
                                    targetLanguage = targetLanguage,
                                    showTranslation = showTranslation,
                                    onToggleTranslation = { showTranslation = !showTranslation },
                                    onSaveKeyPhrase = { phrase, trans ->
                                        viewModel.saveKeyPhraseToVocabulary(phrase, trans)
                                    },
                                    accentColor = accentCyan
                                )
                                2 -> Stage2GrammarView(
                                    segment = segment,
                                    speechHelper = speechHelper,
                                    targetLanguage = targetLanguage,
                                    accentColor = accentIndigo,
                                    onSaveKeyPhrase = { phrase, trans ->
                                        viewModel.saveKeyPhraseToVocabulary(phrase, trans)
                                    },
                                    onGenerateGrammar = {
                                        viewModel.generateGrammarExplanationForCurrentSegment()
                                    },
                                    isGeneratingGrammar = isGeneratingGrammar
                                )
                                3 -> Stage5TransformView(
                                    segment = segment,
                                    speechHelper = speechHelper,
                                    targetLanguage = targetLanguage,
                                    onSavePhrase = { phrase, trans ->
                                        viewModel.saveKeyPhraseToVocabulary(phrase, trans)
                                    },
                                    onGenerateAccelerator = {
                                        viewModel.generateAcceleratorForCurrentSegment()
                                    },
                                    isGeneratingAccelerator = isGeneratingAccelerator,
                                    accentColor = accentAmber
                                )
                                4 -> Stage6CommunicateView(
                                    segment = segment,
                                    speechHelper = speechHelper,
                                    targetLanguage = targetLanguage,
                                    onNavigateToChat = onNavigateToChat,
                                    onNewSegment = { viewModel.resetToIdle() },
                                    accentColor = accentEmerald
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }        }
    }


// INTEGRATED TOP PROMPT & GENERATOR BAR
}

@Composable
fun ShadowingHeaderBar(
    targetLanguage: String,
    shadowingMode: ShadowingMode,
    topicTitle: String,
    onOpenSetup: () -> Unit,
    onOpenHistory: () -> Unit,
    onExitImport: (() -> Unit)?,
    onResetToIdle: () -> Unit,
    accentCyan: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SHADOWING ENGINE",
                        color = accentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = accentCyan.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = shadowingMode.name,
                            color = accentCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${getLanguageDisplayName(targetLanguage)} • $topicTitle",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onResetToIdle() }.padding(4.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Zmień temat", tint = accentCyan, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Zmień temat", color = accentCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onExitImport != null) {
                    IconButton(onClick = onExitImport) {
                        Icon(Icons.Default.Close, contentDescription = "Zamknij import", tint = Color.Red)
                    }
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = "Historia", tint = Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = onOpenSetup) {
                    Icon(Icons.Default.Tune, contentDescription = "Zmień temat / Nowy trening", tint = accentCyan)
                }
            }
        }
    }


// 6 STAGES PIPELINE INDICATOR - FULLY CLICKABLE (1 to 6)
}

@Composable
fun ShadowingStageIndicator(activeStage: Int, onStageSelected: (Int) -> Unit, accentCyan: Color) {
    val stages = listOf(
        "1. Percepcja",
        "2. Gramatyka",
        "3. AI Accelerator",
        "4. Użyj"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, label ->
            val stageNum = index + 1
            val isActive = activeStage == stageNum

            Surface(
                color = if (isActive) accentCyan else Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isActive) accentCyan else Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clickable { onStageSelected(stageNum) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isActive) Color.Black else Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }


// STAGE 1: LISTEN & COMPREHENSIBLE INPUT
}

@Composable
fun Stage1ListenView(
    segment: ShadowingSegmentResponse,
    speechHelper: TutorSpeechHelper,
    speechProgressRange: Pair<Int, Int>?,
    targetLanguage: String,
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    onSaveKeyPhrase: (String, String) -> Unit,
    accentColor: Color
) {
    var activePlaybackSpeed by remember { mutableStateOf<Float?>(null) }

    // Synchronize active speed with TTS speaking state
    LaunchedEffect(speechHelper.isSpeaking) {
        if (!speechHelper.isSpeaking) {
            activePlaybackSpeed = null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        StageHeader(
            stageNum = 1,
            title = "1. Percepcja (Rozumienie materiału)",
            subtitle = "Słuchaj w 4 precyzyjnych prędkościach mowy i analizuj wzorce zdań.",
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Main Foreign Text Display Card - Compact Mobile Friendly
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HighlightedTextDisplay(
                    text = segment.foreignText,
                    speechProgressRange = speechProgressRange,
                    accentColor = accentColor,
                    blur = false,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Polish Translation Section
                AnimatedVisibility(visible = showTranslation) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tłumaczenie na język polski:",
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = segment.nativeTranslation,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4 Playback Speeds Section (0.65x, 0.75x, 0.85x, 1.0x)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Prędkość odtwarzania (Playback Speed):",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(
                            onClick = onToggleTranslation,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (showTranslation) accentColor else Color.White.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (showTranslation) accentColor.copy(alpha = 0.15f) else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = if (showTranslation) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (showTranslation) accentColor else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showTranslation) "Ukryj PL" else "Pokaż PL",
                                color = if (showTranslation) accentColor else Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
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
                            val isThisSpeedActive = speechHelper.isSpeaking && activePlaybackSpeed == speedValue
                            Button(
                                onClick = {
                                    if (isThisSpeedActive) {
                                        speechHelper.stop()
                                        activePlaybackSpeed = null
                                    } else {
                                        speechHelper.stop()
                                        activePlaybackSpeed = speedValue
                                        speechHelper.speakWithRate(segment.foreignText, speedValue, targetLanguage) { _, _ -> }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isThisSpeedActive) accentColor else Color(0xFF1E293B),
                                    contentColor = if (isThisSpeedActive) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isThisSpeedActive) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isThisSpeedActive) Color.Black else accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = speedLabel,
                                    color = if (isThisSpeedActive) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Optional Poetic Context Card for Songs/Lyrics
        if (!segment.poeticContext.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kontekst Poetycki & Idiomy", color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(segment.poeticContext, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }

        // Key Vocabulary Phrases & Full Sentences (Language Islands - Master Level / AI Accelerator)
        if (!segment.keyPhrases.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "AI ACCELERATOR: WYSPY JĘZYKOWE (MASTER)",
                            color = Color(0xFFFFD700),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "🏝️ Zapis do Wysp",
                            color = Color(0xFF81C784),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Pełne zdania i wzorce komunikacyjne gotowe do użycia w rozmowach. Zapisz bezpośrednio do Repozytorium Wysp:",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                segment.keyPhrases.forEach { keyPhrase ->
                    var isSaved by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isSaved) Color(0xFF4CAF50) else Color(0xFFFFD700).copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = Color(0xFFFFD700).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "🏝️ WYSPA MASTER",
                                                color = Color(0xFFFFD700),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = keyPhrase.phrase,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = keyPhrase.translation,
                                        color = accentColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            speechHelper.speakWithRate(keyPhrase.phrase, 0.85f, targetLanguage) { _, _ -> }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Odsłuchaj",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onSaveKeyPhrase(keyPhrase.phrase, keyPhrase.translation)
                                            isSaved = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSaved) Icons.Default.CheckCircle else Icons.Default.BookmarkAdd,
                                            contentDescription = "Zapisz do Wysp Słownictwa",
                                            tint = if (isSaved) Color(0xFF4CAF50) else Color(0xFFFFD700),
                                            modifier = Modifier.size(18.dp)
                                        )
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

// STAGE 2: GRAMATYKA & MOWA NATURALNA (STORYLEARNING MASTERS)

@Composable
fun Stage2GrammarView(
    segment: ShadowingSegmentResponse,
    speechHelper: TutorSpeechHelper,
    targetLanguage: String,
    accentColor: Color,
    onSaveKeyPhrase: (String, String) -> Unit = { _, _ -> },
    onGenerateGrammar: () -> Unit = {},
    isGeneratingGrammar: Boolean = false
) {
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var showQuestionAnswer by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        StageHeader(
            stageNum = 2,
            title = "2. Gramatyka & Składnia (StoryLearning® Masters)",
            subtitle = "Mistrzowskie wyłożenie reguł mowy naturalnej, pragmatyki, schematów zdań oraz ewolucji składniowej.",
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Compact Context Ribbon (Brak powielania wielkiej karty i listy słówek z Kroku 1)
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kontekst analizy: \"${segment.foreignText}\"",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { speechHelper.speak(segment.foreignText, targetLanguage) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Odtwórz", tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION 1: Deep Master Grammar Explanation (Głębokie Objaśnienie StoryLearning)
        val explanation = segment.grammarExplanation
        if (!explanation.isNullOrBlank()) {
            MasterGrammarExplanationCard(
                explanation = explanation,
                speechHelper = speechHelper,
                targetLanguage = targetLanguage,
                accentColor = accentColor
            )
        } else {
            // Generate Grammar Card if explanation is missing
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Wygeneruj Wyjaśnienie Masters",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Otrzymaj od Nauczyciela AI pełne wyłożenie gramatyki, pragmatykę mowy naturalnej, schemat zdania oraz przykłady użycia.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (isGeneratingGrammar) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Nauczyciel AI analizuje składnię...",
                            color = accentColor,
                            fontSize = 11.5.sp
                        )
                    } else {
                        Button(
                            onClick = onGenerateGrammar,
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Odblokuj Wyjaśnienie Masters 🚀", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 4: Retrieval Practice (Checking Questions)
        val questions = segment.retrievedQuestions
        if (!questions.isNullOrEmpty()) {
            val q = questions[currentQuestionIndex.coerceIn(0, questions.size - 1)]
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Pytanie Sprawdzające Rozumienie (${currentQuestionIndex + 1}/${questions.size})",
                            color = Color(0xFF818CF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = q.question,
                            color = Color.White,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { speechHelper.speak(q.question, targetLanguage) }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF818CF8))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (showQuestionAnswer) {
                        Surface(
                            color = Color(0xFF818CF8).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF818CF8))
                        ) {
                            Text(
                                text = "Wzorcowa odpowiedź: " + q.answer,
                                color = Color(0xFF818CF8),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showQuestionAnswer = true },
                            border = BorderStroke(1.dp, Color(0xFF818CF8)),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pokaż Odpowiedź", color = Color(0xFF818CF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                if (currentQuestionIndex > 0) {
                                    currentQuestionIndex--
                                    showQuestionAnswer = false
                                }
                            },
                            enabled = currentQuestionIndex > 0
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = if (currentQuestionIndex > 0) Color.White else Color.Gray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Poprzednie", color = if (currentQuestionIndex > 0) Color.White else Color.Gray, fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = {
                                if (currentQuestionIndex < questions.size - 1) {
                                    currentQuestionIndex++
                                    showQuestionAnswer = false
                                }
                            },
                            enabled = currentQuestionIndex < questions.size - 1
                        ) {
                            Text("Następne", color = if (currentQuestionIndex < questions.size - 1) Color.White else Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = if (currentQuestionIndex < questions.size - 1) Color.White else Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MasterGrammarExplanationCard(
    explanation: String,
    speechHelper: TutorSpeechHelper,
    targetLanguage: String,
    accentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Wyłożenie Gramatyki & Mowa Naturalna (Master Class)",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Split explanation into section blocks by '###'
            val sections = explanation.split("###").map { it.trim() }.filter { it.isNotEmpty() }

            sections.forEach { sectionText ->
                val lines = sectionText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val headerTitle = lines.firstOrNull() ?: ""
                val bodyLines = if (lines.size > 1) lines.subList(1, lines.size) else emptyList()

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header with Icon
                        if (headerTitle.isNotBlank()) {
                            val headerIcon = when {
                                headerTitle.contains("Czas") || headerTitle.contains("🎓") -> Icons.Default.School
                                headerTitle.contains("Schemat") || headerTitle.contains("📐") || headerTitle.contains("Wzorzec") -> Icons.Default.Code
                                headerTitle.contains("Słownictwo") || headerTitle.contains("🔍") || headerTitle.contains("Analiza") -> Icons.Default.Search
                                headerTitle.contains("Wskazówka") || headerTitle.contains("💡") || headerTitle.contains("Mastery") -> Icons.Default.Lightbulb
                                headerTitle.contains("Przykłady") || headerTitle.contains("⚡") -> Icons.Default.FlashOn
                                else -> Icons.Default.AutoAwesome
                            }

                            val headerColor = when {
                                headerTitle.contains("Wskazówka") || headerTitle.contains("💡") -> Color(0xFFF59E0B)
                                headerTitle.contains("Przykłady") || headerTitle.contains("⚡") -> Color(0xFF10B981)
                                headerTitle.contains("Schemat") || headerTitle.contains("📐") -> Color(0xFF818CF8)
                                else -> Color(0xFF38BDF8)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(headerIcon, contentDescription = null, tint = headerColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = headerTitle.replace("#", "").trim(),
                                    color = headerColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Body rendering
                        bodyLines.forEach { line ->
                            when {
                                line.startsWith("`") && line.endsWith("`") -> {
                                    // Formula box
                                    Surface(
                                        color = Color(0xFF0F172A),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = line.removeSurrounding("`"),
                                            color = Color(0xFF818CF8),
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                line.startsWith("-") || line.startsWith("*") -> {
                                    // List item
                                    val itemText = line.substring(1).trim()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text("• ", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = parseMarkdownFormatting(itemText),
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 12.5.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                        val extractForeign = Regex("\"([^\"]+)\"").find(itemText)?.groupValues?.get(1)
                                            ?: Regex("\\*\\*([^*]+)\\*\\*").find(itemText)?.groupValues?.get(1)
                                        if (!extractForeign.isNullOrBlank() && extractForeign.length in 3..60) {
                                            IconButton(
                                                onClick = { speechHelper.speak(extractForeign, targetLanguage) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.VolumeUp,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    Text(
                                        text = parseMarkdownFormatting(line),
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.5.sp,
                                        modifier = Modifier.padding(vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseMarkdownFormatting(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8)))
            builder.append(part)
            builder.pop()
        } else {
            builder.append(part)
        }
    }
    return builder.toAnnotatedString()
}

// STAGE 3: AUTOMATE - ADAPTIVE TEMPO ENGINE

@Composable
fun Stage3AutomateView(
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
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        StageHeader(
            stageNum = 3,
            title = "3. Automatyzuj (Adaptive Tempo Engine)",
            subtitle = "Płynne powtórzenia ze wzrastającym tempem (0.65x → 0.75x → 0.85x → 1.0x) i pauzą na oddech.",
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HighlightedTextDisplay(
                    text = segment.foreignText,
                    speechProgressRange = speechProgressRange,
                    accentColor = accentColor,
                    blur = false,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (tempoCycleActive) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "CYKL ADAPTACYJNY AKTYWNY",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Powtórzenie $currentRepetition z 4",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Tempo: ${currentTempo}x",
                            color = accentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.stopTempoCycle(speechHelper) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Zatrzymaj Cykl", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = "Uruchom automatyczny cykl powtórzeń. Aplikacja odtworzy zdanie 4 razy z rosnącą prędkością, dając czas na oddech pomiędzy seriami.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.startTempoCycle(segment.foreignText, speechHelper) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uruchom Cykl Adaptacyjny", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }


// STAGE 4: RETRIEVE - ACTIVE RECALL & MEMORY SHIELD
}

@Composable
fun Stage4RetrieveView(
    segment: ShadowingSegmentResponse,
    speechHelper: TutorSpeechHelper,
    targetLanguage: String,
    accentColor: Color
) {
    var maskMode by remember { mutableIntStateOf(1) } // 1: Hidden, 2: FirstLetters, 3: Revealed
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var showQuestionAnswer by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            ,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        StageHeader(
            stageNum = 4,
            title = "4. Aktywne odtworzenie z pamięci",
            subtitle = "Słuchasz bez patrzenia na tekst. Odtwórz pełne zdanie w głowie lub na głos zanim je odkryjesz.",
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Audio play button for memory recall
                Button(
                    onClick = { speechHelper.speakWithRate(segment.foreignText, 1.0f, targetLanguage) { _, _ -> } },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Odsłuchaj ze słuchu (1.0x)", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Masked Text Display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val textToDisplay = when (maskMode) {
                        1 -> "••••••••••••••••••••••••••••••••••••••••"
                        2 -> generateFirstLetters(segment.foreignText)
                        else -> segment.foreignText
                    }

                    Text(
                        text = textToDisplay,
                        color = if (maskMode == 3) Color.White else accentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = if (maskMode == 1) Modifier.blur(6.dp) else Modifier
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mask Toggle Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = maskMode == 1,
                        onClick = { maskMode = 1 },
                        label = { Text("Ukryty", color = Color.White) }
                    )
                    FilterChip(
                        selected = maskMode == 2,
                        onClick = { maskMode = 2 },
                        label = { Text("1-sze litery", color = Color.White) }
                    )
                    FilterChip(
                        selected = maskMode == 3,
                        onClick = { maskMode = 3 },
                        label = { Text("Odsłoń tekst", color = Color.White) }
                    )
                }
            }
        }

        // Active Recall Questions
        val questions = segment.retrievedQuestions
        if (!questions.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            val q = questions[currentQuestionIndex]
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pytanie Sprawdzające (${currentQuestionIndex + 1}/${questions.size})", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(q.question, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    if (showQuestionAnswer) {
                        Text(q.answer, color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    } else {
                        OutlinedButton(
                            onClick = { showQuestionAnswer = true },
                            border = BorderStroke(1.dp, accentColor),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Pokaż Wzorcową Odpowiedź", color = accentColor, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                if (currentQuestionIndex > 0) {
                                    currentQuestionIndex--
                                    showQuestionAnswer = false
                                }
                            },
                            enabled = currentQuestionIndex > 0
                        ) { Text("Poprzednie", color = Color.White, fontSize = 12.sp) }

                        TextButton(
                            onClick = {
                                if (currentQuestionIndex < questions.size - 1) {
                                    currentQuestionIndex++
                                    showQuestionAnswer = false
                                }
                            },
                            enabled = currentQuestionIndex < questions.size - 1
                        ) { Text("Następne", color = Color.White, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

// STAGE 5: TRANSFORM - CONSTRUCTION GRAMMAR

@Composable
fun Stage5TransformView(
    segment: ShadowingSegmentResponse,
    speechHelper: TutorSpeechHelper,
    targetLanguage: String,
    onSavePhrase: (String, String) -> Unit,
    onGenerateAccelerator: () -> Unit = {},
    isGeneratingAccelerator: Boolean = false,
    accentColor: Color
) {
    var savedPhrasesMap by remember { mutableStateOf<Set<Int>>(emptySet()) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        StageHeader(
            stageNum = 3,
            title = "3. AI Accelerator (Ewolucja Składniowa Opowiadania)",
            subtitle = "Dynamiczna adaptacja struktury zdań. Obserwuj, jak opowiadanie ewoluuje krok po kroku od zdania bazowego do swobodnej komunikacji.",
            accentColor = accentColor
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Dyskretna wstążka kontekstowa całego opowiadania
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kontekst Fabularny Opowiadania",
                        color = accentColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "\"${segment.foreignText}\"",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Nagłówek sekcji z przyciskiem do regeneracji akceleratora
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ewolucja Składniowa (5 Kroków)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onGenerateAccelerator,
                enabled = !isGeneratingAccelerator,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isGeneratingAccelerator) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generowanie...", color = accentColor, fontSize = 11.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Odśwież ⚡", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val transformations = segment.transformations
        if (transformations.isNullOrEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Brak gotowych przekształceń dla tej historii.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onGenerateAccelerator,
                        enabled = !isGeneratingAccelerator,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Wygeneruj Akcelerator AI dla tej historii ⚡", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            transformations.forEachIndexed { index, tf ->
                if (index > 0) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Ewolucja",
                        tint = accentColor.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 3.dp).size(18.dp)
                    )
                }

                val isSaved = savedPhrasesMap.contains(index)
                val badgeBg = when (index) {
                    0 -> Color(0xFF0284C7) // Sky
                    1 -> Color(0xFF0D9488) // Teal
                    2 -> Color(0xFF7C3AED) // Violet
                    3 -> Color(0xFFD97706) // Amber
                    else -> Color(0xFF059669) // Emerald
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (index == transformations.size - 1) accentColor else accentColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = badgeBg.copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = "KROK ${index + 1}: ${tf.type.uppercase()}",
                                        color = badgeBg,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = tf.instruction,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tf.transformedText,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tf.translation,
                            color = accentColor,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { speechHelper.speakWithRate(tf.transformedText, 1.0f, targetLanguage) { _, _ -> } },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Odsłuchaj", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    onSavePhrase(tf.transformedText, tf.translation)
                                    savedPhrasesMap = savedPhrasesMap + index
                                },
                                border = BorderStroke(1.dp, if (isSaved) accentColor else Color.White.copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = if (isSaved) accentColor else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isSaved) "Zapisano! ✓" else "Zapisz do Wysp",
                                    color = if (isSaved) accentColor else Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// STAGE 6: COMMUNICATE - CONOR QUINN LIVE SCENARIO

@Composable
fun Stage6CommunicateView(
    segment: ShadowingSegmentResponse,
    speechHelper: TutorSpeechHelper,
    targetLanguage: String,
    onNavigateToChat: (scenarioTitle: String, scenarioKey: String, initialPrompt: String) -> Unit,
    onNewSegment: () -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        StageHeader(
            stageNum = 4,
            title = "4. Użyj (Conor Quinn Method)",
            subtitle = "Przenieś przećwiczony materiał do aktywnej konwersacji na żywo z użyciem Wysp Językowych (Anchors).",
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Compact Context Ribbon
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kontekst treningu: \"${segment.foreignText}\"",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { speechHelper.speak(segment.foreignText, targetLanguage) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Odtwórz", tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Conor Quinn Roleplay & Anchor Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "METODA QUINNA: WYZWANIE SYTUACYJNE",
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = segment.communicationScenario ?: "Wciel się w rolę rozmówcy i użyj poznanych zdań w naturalnej dyskusji z AI.",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Anchor, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Twoje Kotwice Komunikacyjne (Language Islands):",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Island
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Główna Wyspa:",
                                color = accentColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = segment.foreignText,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = segment.nativeTranslation,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.5.sp
                            )
                        }

                        IconButton(
                            onClick = { speechHelper.speak(segment.foreignText, targetLanguage) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Rozgrzewka TTS", tint = accentColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Additional Key Phrases / Anchors if available
                if (!segment.keyPhrases.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    segment.keyPhrases.take(2).forEach { kp ->
                        Surface(
                            color = Color(0xFF1E293B).copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = kp.phrase, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = kp.translation, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = { speechHelper.speak(kp.phrase, targetLanguage) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Start Live Conversation Button with Quinn Prompt Injection
        Button(
            onClick = {
                val title = "Konwersacja Shadowing: ${segment.foreignText.take(24)}..."
                val key = "shadowing_chat_${System.currentTimeMillis()}"
                
                val prompt = """
                    Działaj jako przyjazny lektor AI i rozmówca w treningu aktywnej komunikacji metodą Conora Quinna (Wyspy Językowe).
                    
                    SCENARIUSZ ODGRYWANIA ROLI:
                    ${segment.communicationScenario ?: "Prowadź swobodną rozmowę na temat: ${segment.foreignText}"}
                    
                    GŁÓWNA WYSPA JĘZYKOWA UŻYTKOWNIKA (KOTWICA):
                    "${segment.foreignText}" (Tłumaczenie: "${segment.nativeTranslation}")
                    
                    INSTRUKCJA DLA LEKTORA AI:
                    1. Rozpocznij konwersację po angielsku ($targetLanguage) wcielając się dokładnie w postać ze scenariusza.
                    2. Twoje pierwsze zdanie musi zadać pytanie lub zarysować sytuację tak, aby użytkownik miał naturalną okazję wypowiedzieć swoją Wyspę Językową ("${segment.foreignText}").
                    3. Bądź cierpliwy, podtrzymuj rozmowę i chwal za użycie kotwicy językowej!
                """.trimIndent()
                
                onNavigateToChat(title, key, prompt)
            },
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Rozpocznij Konwersację Na Żywo z AI 🎙️", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onNewSegment,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Rozpocznij Nowy Trening Shadowing", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StageHeader(stageNum: Int, title: String, subtitle: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }


}

@Composable
fun ImportedSegmentsSelector(
    segments: List<ImportedAudioSegment>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    accentCyan: Color
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(segments) { idx, seg ->
            val isSel = idx == currentIndex
            FilterChip(
                selected = isSel,
                onClick = { onSelectIndex(idx) },
                label = { Text("Segment ${idx + 1}", color = if (isSel) Color.Black else Color.White) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentCyan)
            )
        }
    }


// UNIFIED MATERIAL SETUP SHEET
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowingMaterialSetupSheet(
    currentTopic: String,
    currentLanguage: String,
    currentMode: ShadowingMode,
    lyricsInput: String,
    podcastInput: String,
    onLanguageSelected: (String) -> Unit,
    onModeSelected: (ShadowingMode) -> Unit,
    onTopicSubmitted: (String) -> Unit,
    onLyricsSubmitted: (String) -> Unit,
    onPodcastSubmitted: (String) -> Unit,
    onPickAudio: () -> Unit,
    onDismiss: () -> Unit,
    accentCyan: Color
) {
    var inputText by remember { mutableStateOf(currentTopic.ifBlank { lyricsInput.ifBlank { podcastInput } }) }

    val langDisplay = when (currentLanguage.lowercase()) {
        "spanish" -> "Hiszpański 🇪🇸"
        "english" -> "Angielski 🇬🇧"
        "german" -> "Niemiecki 🇩🇪"
        "french" -> "Francuski 🇫🇷"
        "italian" -> "Włoski 🇮🇹"
        else -> "$currentLanguage 🌐"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Konfigurator Treningu Shadowing",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Auto-synced Language Indicator from Goals
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = accentCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Język docelowy (pobrany z Celów): ",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = langDisplay,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Single Unified Input Field
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Temat lub tekst treningowy", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) },
                placeholder = { Text("Wpisz temat (np. 'Zamawianie kawy') lub wklej fragment piosenki / artykułu...", color = Color.Gray, fontSize = 13.sp) },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentCyan,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Generate Button
            Button(
                onClick = {
                    val promptText = inputText.ifBlank { "Spontaniczna konwersacja językowa" }
                    onTopicSubmitted(promptText)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wygeneruj Trening Shadowing", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Audio File Import
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onPickAudio()
                },
                border = BorderStroke(1.dp, Color(0xFF334155)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.AudioFile, contentDescription = null, tint = accentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importuj Plik Audio MP3 / WAV", color = Color.White, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }


// HISTORY DIALOG
}

@Composable
fun ShadowingHistoryDialog(
    history: List<ShadowingEntity>,
    onSelectSession: (ShadowingEntity) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onDismiss: () -> Unit,
    accentCyan: Color
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("Zapisane Sesje Shadowing", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            if (history.isEmpty()) {
                Text("Brak zapisanych sesji w bazie.", color = Color.White.copy(alpha = 0.6f))
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history) { session ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSession(session) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(session.foreignPhrase, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(session.nativeTranslation, color = accentCyan, fontSize = 12.sp)
                                    Text("${session.targetLanguage} • ${session.learningMode}", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                                }
                                IconButton(onClick = { onDeleteSession(session.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = Color.Red.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = accentCyan)
            }
        }
    )


// UTILS
}

@Composable
fun HighlightedTextDisplay(
    text: String,
    speechProgressRange: Pair<Int, Int>?,
    accentColor: Color,
    blur: Boolean = false,
    fontSize: TextUnit = 14.5.sp,
    lineHeight: TextUnit = 20.sp
) {
    val annotatedString = buildAnnotatedString {
        if (speechProgressRange != null && !blur) {
            val (start, end) = speechProgressRange
            val safeStart = start.coerceIn(0, text.length)
            val safeEnd = end.coerceIn(safeStart, text.length)

            if (safeStart > 0) {
                append(text.substring(0, safeStart))
            }
            if (safeEnd > safeStart) {
                withStyle(
                    style = SpanStyle(
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        background = accentColor.copy(alpha = 0.25f)
                    )
                ) {
                    append(text.substring(safeStart, safeEnd))
                }
            }
            if (safeEnd < text.length) {
                append(text.substring(safeEnd))
            }
        } else {
            append(text)
        }
    }

    Text(
        text = annotatedString,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        lineHeight = lineHeight,
        modifier = if (blur) Modifier.blur(8.dp) else Modifier
    )


}

fun generateFirstLetters(text: String): String {
    val words = text.split(Regex("\\s+"))
    return words.joinToString(" ") { word ->
        if (word.isNotEmpty()) {
            word.first().toString() + ".".repeat((word.length - 1).coerceAtLeast(1))
        } else ""
    }


}

fun getLanguageDisplayName(language: String): String {
    val clean = language.trim().lowercase()
    return when {
        clean.contains("spanish") || clean.contains("hiszpań") || clean.contains("hiszpan") || clean == "es" -> "Hiszpański"
        clean.contains("french") || clean.contains("francusk") || clean == "fr" -> "Francuski"
        clean.contains("german") || clean.contains("niemiec") || clean == "de" -> "Niemiecki"
        clean.contains("italian") || clean.contains("włosk") || clean.contains("wlosk") || clean == "it" -> "Włoski"
        clean.contains("japanese") || clean.contains("japoń") || clean.contains("japon") || clean == "ja" -> "Japoński"
        clean.contains("chinese") || clean.contains("chiń") || clean.contains("chin") || clean == "zh" -> "Chiński"
        clean.contains("english") || clean.contains("angiel") || clean == "en" -> "Angielski"
        else -> language
    }


}
