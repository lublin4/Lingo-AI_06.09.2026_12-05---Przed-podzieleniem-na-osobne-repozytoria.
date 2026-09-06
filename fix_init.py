import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingViewModel.kt', 'r') as f:
    text = f.read()

# Only comment out inside init block
text = re.sub(
    r'(_metadataText\.value = shadowingRepository\.getCurrentMetadata\(\)\s*\n\s*)loadNewSegment\(\)',
    r'\g<1>// loadNewSegment()',
    text
)

with open('app/src/main/java/com/example/ui/shadowing/ShadowingViewModel.kt', 'w') as f:
    f.write(text)
