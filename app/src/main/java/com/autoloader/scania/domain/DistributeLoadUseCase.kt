package com.autoloader.scania.domain

import com.autoloader.scania.model.*

/**
 * Use-case: построение плана погрузки.
 * Изолирует бизнес-логику от источников данных и UI.
 * Легко тестируется без Android-зависимостей.
 *
 * Физические параметры Scania P-series 4×4 (2008) + Uçsuoğlu:
 *   platformHeight[3] = 4000 − maxCarHeight[3]
 *   maxCarHeight[3]   = 4000 − 2550 = 1450 мм
 *   (единое значение, источник: таблица слотов, нигде не дублируется)
 */
class DistributeLoadUseCase {

    companion object {
        const val MAX_TOTAL_HEIGHT_MM    = 4_000
        const val WARNING_MARGIN_MM      = 150   // предупреждение если до лимита < 150 мм
        const val MAX_TRACTOR_TOTAL_KG   = 11_500 // допустимая нагрузка обеих осей тягача
        const val MAX_TRAILER_TOTAL_KG   = 18_000 // допустимая нагрузка всех осей прицепа
        const val MAX_TRACTOR_FRONT_KG   = 7_100  // передняя ось тягача (нормы ЕС)
        const val MAX_TRACTOR_REAR_KG    = 11_500 // задняя ось тягача
        const val MAX_TRAILER_PER_AXLE_KG = 10_000 // каждая ось прицепа

        /**
         * Единственное место определения слотов.
         * Правило: platformHeightMm + maxCarHeightMm = MAX_TOTAL_HEIGHT_MM (4000).
         * SlotConfig.init() проверяет это при создании → ошибка конфигурации
         * обнаруживается немедленно, не в рантайме.
         */
        val SLOT_CATALOG: List<SlotConfig> = listOf(
            // Тягач — нижний ярус
            SlotConfig(1, "Тягач — нижний, передняя позиция",
                platformHeightMm = 1_200, maxCarHeightMm = 2_800,
                isUpperDeck = false),
            SlotConfig(2, "Тягач — нижний, задняя позиция",
                platformHeightMm = 1_200, maxCarHeightMm = 2_800,
                isUpperDeck = false),
            // Тягач — над кабиной.
            // 4×4 рама выше стандарта на 250 мм → платформа 2 550 мм.
            // maxCarHeightMm = 4000 − 2550 = 1450 мм. Это единственное место.
            SlotConfig(3, "Тягач — верхний, над кабиной ⚠",
                platformHeightMm = 2_550, maxCarHeightMm = 1_450,
                isUpperDeck = true, isCriticalOverCab = true),
            // Прицеп — нижний ярус (рама ниже тягача на 150 мм)
            SlotConfig(4, "Прицеп — нижний, 1-я позиция",
                platformHeightMm = 1_050, maxCarHeightMm = 2_950,
                isUpperDeck = false),
            SlotConfig(5, "Прицеп — нижний, 2-я позиция",
                platformHeightMm = 1_050, maxCarHeightMm = 2_950,
                isUpperDeck = false),
            SlotConfig(6, "Прицеп — нижний, 3-я позиция",
                platformHeightMm = 1_050, maxCarHeightMm = 2_950,
                isUpperDeck = false),
            // Прицеп — верхний ярус
            SlotConfig(7, "Прицеп — верхний, 1-я позиция",
                platformHeightMm = 2_150, maxCarHeightMm = 1_850,
                isUpperDeck = true),
            SlotConfig(8, "Прицеп — верхний, 2-я позиция",
                platformHeightMm = 2_150, maxCarHeightMm = 1_850,
                isUpperDeck = true),
        )
    }

    // ─── Главная функция ──────────────────────────────────────────────────────

