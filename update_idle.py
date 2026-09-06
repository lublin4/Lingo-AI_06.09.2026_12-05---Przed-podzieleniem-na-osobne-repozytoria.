import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    text = f.read()

idle_state_ui = """                    is ShadowingUiState.Idle -> {
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
                    }"""

text = re.sub(
    r'when\s*\(val state = uiState\)\s*\{',
    'when (val state = uiState) {\n' + idle_state_ui,
    text
)

# And in ShadowingHeaderBar, make sure "Zmień temat / Nowy trening" resets to Idle
text = re.sub(
    r'onOpenSetup\(\)',
    r'viewModel.resetToIdle()',
    text
)
# We also need to add resetToIdle in ShadowingViewModel
with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.write(text)
