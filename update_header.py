import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    text = f.read()

# Add onResetToIdle to ShadowingHeaderBar parameters
text = text.replace(
    '    onExitImport: (() -> Unit)?,',
    '    onExitImport: (() -> Unit)?,\n    onResetToIdle: () -> Unit,'
)

# Add it to the call in ShadowingScreen
text = text.replace(
    '                onExitImport = if (isImportMode) { { viewModel.exitImportMode() } } else null,',
    '                onExitImport = if (isImportMode) { { viewModel.exitImportMode() } } else null,\n                onResetToIdle = { viewModel.resetToIdle() },'
)

# Add an edit button next to the topic
text = re.sub(
    r'(Text\(\s*text = titleText,\s*color = Color\.White,\s*fontSize = 16\.sp,\s*fontWeight = FontWeight\.Bold,\s*maxLines = 1,\s*overflow = TextOverflow\.Ellipsis\s*\)\s*\n\s*\}\s*\n\s*Spacer\(modifier = Modifier\.height\(4\.dp\)\))',
    r'\g<1>\n                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onResetToIdle() }) {\n                        Icon(Icons.Default.Edit, contentDescription = "Zmień temat", tint = accentCyan, modifier = Modifier.size(12.dp))\n                        Spacer(modifier = Modifier.width(4.dp))\n                        Text("Zmień temat", color = accentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)\n                    }',
    text
)

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.write(text)
