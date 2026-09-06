import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    text = f.read()

# 1. Remove Stage Indicator from `if (uiState is ShadowingUiState.Success) {`
text = re.sub(
    r'// 6-Stage Pipeline Indicator \(Only shown when state is Success\)\s*if \(uiState is ShadowingUiState\.Success\) \{\s*ShadowingStageIndicator\([\s\S]*?accentCyan\s*\)\s*Spacer\(modifier = Modifier\.height\(16\.dp\)\)\s*\}',
    '',
    text
)

# 2. Replace AnimatedContent & Bottom Navigation Stage Controls with scrolling view
start_marker = "                        AnimatedContent("
end_marker = "            // Bottom Navigation Stage Controls"

start_idx = text.find(start_marker)
end_idx = text.find(end_marker)

if start_idx != -1 and end_idx != -1:
    replacement = """                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
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
                            
                            Stage1ListenView(
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
                            Stage2ShadowView(
                                segment = segment,
                                speechHelper = speechHelper,
                                speechProgressRange = speechProgressRange,
                                targetLanguage = targetLanguage,
                                accentColor = accentIndigo
                            )
                            Stage3AutomateView(
                                segment = segment,
                                speechHelper = speechHelper,
                                viewModel = viewModel,
                                tempoCycleActive = tempoCycleActive,
                                currentRepetition = currentRepetition,
                                currentTempo = currentTempo,
                                speechProgressRange = speechProgressRange,
                                targetLanguage = targetLanguage,
                                accentColor = accentAmber
                            )
                            Stage4RetrieveView(
                                segment = segment,
                                speechHelper = speechHelper,
                                targetLanguage = targetLanguage,
                                accentColor = accentEmerald
                            )
                            Stage5TransformView(
                                segment = segment,
                                speechHelper = speechHelper,
                                targetLanguage = targetLanguage,
                                onSavePhrase = { phrase, trans ->
                                    viewModel.saveKeyPhraseToVocabulary(phrase, trans)
                                },
                                accentColor = accentCyan
                            )
                            Stage6CommunicateView(
                                segment = segment,
                                onNavigateToChat = onNavigateToChat,
                                onNewSegment = { viewModel.loadNewSegment() },
                                accentColor = accentIndigo
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }"""
    
    end_nav_idx = text.find("        }\n    }\n}\n\n// INTEGRATED TOP PROMPT")
    if end_nav_idx != -1:
        text = text[:start_idx] + replacement + text[end_nav_idx:]
    else:
        print("Could not find end of bottom nav")

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.write(text)
