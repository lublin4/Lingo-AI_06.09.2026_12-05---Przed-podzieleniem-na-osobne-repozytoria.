# AI Language Tutor 🗣️🤖

Twój osobisty asystent AI do nauki języków obcych bezpośrednio na telefonie z systemem Android! Aplikacja pozwala na prowadzenie naturalnych konwersacji głosowych i tekstowych, symulowanie scenariuszy z życia wziętych (roleplay) oraz otrzymywanie natychmiastowych wskazówek gramatycznych i wymowy zasilanych przez najnowsze modele **Gemini**.

---

## 🚀 Jak zainstalować aplikację na telefonie (Szybko i Prosto)

Nie musisz kompilować kodu ani pobierać żadnych archiwów ZIP! Dzięki zintegrowanej automatyzacji **GitHub Actions**, gotowy, świeżo zbudowany plik instalacyjny `.apk` jest zawsze dostępny bezpośrednio w zakładce **Releases** (Wydania) Twojego repozytorium na GitHubie.

### Krok po kroku:
1. **Pobierz gotowy plik APK:**
   - Na głównej stronie swojego repozytorium GitHub, po prawej stronie znajdź sekcję **Releases** (Wydania).
   - Kliknij na wydanie oznaczone jako **latest** (Najnowsza wersja).
   - W sekcji *Assets* kliknij bezpośrednio na plik **`app-debug.apk`**, aby go pobrać bezpośrednio na swój telefon (lub komputer).
   - *Alternatywnie, po wgraniu projektu na GitHub, bezpośredni link do pobrania najnowszej wersji to:*
     `https://github.com/TWÓJ_PROFIL_GITHUB/NAZWA_REPOZYTORIUM/releases/download/latest/app-debug.apk` *(zamień fragmenty dużymi literami na swoje dane)*.

2. **Zainstaluj aplikację:**
   - Otwórz pobrany plik `app-debug.apk` na swoim telefonie z systemem Android.
   - Jeśli telefon zapyta o zgodę na instalację aplikacji z nieznanych źródeł (np. z przeglądarki Chrome lub menedżera plików) - kliknij **Ustawienia** i zezwól na to jednorazowo.
   - Kliknij **Zainstaluj** i ciesz się aplikacją!

---

## 🔑 Konfiguracja klucza API Gemini

Aplikacja wykorzystuje sztuczną inteligencję Google Gemini do prowadzenia rozmów. Aby działała, potrzebuje ona klucza API (który możesz wygenerować całkowicie bezpłatnie):

1. Wejdź na stronę [Google AI Studio](https://aistudio.google.com/) i wygeneruj darmowy klucz API.
2. **Metoda łatwa (w aplikacji):**
   - Po zainstalowaniu aplikacji na telefonie, wejdź w jej ustawienia.
   - Wklej swój klucz API w wyznaczone pole. Zostanie on bezpiecznie zapisany w pamięci Twojego urządzenia.
3. **Metoda automatyczna (przy budowaniu aplikacji):**
   - Jeśli chcesz, aby pobierana aplikacja miała już wbudowany Twój klucz API:
   - Wejdź w ustawienia swojego repozytorium na GitHubie (**Settings** -> **Secrets and variables** -> **Actions**).
   - Dodaj nowy sekret (kliknij *New repository secret*).
   - Nazwij go `GEMINI_API_KEY` i wklej jako wartość swój klucz API.
   - Każda kolejna wersja zbudowana przez GitHub Actions będzie miała ten klucz automatycznie wbudowany!

---

## 🛠️ Informacje dla Programistów (Uruchamianie lokalne)

Jeśli chcesz zmodyfikować kod źródłowy lub uruchomić aplikację bezpośrednio na swoim komputerze:

### Wymagania wstępne:
- Zainstalowane środowisko **Android Studio** (zalecane Ladybug lub nowsze).
- JDK 21.

### Instrukcja uruchomienia:
1. Sklonuj to repozytorium na swój dysk.
2. Otwórz projekt w **Android Studio** (wybierz folder główny projektu).
3. Utwórz plik o nazwie `.env` w głównym folderze projektu (skorzystaj ze wzoru w `.env.example`) i dodaj tam swój klucz:
   ```env
   GEMINI_API_KEY=twój_klucz_api_tutaj
   ```
4. Podłącz fizyczny telefon z włączonym debugowaniem USB lub uruchom emulator w Android Studio.
5. Kliknij przycisk **Run** (zielona strzałka), aby skompilować i wgrać aplikację na urządzenie.
