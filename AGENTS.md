# Instrukcje i reguły dla AI Agent (Lingo-AI)

Plik ten zawiera stałe wytyczne dotyczące rozwoju aplikacji **Lingo-AI**. Agent AI automatycznie wczytuje te reguły przy każdej sesji, co gwarantuje spójność kodu i zachowanie Twoich preferencji.

---

## 🧠 Główne reguły projektowe i funkcjonalne

### 1. Słownictwo i Baza Danych (Vocabulary)
- **Brak duplikatów:** Przy dodawaniu słówek do bazy (zarówno automatycznie podczas rozmowy z AI, jak i ręcznie przez użytkownika) system musi rygorystycznie sprawdzać, czy słowo już istnieje w danym języku (case-insensitive, ignorując białe znaki na początku i końcu). Duplikaty muszą być odrzucane.
- **Ignorowanie interpunkcji w korekcie:** Jeśli różnica między wypowiedzią użytkownika a wersją skorygowaną przez AI polega wyłącznie na interpunkcji, wielkości liter lub braku znaku zapytania (częsty problem przy rozpoznawaniu mowy/głosowym), **nie oznaczaj tego jako błąd** (`isCorrected = false`) i nie twórz nowego wpisu w historii błędów.

### 2. Inteligentne Powtórki PRO
- **Prezentacja poprawnych zdań:** W widoku powtórek i błędów nie pokazujemy użytkownikowi wersji błędnych (czerwonych, przekreślonych). Skupiamy się wyłącznie na wersji poprawnej, która ma być czytelna i estetyczna.
- **Kreatywny poziom nauki (Tłumaczenie):** Na awersie karty w powtórkach nie pokazujemy błędnego zdania ani od razu poprawnego. Zamiast tego stawiamy wyzwanie tłumaczeniowe: pokazujemy polskie tłumaczenie zdania (pobierane dynamicznie z API) i prosimy użytkownika o pomyślenie, jak poprawnie wypowiedzieć to zdanie w języku obcym. Poprawna wersja pokazuje się na rewersie.
- **Usuwanie zdań:** W widoku Moich Błędów oraz powtórek użytkownik musi mieć zawsze dostępną ikonę usuwania (kosza), pozwalającą trwale skasować dane zdanie lub słówko z bazy.

### 3. Stabilność kompilacji i CI/CD (GitHub)
- **Automatyczny podpis (Keystore):** W pliku `app/build.gradle.kts` konfiguracja podpisywania wersji debugowej musi dynamicznie sprawdzać obecność pliku `debug.keystore`. Jeśli plik lokalny nie istnieje (np. na serwerach GitHub Actions), gradle powinien automatycznie przełączyć się na standardowy certyfikat debugowania systemu Android, zapobiegając błędom budowania ("keystore not found").
