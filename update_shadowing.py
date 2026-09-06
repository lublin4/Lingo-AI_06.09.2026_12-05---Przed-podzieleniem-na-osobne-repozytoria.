import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    content = f.read()

# Remove the StageIndicator rendering
content = re.sub(r'// 6-Stage Pipeline Indicator \(Only shown when state is Success\)\s*if \(uiState is ShadowingUiState\.Success\) \{.*?Spacer\(modifier = Modifier\.height\(16\.dp\)\)\s*\}', '', content, flags=re.DOTALL)

# Replace AnimatedContent with a scrolling Column
animated_content_pattern = r'AnimatedContent\(\s*targetState = activeStage.*?// \*\*\* BOTTOM NAV BAR \*\*\*'
replacement = """
                        Column(
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
                                accentColor = accentFuchsia
                            )
                            Stage6CommunicateView(
                                segment = segment,
                                targetLanguage = targetLanguage,
                                onNavigateToChat = onNavigateToChat,
                                accentColor = accentCyan
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }

            // *** BOTTOM NAV BAR ***"""
content = re.sub(animated_content_pattern, replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.write(content)

