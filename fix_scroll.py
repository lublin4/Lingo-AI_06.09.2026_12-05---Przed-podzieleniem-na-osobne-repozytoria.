import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    text = f.read()

# Remove .verticalScroll(rememberScrollState()) from the individual Stage views
for i in range(1, 7):
    text = re.sub(
        r'fun Stage' + str(i) + r'.*?Modifier\s*\n\s*\.fillMaxSize\(\)\s*\n\s*\.verticalScroll\(rememberScrollState\(\)\),',
        lambda m: m.group(0).replace('.verticalScroll(rememberScrollState()),', ','),
        text,
        flags=re.DOTALL
    )
    # Also if it's not fillMaxSize but fillMaxWidth:
    text = re.sub(
        r'fun Stage' + str(i) + r'.*?Modifier\s*\n\s*\.fillMaxWidth\(\)\s*\n\s*\.verticalScroll\(rememberScrollState\(\)\),',
        lambda m: m.group(0).replace('.verticalScroll(rememberScrollState()),', ','),
        text,
        flags=re.DOTALL
    )
    
with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.write(text)
