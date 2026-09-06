import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    text = f.read()

replacement = """                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${getLanguageDisplayName(targetLanguage)} • $topicTitle",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = False)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onResetToIdle() }.padding(4.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Zmień temat", tint = accentCyan, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Zmień temat", color = accentCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }"""

text = re.sub(
    r'Spacer\(modifier = Modifier\.height\(2\.dp\)\)\s*\n\s*Text\(\s*text = "\$\{getLanguageDisplayName\(targetLanguage\)\} • \$topicTitle",\s*\n\s*color = Color\.White,\s*\n\s*fontSize = 14\.sp,\s*\n\s*fontWeight = FontWeight\.Bold,\s*\n\s*maxLines = 1\s*\n\s*\)',
    replacement,
    text
)

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.write(text)