    /**
     * Строит план погрузки для списка автомобилей (1..8).
     * Никогда не бросает исключение — все ошибочные состояния возвращаются
     * через warnings.
     */
    fun execute(cars: List<CarSpecs>): LoadPlan {
        require(cars.isNotEmpty()) { "Список автомобилей пуст" }
        require(cars.size <= SLOT_CATALOG.size) {
            "Максимум ${SLOT_CATALOG.size} автомобилей, получено ${cars.size}"
        }

        val algorithmWarnings = mutableListOf<String>()
        val remaining = cars.toMutableList()
        // slotNumber → (car, reason)
        val assignments = LinkedHashMap<Int, Pair<CarSpecs, String>>(SLOT_CATALOG.size)

        // ── 1. Слот 3: над кабиной — жёсткий лимит высоты ────────────────────
        val cfg3 = slotById(3)
        val best3 = remaining
            .filter { it.heightMm <= cfg3.maxCarHeightMm }
            .minByOrNull { it.heightMm * 10 + it.weightKg / 100 } // приоритет: ниже, потом легче

        if (best3 != null) {
            assign(assignments, cfg3.number, best3,
                "Самое низкое авто (${best3.heightMm} мм) для позиции над кабиной 4×4 " +
                "(лимит ${cfg3.maxCarHeightMm} мм)")
            remaining.remove(best3)
        } else {
            val shortest = remaining.minByOrNull { it.heightMm }!!
            assign(assignments, cfg3.number, shortest,
                "⚠ ВЫНУЖДЕННОЕ: ни одно авто ≤ ${cfg3.maxCarHeightMm} мм — назначено наименьшее")
            remaining.remove(shortest)
            algorithmWarnings.add(
                "Слот 3: ни одно авто не вписывается в лимит ${cfg3.maxCarHeightMm} мм. " +
                "Проверьте высоты авто.")
        }

        // ── 2. Верхний ярус прицепа (7, 8): лёгкие и низкие ─────────────────
        for (slotNum in listOf(7, 8)) {
            if (remaining.isEmpty()) break
            val cfg = slotById(slotNum)
            val candidate = remaining
                .filter { it.heightMm <= cfg.maxCarHeightMm }
                .minByOrNull { it.weightKg }
                ?: run {
                    algorithmWarnings.add("Слот $slotNum: нет авто с высотой ≤ ${cfg.maxCarHeightMm} мм.")
                    remaining.minByOrNull { it.heightMm }!!
                }
            assign(assignments, slotNum, candidate,
                "Верхний ярус прицепа — наилегчайшее подходящее авто (${candidate.weightKg} кг)")
            remaining.remove(candidate)
        }

        // ── 3. Нижние слоты: тяжёлые к осям ─────────────────────────────────
        // Приоритет осей по нагрузке (центр масс → ближе к осям прицепа):
        // 4 (передняя ось прицепа) → 5 (задняя ось прицепа) →
        // 1 (передняя тягача) → 2 (задняя тягача) → 6 (зад прицепа, консоль)
        for (slotNum in listOf(4, 5, 1, 2, 6)) {
            if (remaining.isEmpty()) break
            val cfg = slotById(slotNum)
            val candidate = remaining
                .filter { it.heightMm <= cfg.maxCarHeightMm }
                .maxByOrNull { it.weightKg }
                ?: remaining.maxByOrNull { it.weightKg }!!
            assign(assignments, slotNum, candidate,
                "Нижний ярус — тяжёлое авто (${candidate.weightKg} кг) к оси для стабильности")
            remaining.remove(candidate)
        }

        // ── 4. Сборка SlotAssignment ──────────────────────────────────────────
        val slotAssignments = SLOT_CATALOG.mapNotNull { cfg ->
            val (car, reason) = assignments[cfg.number] ?: return@mapNotNull null
            val total = cfg.platformHeightMm + car.heightMm
            val status = computeStatus(car, cfg, total)
            SlotAssignment(cfg, car, total, status, reason)
        }

        val emptySlots = SLOT_CATALOG.filter { it.number !in assignments }

        // ── 5. Проверка суммарной высоты автопоезда ───────────────────────────
        val actualMaxHeight = slotAssignments.maxOfOrNull { it.totalHeightMm } ?: 0
        if (actualMaxHeight > MAX_TOTAL_HEIGHT_MM) {
            algorithmWarnings.add(
                "НЕГАБАРИТ: максимальная высота автопоезда $actualMaxHeight мм " +
                "превышает допустимые $MAX_TOTAL_HEIGHT_MM мм.")
        }

        // ── 6. Нагрузка по осям с проверкой ──────────────────────────────────
        fun kg(vararg nums: Int) = nums.sumOf { n ->
            assignments[n]?.first?.weightKg ?: 0
        }

        val axleLoads = AxleLoads(
            tractorFrontAxleKg = kg(1, 3),
            tractorRearAxleKg  = kg(2),
            trailerFrontAxleKg = kg(4, 7),
            trailerRearAxleKg  = kg(5, 6, 8)
        )

        val overaxleWarnings = mutableListOf<String>()
        checkAxle(axleLoads.tractorFrontAxleKg,  MAX_TRACTOR_FRONT_KG,
            "Передняя ось тягача", overaxleWarnings)
        checkAxle(axleLoads.tractorRearAxleKg,   MAX_TRACTOR_REAR_KG,
            "Задняя ось тягача", overaxleWarnings)
        checkAxle(axleLoads.trailerFrontAxleKg,  MAX_TRAILER_PER_AXLE_KG,
            "Передняя ось прицепа", overaxleWarnings)
        checkAxle(axleLoads.trailerRearAxleKg,   MAX_TRAILER_PER_AXLE_KG,
            "Задняя ось прицепа", overaxleWarnings)
        checkAxle(axleLoads.tractorTotalKg,      MAX_TRACTOR_TOTAL_KG,
            "Суммарная нагрузка тягача", overaxleWarnings)
        checkAxle(axleLoads.trailerTotalKg,      MAX_TRAILER_TOTAL_KG,
            "Суммарная нагрузка прицепа", overaxleWarnings)

        return LoadPlan(
            assignments           = slotAssignments,
            emptySlots            = emptySlots,
            totalCargoWeightKg    = cars.sumOf { it.weightKg },
            axleLoads             = axleLoads,
            maxTotalHeightMm      = actualMaxHeight,
            hasOversize           = slotAssignments.any { it.status == SlotStatus.OVERSIZE },
            overaxleWarnings      = overaxleWarnings,
            algorithmWarnings     = algorithmWarnings
        )
    }

    // ─── Вспомогательные ──────────────────────────────────────────────────────

    private fun slotById(n: Int) = SLOT_CATALOG.first { it.number == n }

    private fun assign(map: MutableMap<Int, Pair<CarSpecs, String>>,
                       slot: Int, car: CarSpecs, reason: String) {
        map[slot] = car to reason
    }

    private fun computeStatus(car: CarSpecs, cfg: SlotConfig, total: Int): SlotStatus = when {
        car.heightMm > cfg.maxCarHeightMm     -> SlotStatus.OVERSIZE
        total > MAX_TOTAL_HEIGHT_MM           -> SlotStatus.OVERSIZE
        total > MAX_TOTAL_HEIGHT_MM - WARNING_MARGIN_MM -> SlotStatus.WARNING_CLOSE
        else                                  -> SlotStatus.OK
    }

    private fun checkAxle(actual: Int, limit: Int, label: String,
                          warnings: MutableList<String>) {
        if (actual > limit)
            warnings.add("$label: $actual кг > допустимых $limit кг (перегруз ${actual - limit} кг)")
    }
}
