package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.ui.speech.TutorSpeechHelper
import com.example.ui.theme.LingoPrimary

/**
 * Zaktualizowany Duszek Wiadomości AI (v2.7.5-FINAL-FIX)
 * Gwarantuje widoczność AI Acceleratora oraz pełną pętlę tempa (1.0 -> 0.9 -> 0.8 -> 0.75).
 */
@Composable
fun AiMessageBubble(
    message: ChatMessage,
    language: String,
    speechHelper: TutorSpeechHelper,
    cefrLevel: String = "B1",
    stressLevel: Int = 0,
    speedMultiplier: Float = 1.0f, // Prędkość początkowa z ViewModelu
    onDictionaryClick: () -> Unit,
    onAcceleratorClick: () -> Unit
) {
    // Definicja tła i obramowania w zależności od poziomu stresu sesji
    val cardBg = when (stressLevel) {
        1 -> Color(0xFFFFFDF5) // Light Yellow dla Szybkiej Reakcji
        2 -> Color(0xFFFFF8F8) // Light Red dla Chaosu
        else -> Color(0xFFF9FBF9) // Defaut Greenish
    }
    
    val cardBorder = when (stressLevel) {
        1 -> BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f))
        2 -> BorderStroke(1.5.dp, Color(0xFFE53935).copy(alpha = 0.7f))
        else -> BorderStroke(1.dp, Color(0xFF81C784).copy(alpha = 0.25f))
    }
    
    val stressBadgeText = when (stressLevel) {
        1 -> "⚡ Szybka Reakcja"
        2 -> "🔥 Ekstremalny Chaos"
        else -> "🍃 Oaza Spokoju"
    }
    
    val stressBadgeColor = when (stressLevel) {
        1 -> Color(0xFFFFB300)
        2 -> Color(0xFFD32F2F)
        else -> Color(0xFF4CAF50)
    }

    // --- POPRAWKA 1: SZTYWNA LOGIKA TEMPA (Hardcoded Loop) ---
    // Lokalny stan zapamiętuje wybraną prędkość dla tej konkretnej wiadomości.
    var currentSpeed by remember { mutableStateOf(speedMultiplier) }
    
    // Parsowanie dialogu - usuwanie wewnętrznych tagów ambientowych z LLM
    val cleanDialogue = message.originalText
        .replace(Regex("\\*\\*Ambient\\*\\*:.*?\\n"), "")
        .replace(Regex("\\*\\*Sound\\*\\*:.*?\\n"), "")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            // Zwiększona szerokość duszka lektora (z 0.85 na 0.92), aby zmieścić 4 przyciski
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalAlignment = Alignment.Start
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Nagłówek duszka lektora
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Lingo Tutor", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LingoPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(stressBadgeColor.copy(alpha = 0.12f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text(text = stressBadgeText, color = stressBadgeColor, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Tekst dialogu
                    Text(text = cleanDialogue, fontSize = 15.sp, color = Color(0xFF2E3D49), lineHeight = 22.sp)
                    
                    // Opcjonalne tłumaczenie
                    if (message.translatedText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = message.translatedText, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Odtwórz
                        IconButton(
                            onClick = { speechHelper.speak(message.originalText, language, cefrLevel, stressLevel = stressLevel, speedMultiplier = currentSpeed) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Odtwórz", tint = LingoPrimary, modifier = Modifier.size(16.dp))
                        }

                        // 2. Tempo
                        TextButton(
                            onClick = { 
                                currentSpeed = when(currentSpeed) {
                                    1.0f -> 0.85f
                                    0.85f -> 0.75f
                                    0.75f -> 0.65f
                                    else -> 1.0f
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text("🐢 ${currentSpeed}x", fontSize = 10.sp, color = LingoPrimary, fontWeight = FontWeight.Bold)
                        }

                        // 3. Słownik
                        IconButton(
                            onClick = onDictionaryClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = "Słownik", tint = LingoPrimary, modifier = Modifier.size(16.dp))
                        }

                        // 4. AI Accelerator ⚡ (Gwarancja widoczności)
                        FilterChip(
                            selected = false,
                            onClick = onAcceleratorClick,
                            label = { Text("⚡ Accel", fontSize = 9.5.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFFFFF8E1),
                                labelColor = Color(0xFFFF8F00)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = Color(0xFFFFB300).copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }
        }
    }
}