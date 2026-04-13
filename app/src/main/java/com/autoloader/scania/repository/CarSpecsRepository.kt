package com.autoloader.scania.repository

import com.autoloader.scania.model.CarSpecs
import com.autoloader.scania.model.FetchResult
import com.autoloader.scania.model.SpecSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.UnsupportedMimeTypeException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Репозиторий характеристик автомобилей.
 * Единственный экземпляр создаётся в ViewModel и живёт с ней.
 *
 * Стратегия получения данных:
 *   1. auto-data.net     (сеть, таймаут 10 с)
 *   2. carfolio.com      (сеть, таймаут 10 с)
 *   3. FallbackDatabase  (встроенная база — никогда не падает)
 *
 * FetchResult.Error возвращается ТОЛЬКО если все три источника
 * вернули исключение и fallback тоже выбросил (невозможно на практике,
 * т.к. fallback не обращается к сети).
 */
class CarSpecsRepository {

    companion object {
        private const val NETWORK_TIMEOUT_MS = 10_000L
        private const val JSOUP_TIMEOUT_MS   = 8_000
        private val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private val fallback = FallbackDatabase()

    // ─── Публичный API ────────────────────────────────────────────────────────

    suspend fun fetch(query: String): FetchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext FetchResult.Error(query, "Пустой запрос")
        }

        // Пробуем сеть с общим таймаутом
        val networkResult: CarSpecs? = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            tryAutoDataNet(trimmed) ?: tryCarfolio(trimmed)
        }

        if (networkResult != null) {
            return@withContext FetchResult.Success(networkResult)
        }

        // Fallback — всегда возвращает данные
        return@withContext try {
            FetchResult.Success(fallback.estimate(trimmed))
        } catch (e: Exception) {
            FetchResult.Error(query, "Внутренняя ошибка базы: ${e.message}")
        }
    }

    // ─── Источник 1: auto-data.net ────────────────────────────────────────────

    private fun tryAutoDataNet(query: String): CarSpecs? {
        return try {
            val parts = query.split(" ").filter { it.isNotBlank() }
            if (parts.size < 2) return null

            val searchUrl = buildString {
                append("https://www.auto-data.net/en/search?q=")
                append(parts.take(2).joinToString("+"))
            }

            val searchDoc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT).timeout(JSOUP_TIMEOUT_MS)
                .get()

            val carUrl = searchDoc
                .select("a.car-title, a[href*='/model-']")
                .firstOrNull()?.absUrl("href") ?: return null

            val carPage = Jsoup.connect(carUrl)
                .userAgent(USER_AGENT).timeout(JSOUP_TIMEOUT_MS)
                .get()

            parseSpecTable(query, carPage.select("table tr").map { row ->
                val cells = row.select("td")
                if (cells.size >= 2) cells[0].text() to cells[1].text() else "" to ""
            }, SpecSource.NETWORK)

        } catch (e: UnknownHostException)        { null } // нет сети
        catch (e: SocketTimeoutException)        { null }
        catch (e: HttpStatusException)           { null }
        catch (e: UnsupportedMimeTypeException)  { null }
        catch (e: IOException)                   { null }
        catch (e: Exception)                     { null }
    }

    // ─── Источник 2: carfolio.com ─────────────────────────────────────────────

    private fun tryCarfolio(query: String): CarSpecs? {
        return try {
            val parts = query.split(" ").filter { it.isNotBlank() }
            if (parts.isEmpty()) return null

            val searchUrl = "https://www.carfolio.com/specifications/search/?make=${parts[0]}"
            val searchDoc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT).timeout(JSOUP_TIMEOUT_MS)
                .get()

            val carUrl = searchDoc
                .select("a[href*='/specifications/']")
                .firstOrNull()?.absUrl("href") ?: return null

            val carPage = Jsoup.connect(carUrl)
                .userAgent(USER_AGENT).timeout(JSOUP_TIMEOUT_MS)
                .get()

            parseSpecTable(query, carPage.select("tr").map { row ->
                val cells = row.select("td")
                if (cells.size >= 2) cells[0].text() to cells[1].text() else "" to ""
            }, SpecSource.NETWORK)

        } catch (e: Exception) { null }
    }

    // ─── Общий парсер таблицы характеристик ──────────────────────────────────

    private fun parseSpecTable(
        query: String,
        rows: List<Pair<String, String>>,
        source: SpecSource
    ): CarSpecs? {
        var weight = 0; var height = 0; var length = 0

        for ((label, value) in rows) {
            val l = label.lowercase()
            when {
                (l.contains("curb weight") || l.contains("kerb weight")) && weight == 0 ->
                    weight = extractFirstInt(value)
                l.contains("height") && !l.contains("ground") && !l.contains("seat") && height == 0 ->
                    height = extractFirstInt(value)
                l.contains("length") && !l.contains("wheel") && length == 0 ->
                    length = extractFirstInt(value)
            }
        }

        // Санитарная проверка: значения должны быть физически возможными
        if (weight < 500 || weight > 6_000) return null
        if (height < 1_000 || height > 2_500) return null
        if (length < 2_000 || length > 7_000) return null

        return runCatching {
            CarSpecs(query, buildDisplayName(query), weight, height, length, source)
        }.getOrNull()
    }

    // ─── Утилиты ──────────────────────────────────────────────────────────────

    /** Извлекает первое целое из «1 450 kg» → 1450 */
    private fun extractFirstInt(s: String): Int =
        s.replace(",", "").replace("\u00a0", "").replace(" ", "")
            .filter { it.isDigit() }
            .take(6)   // не более 6 цифр подряд
            .toIntOrNull() ?: 0

    private fun buildDisplayName(query: String): String =
        query.trim().split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
}
