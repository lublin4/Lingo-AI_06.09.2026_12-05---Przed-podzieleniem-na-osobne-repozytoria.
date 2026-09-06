import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    text = f.read()

replacement = """        StageHeader(
            stageNum = 5,
            title = "5. AI Accelerator (Adaptive Context Evolution)",
            subtitle = "Obserwuj, jak jedno zdanie bazowe ewoluuje od biernego rozpoznania do swobodnej komunikacji.",
            accentColor = accentColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        val transformations = segment.transformations
        if (transformations.isNullOrEmpty()) {
            Text("Brak wygenerowanych przekształceń dla tego segmentu.", color = Color.White.copy(alpha = 0.6f))
        } else {
            transformations.forEachIndexed { index, tf ->
                if (index > 0) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Ewolucja",
                        tint = accentColor.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp).size(24.dp)
                    )
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (index == transformations.size - 1) accentColor else accentColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = accentColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = tf.type.uppercase(),
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tf.instruction, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(tf.transformedText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(tf.translation, color = accentColor, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { speechHelper.speakWithRate(tf.transformedText, 1.0f, targetLanguage) { _, _ -> } },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Odsłuchaj", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onSavePhrase(tf.transformedText, tf.translation) },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Zapisz", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}"""

# I need to match everything from `StageHeader` up to the end of Stage5TransformView function body.
start_str = """        StageHeader(
            stageNum = 5,"""

end_str = """                        }
                    }
                }
            }
        }
    }
}"""

pattern = re.compile(r'        StageHeader\(\s*stageNum = 5,.*?\}\s*\}\s*\}\s*\}\s*\}\s*\}\s*\}', re.DOTALL)
text = pattern.sub(replacement, text)

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.write(text)

