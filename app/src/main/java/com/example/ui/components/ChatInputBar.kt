package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LingoPrimary
import com.example.ui.theme.LingoSecondary

@Composable
fun ChatInputBar(
    messageText: MutableState<String>,
    language: String,
    onDictionaryClick: () -> Unit,
    onSubmit: () -> Unit
) {
    val kbController = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDictionaryClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(LingoSecondary.copy(alpha = 0.15f))
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "Słownik AI",
                tint = LingoSecondary
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        OutlinedTextField(
            value = messageText.value,
            onValueChange = { messageText.value = it },
            placeholder = { Text("Reply in $language...") },
            modifier = Modifier
                .weight(1f)
                .testTag("chat_text_input"),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LingoPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (messageText.value.isNotBlank()) {
                    onSubmit()
                    kbController?.hide()
                }
            }),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        IconButton(
            onClick = {
                if (messageText.value.isNotBlank()) {
                    onSubmit()
                    kbController?.hide()
                }
            },
            modifier = Modifier
                .clip(CircleShape)
                .background(LingoPrimary)
                .size(48.dp)
                .testTag("chat_send_btn")
        ) {
            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
        }
    }
}
