package com.example.data.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Bezpieczny i szybki ekstraktor treści z artykułów internetowych (URL).
 * Pobiera stronę w tle via OkHttp, oczyszcza kod ze skryptów/stylów/menu
 * i wyodrębnia czystą treść oraz nagłówek artykułu dla generatora StoryLearning®.
 */
object WebArticleExtractor {

    private const val TAG = "WebArticleExtractor"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    data class ExtractedArticle(
        val url: String,
        val domain: String,
        val title: String,
        val cleanContent: String
    )

    suspend fun extractFromUrl(rawUrl: String): Result<ExtractedArticle> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = rawUrl.trim()
            val finalUrl = if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                "https://$trimmedUrl"
            } else {
                trimmedUrl
            }

            val domain = try {
                val uri = java.net.URI(finalUrl)
                uri.host?.removePrefix("www.") ?: finalUrl
            } catch (e: Exception) {
                finalUrl
            }

            // Realistyczny nagłówek przeglądarki mobilnej, zapobiegający blokadom 403 (RTVE, BBC, Politico itp.)
            val request = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7")
                .header("Accept-Language", "es-ES,es;q=0.9,en-US,en;q=0.8,pl;q=0.7")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Błąd HTTP ${response.code}: Serwer źródłowy odrzucił połączenie.")
                )
            }

            val html = response.body?.string() ?: ""
            if (html.isBlank()) {
                return@withContext Result.failure(
                    Exception("Strona źródłowa zwróciła pustą treść.")
                )
            }

            val title = extractTitle(html).ifBlank { "Artykuł z $domain" }
            val cleanText = sanitizeHtmlToReadableText(html)

            if (cleanText.length < 50) {
                return@withContext Result.failure(
                    Exception("Nie udało się odczytać treści artykułu (możliwa blokada paywall lub skryptowa strona SPA). Możesz wkleić tekst ręcznie.")
                )
            }

            // Ograniczamy do pierwszych ~8000 znaków (esencja artykułu idealna dla Gemini)
            val trimmedContent = if (cleanText.length > 8000) cleanText.take(8000) + "..." else cleanText

            Result.success(
                ExtractedArticle(
                    url = finalUrl,
                    domain = domain,
                    title = title,
                    cleanContent = trimmedContent
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for $rawUrl", e)
            Result.failure(e)
        }
    }

    private fun extractTitle(html: String): String {
        return try {
            val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
            if (titleMatcher.find()) {
                cleanHtmlEntities(titleMatcher.group(1).trim())
            } else {
                val h1Matcher = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                if (h1Matcher.find()) {
                    cleanHtmlEntities(h1Matcher.group(1).trim())
                } else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun sanitizeHtmlToReadableText(html: String): String {
        var text = html

        // 1. Usunięcie niepotrzebnych bloków skryptów, styli, svg, nawigacji, stopek i nagłówków
        text = text.replace(Regex("(?s)<script.*?</script>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("(?s)<style.*?</style>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("(?s)<noscript.*?</noscript>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("(?s)<svg.*?</svg>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("(?s)<nav.*?</nav>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("(?s)<footer.*?</footer>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("(?s)<header.*?</header>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("(?s)<!--.*?-->", RegexOption.IGNORE_CASE), " ")

        // 2. Zamiana tagów akapitów i nagłówków na nowe linie
        text = text.replace(Regex("(?i)</?(p|h1|h2|h3|h4|h5|h6|li|br|div|blockquote)[^>]*>"), "\n")

        // 3. Usunięcie wszelkich pozostałych tagów HTML
        text = text.replace(Regex("<[^>]+>"), " ")

        // 4. Dekodowanie encji HTML (&nbsp;, &amp;, &quot;, &#8217; itp.)
        text = cleanHtmlEntities(text)

        // 5. Normalizacja białych znaków i usunięcie pustych linii
        val lines = text.split("\n")
            .map { it.trim() }
            .filter { line ->
                // Filtrujemy linie zbyt krótkie, typowe dla menu/przycisków (chyba że to treść)
                line.isNotBlank() && !line.matches(Regex("^(Share|Tweet|Follow|Comments|Cookie|Privacy|Terms|Menu|Search).*$", RegexOption.IGNORE_CASE))
            }

        return lines.joinToString("\n\n")
    }

    private fun cleanHtmlEntities(input: String): String {
        var str = input
        str = str.replace("&nbsp;", " ")
        str = str.replace("&amp;", "&")
        str = str.replace("&quot;", "\"")
        str = str.replace("&apos;", "'")
        str = str.replace("&#39;", "'")
        str = str.replace("&lt;", "<")
        str = str.replace("&gt;", ">")
        str = str.replace("&bull;", "•")
        str = str.replace("&mdash;", "—")
        str = str.replace("&ndash;", "–")
        str = str.replace("&hellip;", "…")
        // Ogólne kody numeryczne
        str = str.replace(Regex("&#(\\d+);")) { match ->
            try {
                match.groupValues[1].toInt().toChar().toString()
            } catch (e: Exception) {
                ""
            }
        }
        return str
    }
}
