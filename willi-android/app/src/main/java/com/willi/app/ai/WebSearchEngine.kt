package com.willi.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Moteur de recherche internet de WILLI.
 * Utilise DuckDuckGo Instant Answer API (gratuit, sans clé)
 * + extraction de contenu web via Jsoup.
 */
class WebSearchEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("User-Agent", "WILLI-AI/2.0 (Android; Educational)")
                .build()
            chain.proceed(req)
        }
        .build()

    data class SearchResult(
        val title: String,
        val snippet: String,
        val url: String,
        val fullContent: String = ""
    )

    // ─── Recherche DuckDuckGo ─────────────────────────────────────────────────

    suspend fun search(query: String, fetchContent: Boolean = false): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<SearchResult>()

            // 1. DuckDuckGo Instant Answer (réponse rapide)
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val ddgUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"

                val response = client.newCall(Request.Builder().url(ddgUrl).build()).execute()
                val body = response.body?.string() ?: ""

                if (body.isNotBlank()) {
                    val json = JSONObject(body)

                    // Réponse abstraite (Wikipedia-like)
                    val abstract = json.optString("AbstractText", "")
                    val abstractUrl = json.optString("AbstractURL", "")
                    val abstractTitle = json.optString("AbstractSource", query)

                    if (abstract.isNotBlank()) {
                        results.add(SearchResult(
                            title = abstractTitle,
                            snippet = abstract.take(500),
                            url = abstractUrl,
                            fullContent = if (fetchContent) abstract else ""
                        ))
                    }

                    // Réponse directe
                    val answer = json.optString("Answer", "")
                    if (answer.isNotBlank() && answer != abstract) {
                        results.add(SearchResult(
                            title = "Réponse directe",
                            snippet = answer.take(300),
                            url = abstractUrl
                        ))
                    }

                    // Topics liés
                    val topics = json.optJSONArray("RelatedTopics")
                    if (topics != null) {
                        for (i in 0 until minOf(topics.length(), 4)) {
                            val topic = topics.optJSONObject(i) ?: continue
                            val text = topic.optString("Text", "")
                            val url = topic.optString("FirstURL", "")
                            if (text.isNotBlank()) {
                                results.add(SearchResult(
                                    title = extractTitle(text),
                                    snippet = text.take(300),
                                    url = url
                                ))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Silencieux — on essaie le fallback
            }

            // 2. Si peu de résultats, scrape un moteur de recherche
            if (results.size < 2) {
                results.addAll(scrapeSearchResults(query))
            }

            results.take(5)
        }

    // ─── Extraction de contenu d'une page ────────────────────────────────────

    suspend fun fetchPageContent(url: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent("WILLI-AI/2.0")
                .timeout(10000)
                .get()

            // Retire les éléments non pertinents
            doc.select("script, style, nav, footer, header, aside, .ad, .cookie").remove()

            // Extrait le texte principal
            val body = doc.body()?.text() ?: ""
            body.take(2000) // Limite pour éviter les tokens excessifs
        } catch (e: Exception) {
            ""
        }
    }

    // ─── Résumé d'actualités sur un sujet ────────────────────────────────────

    suspend fun searchNews(topic: String): List<SearchResult> = withContext(Dispatchers.IO) {
        search("$topic actualité 2025 2026", fetchContent = false)
    }

    // ─── Recherche Wikipedia directe ─────────────────────────────────────────

    suspend fun searchWikipedia(query: String): String = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val wikiUrl = "https://fr.wikipedia.org/api/rest_v1/page/summary/$encoded"

            val response = client.newCall(Request.Builder().url(wikiUrl).build()).execute()
            val body = response.body?.string() ?: return@withContext ""

            val json = JSONObject(body)
            json.optString("extract", "")
        } catch (e: Exception) {
            ""
        }
    }

    // ─── Fallback scraping ────────────────────────────────────────────────────

    private fun scrapeSearchResults(query: String): List<SearchResult> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            // Utilise Ecosia (respecte la vie privée, ne bloque pas les bots simples)
            val doc = Jsoup.connect("https://www.ecosia.org/search?q=$encoded")
                .userAgent("Mozilla/5.0 (Linux; Android 12)")
                .timeout(10000)
                .get()

            doc.select(".result").take(3).mapNotNull { el ->
                val title = el.select(".result-title").text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val snippet = el.select(".result-snippet").text()
                val url = el.select("a.result-url").attr("href")
                SearchResult(title = title, snippet = snippet, url = url)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─── Formatage des résultats pour l'IA ───────────────────────────────────

    fun formatResultsForPrompt(results: List<SearchResult>, query: String): String {
        if (results.isEmpty()) return ""

        return buildString {
            appendLine("=== RÉSULTATS INTERNET pour: \"$query\" ===")
            results.forEachIndexed { i, r ->
                appendLine("[${i + 1}] ${r.title}")
                if (r.snippet.isNotBlank()) appendLine("    ${r.snippet}")
                if (r.url.isNotBlank()) appendLine("    Source: ${r.url}")
                appendLine()
            }
            appendLine("Utilise ces informations pour enrichir ta réponse. Cite tes sources.")
        }
    }

    private fun extractTitle(text: String): String {
        val parts = text.split(" - ")
        return if (parts.size > 1) parts[0].trim() else text.take(60)
    }
}
