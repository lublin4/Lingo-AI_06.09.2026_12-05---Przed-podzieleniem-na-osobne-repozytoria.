import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    text = f.read()

text = text.replace('title = "1. Słuchaj (Comprehensible Input)",', 'title = "1. Percepcja (Rozumienie materiału)",')
text = text.replace('subtitle = "Zrozumiały kontekst (~95-98% zrozumienia). Odsłuchaj zdanie i odkryj kluczowe zwroty.",', 'subtitle = "Przeczytaj tekst i tłumaczenie. Słuchaj w normalnym lub zwolnionym tempie.",')

text = text.replace('title = "2. Naśladuj (Shadowing)",', 'title = "2. Synchronizacja słuchowo-artykulacyjna",')
text = text.replace('subtitle = "Słuchaj i powtarzaj tekst jednocześnie (z opóźnieniem ułamka sekundy). Skup się na rytmie, intonacji i wymowie.",', 'subtitle = "Naśladuj lektora z opóźnieniem. Skup się na rytmie, intonacji i wymowie.",')

text = text.replace('title = "3. Zautomatyzuj Tempo (Wymowa)",', 'title = "3. Automatyzacja chunków (często używanych konstrukcji)",')
text = text.replace('subtitle = "Buduj pamięć mięśniową przez powtarzanie ze zmiennym tempem.",', 'subtitle = "Zapisz i powtarzaj najważniejsze chunki (zbitki wyrazowe).",')
text = text.replace('title = "3. Zautomatyzuj tempo (Wymowa)",', 'title = "3. Automatyzacja chunków (często używanych konstrukcji)",')

text = text.replace('title = "4. Odtwórz z pamięci (Active Recall)",', 'title = "4. Aktywne odtworzenie z pamięci",')

text = text.replace('title = "5. Przekształć (Elastyczność językowa)",', 'title = "5. Transformacja (zmiana osoby, czasu, kontekstu)",')
text = text.replace('subtitle = "Użyj struktur w nowym kontekście. Odpowiedz na pytania lub stwórz własne zdanie.",', 'subtitle = "Zbuduj zdanie w innym czasie, osobie lub kontekście na podstawie oryginału.",')

text = text.replace('title = "6. Rozmawiaj (Spontaniczna produkcja)",', 'title = "6. Realna komunikacja",')
text = text.replace('subtitle = "Przenieś wyćwiczone wzorce do swobodnej rozmowy z Lingo-AI.",', 'subtitle = "Wykorzystaj kontekst i poznane chunki w prawdziwej rozmowie z AI.",')

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.write(text)
