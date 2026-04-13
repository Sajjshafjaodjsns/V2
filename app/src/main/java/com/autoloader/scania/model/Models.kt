package com.autoloader.scania.model

// ═══════════════════════════════════════════════════════════════
// ВХОДНЫЕ ДАННЫЕ
// ═══════════════════════════════════════════════════════════════

/**
 * Введённый пользователем запрос.
 * Валидация — в CarQueryValidator, здесь только структура.
 */
data class CarQuery(
    val index: Int,       // 0..7, позиция в UI-списке
    val rawInput: String  // «Toyota Camry 2020»
)

/** Результат валидации одного запроса. */
sealed class ValidationResult {
    data class Valid(val query: CarQuery) : ValidationResult()
    data class Invalid(val index: Int, val reason: String) : ValidationResult()
}

// ═══════════════════════════════════════════════════════════════
// ХАРАКТЕРИСТИКИ АВТОМОБИЛЯ
// ═══════════════════════════════════════════════════════════════

data class CarSpecs(
    val query: String,
    val displayName: String,
    val weightKg: Int,    // снаряжённая масса, кг  (>= 500)
    val heightMm: Int,    // высота кузова, мм       (>= 1000)
    val lengthMm: Int,    // длина кузова, мм        (>= 2000)
    val source: SpecSource
) {
    init {
        require(weightKg >= 500)  { "Нереальная масса: $weightKg кг" }
        require(heightMm >= 800)  { "Нереальная высота: $heightMm мм" }
        require(lengthMm >= 2000) { "Нереальная длина: $lengthMm мм" }
    }
}

enum class SpecSource {
    NETWORK,      // получено с сайта-каталога
    FALLBACK_DB,  // из встроенной базы категорий
    UNKNOWN       // заглушка (не должна использоваться в prod)
}

// ═══════════════════════════════════════════════════════════════
// РЕЗУЛЬТАТ ПОЛУЧЕНИЯ ХАРАКТЕРИСТИК
// ═══════════════════════════════════════════════════════════════

sealed class FetchResult {
    data class Success(val specs: CarSpecs) : FetchResult()
    data class Error(val query: String, val message: String) : FetchResult()
}

// ═══════════════════════════════════════════════════════════════
// ФИЗИЧЕСКАЯ МОДЕЛЬ СЛОТА
//
// Scania P-series 4×4 (2008) + прицеп Uçsuoğlu:
//
//   ТЯГАЧ (3 слота)                   ПРИЦЕП (5 слотов)
//   ┌────────┬────────┐               ┌──────────────┐
//   │  [3]↑  │        │               │  [7]↑  [8]↑  │
//   │  верх  │        │               │  верхний ярус│
//   ├────────┤        │               ├──────────────┤
//   │  [1]   │  [2]   │               │ [4]  [5]  [6]│
//   │  низ   │  низ   │               │  нижний ярус │
//   │  перед │  зад   │               │              │
//   └────────┴────────┘               └──────────────┘
//
// Рама 4×4 выше стандартной на 250 мм из-за переднего ведущего моста.
// Это ПОДНИМАЕТ платформу слота [3] до 2 550 мм от земли,
// из-за чего максимальная высота авто на этой позиции — 1 450 мм
// (4 000 − 2 550 = 1 450).
// Противоречие в предыдущей версии (1 450 vs 1 640) — ИСПРАВЛЕНО:
// везде используется одно значение 1 450, выведенное из физики.
//
// Нагрузка по осям (плечи от CMC прицепа):
//   Ось тягача передняя  ← слоты 1, 3  (над ведущим мостом)
//   Ось тягача задняя    ← слот  2     (над задним мостом)
//   Ось прицепа передняя ← слоты 4, 7  (1/3 длины прицепа)
//   Ось прицепа задняя   ← слоты 5,6,8 (2/3 длины прицепа)
//
// ═══════════════════════════════════════════════════════════════

/** Неизменяемая конфигурация одного слота, определяется физикой установки. */
data class SlotConfig(
    val number: Int,
    val label: String,
    val platformHeightMm: Int,   // высота платформы от земли
    val maxCarHeightMm: Int,     // MAX_TOTAL_HEIGHT − platformHeightMm
    val isUpperDeck: Boolean,
    val isCriticalOverCab: Boolean = false
) {
    /** Максимальная суммарная высота всегда = platformHeightMm + maxCarHeightMm = 4 000. */
    val maxTotalHeightMm: Int get() = platformHeightMm + maxCarHeightMm

    init {
        // Проверяем, что конфигурация самосогласована
        require(maxTotalHeightMm == 4_000) {
            "Слот $number: platform($platformHeightMm) + maxCar($maxCarHeightMm) = " +
            "$maxTotalHeightMm ≠ 4000"
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// РЕЗУЛЬТАТ РАСПРЕДЕЛЕНИЯ
// ═══════════════════════════════════════════════════════════════

data class SlotAssignment(
    val config: SlotConfig,
    val car: CarSpecs,
    val totalHeightMm: Int,   // config.platformHeightMm + car.heightMm
    val status: SlotStatus,
    val reason: String
) {
    val slotNumber: Int get() = config.number
    val platformHeightMm: Int get() = config.platformHeightMm
}

enum class SlotStatus {
    OK,            // высота в норме
    WARNING_CLOSE, // до лимита осталось < 150 мм
    OVERSIZE       // превышает 4 000 мм или лимит слота
}

data class AxleLoads(
    /** Слоты 1, 3 — над передним ведущим мостом тягача */
    val tractorFrontAxleKg: Int,
    /** Слот 2 — над задним мостом тягача */
    val tractorRearAxleKg: Int,
    /** Слоты 4, 7 — передняя ось прицепа */
    val trailerFrontAxleKg: Int,
    /** Слоты 5, 6, 8 — задняя ось прицепа */
    val trailerRearAxleKg: Int
) {
    val tractorTotalKg: Int get() = tractorFrontAxleKg + tractorRearAxleKg
    val trailerTotalKg: Int get() = trailerFrontAxleKg + trailerRearAxleKg
}

data class LoadPlan(
    val assignments: List<SlotAssignment>,
    val emptySlots: List<SlotConfig>,          // слоты без авто
    val totalCargoWeightKg: Int,
    val axleLoads: AxleLoads,
    val maxTotalHeightMm: Int,                 // максимум по всем назначениям
    val hasOversize: Boolean,
    val overaxleWarnings: List<String>,        // перегруз по осям
    val algorithmWarnings: List<String>        // прочие предупреждения алгоритма
) {
    val allWarnings: List<String> get() = overaxleWarnings + algorithmWarnings
}

// ═══════════════════════════════════════════════════════════════
// UI-СОСТОЯНИЕ
// Покрывает частичные состояния: часть загружена, часть — нет
// ═══════════════════════════════════════════════════════════════

sealed class UiState {
    object Idle : UiState()

    data class Loading(
        val completedCount: Int,
        val totalCount: Int
    ) : UiState() {
        val progressPercent: Int get() =
            if (totalCount == 0) 0 else (completedCount * 100) / totalCount
        val message: String get() = "Получение данных: $completedCount / $totalCount"
    }

    /**
     * Частичный успех: некоторые авто не загружены, но план построен.
     * fetchErrors — список авто, для которых данные не получены (использован fallback).
     */
    data class Result(
        val plan: LoadPlan,
        val fetchErrors: List<FetchResult.Error> = emptyList()
    ) : UiState() {
        val hasPartialErrors: Boolean get() = fetchErrors.isNotEmpty()
    }

    /** Полный провал — план построить невозможно. */
    data class Failure(val message: String) : UiState()
}
