import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace(
    'fun setLanguage(language: String) {',
    'fun resetToIdle() {\n        _uiState.value = ShadowingUiState.Idle\n    }\n\n    fun setLanguage(language: String) {'
)

with open('app/src/main/java/com/example/ui/shadowing/ShadowingViewModel.kt', 'w') as f:
    f.write(text)
