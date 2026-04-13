package com.autoloader.scania.domain

import com.autoloader.scania.model.CarQuery
import com.autoloader.scania.model.ValidationResult

/**
 * Use-case: валидация пользовательского ввода перед отправкой в репозиторий.
 * Не зависит ни от Android, ни от сети → легко тестируется.
 */
class CarQueryValidator {

    companion object {
        const val MAX_CARS = 8
        const val MIN_INPUT_LENGTH = 3
        const val MAX_INPUT_LENGTH = 80
        // Минимум одно слово длиной >= 2 (марка)
        private val VALID_PATTERN = Regex("^[\\p{L}\\d][\\p{L}\\d\\s\\-./]{1,79}$")
        // Очевидно некорректные строки
        private val REJECT_PATTERNS = listOf(
            Regex("^\\d+$"),           // только цифры
            Regex("^[^\\p{L}\\d]+$")  // только спецсимволы
        )
    }

    /**
     * Валидирует список сырых строк ввода.
     * @return ValidationBatch с разбивкой на valid/invalid
     */
    fun validate(inputs: List<String>): ValidationBatch {
        val results = inputs.mapIndexed { index, raw ->
            validateSingle(index, raw)
        }
        return ValidationBatch(results)
    }

    private fun validateSingle(index: Int, raw: String): ValidationResult {
        val trimmed = raw.trim()

        if (trimmed.isBlank()) {
            // Пустое поле — не ошибка, просто пропускаем
            return ValidationResult.Invalid(index, "")
        }
        if (trimmed.length < MIN_INPUT_LENGTH) {
            return ValidationResult.Invalid(index,
                "Слишком короткий запрос (мин. $MIN_INPUT_LENGTH символа)")
        }
        if (trimmed.length > MAX_INPUT_LENGTH) {
            return ValidationResult.Invalid(index,
                "Слишком длинный запрос (макс. $MAX_INPUT_LENGTH символов)")
        }
        if (REJECT_PATTERNS.any { it.matches(trimmed) }) {
            return ValidationResult.Invalid(index,
                "Введите марку и модель автомобиля")
        }

        return ValidationResult.Valid(CarQuery(index, trimmed))
    }
}

/** Результат валидации всего пакета. */
data class ValidationBatch(
    val results: List<ValidationResult>
) {
    val validQueries: List<CarQuery> =
        results.filterIsInstance<ValidationResult.Valid>().map { it.query }

    val invalidEntries: List<ValidationResult.Invalid> =
        results.filterIsInstance<ValidationResult.Invalid>()
            .filter { it.reason.isNotBlank() } // пустые поля не считаем ошибкой

    val hasErrors: Boolean get() = invalidEntries.isNotEmpty()
    val isEmpty: Boolean get() = validQueries.isEmpty()
}
