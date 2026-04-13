package com.autoloader.scania.repository

import com.autoloader.scania.model.CarSpecs
import com.autoloader.scania.model.SpecSource

/**
 * Встроенная база средних характеристик по категориям автомобилей.
 * Данные — медианные значения по сегменту, источник: ADAC / EurotaxGlass.
 * Никогда не обращается к сети → всегда возвращает результат.
 */
class FallbackDatabase {

    private data class Profile(val weightKg: Int, val heightMm: Int, val lengthMm: Int)

    private val categories: List<Pair<List<String>, Profile>> = listOf(

        // ── Коммерческие / микроавтобусы ──────────────────────────────────────
        listOf("sprinter", "transit", "master", "boxer", "crafter",
               "vito", "vivaro", "trafic", "ducato", "jumper", "hiace") to
            Profile(2_050, 1_970, 5_350),

        // ── Полноразмерные пикапы ─────────────────────────────────────────────
        listOf("f-150", "f150", "ram 1500", "silverado", "sierra", "tundra",
               "hilux", "ranger", "amarok", "navara", "l200", "triton") to
            Profile(2_100, 1_810, 5_280),

        // ── Крупные рамные внедорожники ───────────────────────────────────────
        listOf("land cruiser 200", "land cruiser 300", "patrol y62",
               "tahoe", "suburban", "expedition", "navigator", "sequoia",
               "armada", "qx80", "gx", "lx") to
            Profile(2_550, 1_915, 5_020),

        // ── Land Cruiser (без уточнения серии) ────────────────────────────────
        listOf("land cruiser", "prado", "fj cruiser") to
            Profile(2_100, 1_870, 4_780),

        // ── Defender / Wrangler ────────────────────────────────────────────────
        listOf("defender 90", "wrangler 2-door") to
            Profile(1_880, 1_910, 4_320),
        listOf("defender 110", "defender 130", "wrangler unlimited") to
            Profile(2_200, 1_970, 4_760),

        // ── Средние SUV ───────────────────────────────────────────────────────
        listOf("rav4", "cr-v", "crv", "cx-5", "tucson", "sportage",
               "qashqai", "tiguan", "x5", "x3", "gle", "q5", "q7",
               "glc", "gla", "rdx", "mdx", "forester", "outback",
               "4runner", "highlander", "pilot", "telluride", "sorento") to
            Profile(1_840, 1_685, 4_680),

        // ── Компактные кроссоверы ─────────────────────────────────────────────
        listOf("hr-v", "hrv", "cx-30", "kona", "creta", "seltos",
               "captur", "t-roc", "ecosport", "puma", "yaris cross",
               "juke", "arona", "mokka", "trailblazer") to
            Profile(1_440, 1_595, 4_245),

        // ── Минивэны ─────────────────────────────────────────────────────────
        listOf("sienna", "odyssey", "carnival", "caravelle",
               "galaxy", "sharan", "touran", "scenic", "zafira",
               "berlingo", "rifter", "partner", "combo") to
            Profile(2_000, 1_740, 4_860),

        // ── Электромобили (тяжелее ДВС-аналогов) ─────────────────────────────
        listOf("tesla", "model 3", "model y", "model s", "model x",
               "ioniq 6", "ioniq 5", "ev6", "enyaq", "id.4", "id4",
               "bz4x", "ariya", "mach-e", "lightning") to
            Profile(2_050, 1_585, 4_760),

        // ── Бизнес-класс / большие седаны ────────────────────────────────────
        listOf("e-class", "e class", "5 series", "a6", "xf", "genesis g80",
               "camry", "accord", "passat", "mondeo", "insignia",
               "laguna", "508") to
            Profile(1_660, 1_470, 4_870),

        // ── Спорткары ────────────────────────────────────────────────────────
        listOf("mustang", "camaro", "corvette", "911", "cayman",
               "boxster", "supra", "86", "brz", "m4", "m3",
               "amg gt", "f-type", "rc", "lc") to
            Profile(1_540, 1_300, 4_440),

        // ── Компактные хэтчбеки и седаны ─────────────────────────────────────
        listOf("golf", "focus", "corolla", "civic", "polo",
               "clio", "megane", "astra", "a3", "leon", "scala",
               "elantra", "i30", "ceed", "308", "208", "i20") to
            Profile(1_390, 1_455, 4_360),

        // ── Городские / субкомпактные ─────────────────────────────────────────
        listOf("yaris", "fiesta", "up", "fabia", "ibiza",
               "jazz", "rio", "sandero", "swift", "aygo", "twingo",
               "spark", "picanto", "i10") to
            Profile(1_130, 1_490, 3_930),
    )

    /**
     * Находит наиболее подходящую категорию для запроса.
     * Матчинг: проверяем каждое ключевое слово как подстроку (case-insensitive).
     * Берём первое совпадение — категории упорядочены от специфичных к общим.
     */
    fun estimate(query: String): CarSpecs {
        val q = query.lowercase()
        val profile = categories
            .firstOrNull { (keywords, _) -> keywords.any { q.contains(it) } }
            ?.second
            ?: Profile(1_500, 1_500, 4_500)  // дефолт: средний легковой

        return CarSpecs(
            query       = query,
            displayName = buildDisplayName(query),
            weightKg    = profile.weightKg,
            heightMm    = profile.heightMm,
            lengthMm    = profile.lengthMm,
            source      = SpecSource.FALLBACK_DB
        )
    }

    private fun buildDisplayName(query: String): String =
        query.trim().split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
}
