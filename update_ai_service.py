import re

with open('app/src/main/java/com/example/data/api/TutorAiService.kt', 'r') as f:
    text = f.read()

replacement = """            2. TRANSFORM (AI Accelerator - Adaptive Context Evolution): Weź jedno bazowe, użyteczne zdanie z wygenerowanego tekstu i zbuduj sekwencję 5-7 wariantów, które krok po kroku ewoluują - od najprostszego, biernego rozpoznania, do coraz bardziej złożonego, aż po spontaniczne użycie i pytania. Przykład: (1. "I need a hotel" -> 2. "I need a cheap hotel" -> 3. "I need a hotel near the airport" -> 4. "I need a hotel because my flight was cancelled" -> 5. "Can you recommend a hotel?" -> 6. "Tell me about the worst hotel you've visited"). 
            3. COMMUNICATE: Krótkie wyzwanie sytuacyjne zakotwiczone w tym temacie (instrukcja w języku polskim, np. "Wyobraź sobie, że...") do przeprowadzenia czatu na żywo.
            
            Zwróć odpowiedź WYŁĄCZNIE jako czysty obiekt JSON o następującej strukturze (żadnych innych tekstów poza JSON-em, bez bloku ```json). Używaj pojedynczych apostrofów zamiast podwójnych cudzysłowów wewnątrz wartości tekstowych:
            {
              "foreignText": "tekst w języku obcym ($targetLanguage)",
              "nativeTranslation": "tłumaczenie całego tekstu na język polski",
              "keyPhrases": [
                {
                  "phrase": "kluczowy zwrot w języku obcym",
                  "translation": "tłumaczenie zwrotu na język polski"
                }
              ],
              "retrievedQuestions": [
                {
                  "question": "pytanie 1 sprawdzające zapamiętanie w języku $targetLanguage",
                  "answer": "krótka modelowa odpowiedź w języku $targetLanguage"
                }
              ],
              "transformations": [
                {
                  "type": "Baza / Rozszerzenie (Przymiotnik) / Okolicznik / Przyczyna / Pytanie / Personalizacja",
                  "transformedText": "ewoluujące zdanie w języku $targetLanguage",
                  "translation": "polskie tłumaczenie zdania",
                  "instruction": "krótki komentarz, co się zmieniło lub na co zwrócić uwagę (np. 'Dodajemy kontekst miejsca...')"
                }
              ],"""

text = re.sub(
    r'2\. TRANSFORM: 3 warianty przekształcenia[\s\S]*?"instruction": "krótka instrukcja dla ucznia po polsku, np\. \'Powiedz to w czasie przeszłym\.\.\.\'"\s*\}\s*\]\,',
    replacement,
    text
)

with open('app/src/main/java/com/example/data/api/TutorAiService.kt', 'w') as f:
    f.write(text)
