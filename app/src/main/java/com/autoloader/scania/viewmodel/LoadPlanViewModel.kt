package com.autoloader.scania.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoloader.scania.domain.CarQueryValidator
import com.autoloader.scania.domain.DistributeLoadUseCase
import com.autoloader.scania.model.FetchResult
import com.autoloader.scania.model.UiState
import com.autoloader.scania.repository.CarSpecsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LoadPlanViewModel : ViewModel() {

    // Зависимости создаются здесь (без DI) — один экземпляр на весь ViewModel
    private val validator    = CarQueryValidator()
    private val repository   = CarSpecsRepository()
    private val distributeUC = DistributeLoadUseCase()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null
    // Mutex защищает от одновременного обновления прогресса из разных корутин
    private val progressMutex = Mutex()
    private var completedCount = 0

    // ─── Публичные методы ─────────────────────────────────────────────────────

    fun calculate(rawInputs: List<String>) {
        // 1. Валидация
        val batch = validator.validate(rawInputs)

        if (batch.isEmpty) {
            _uiState.value = UiState.Failure("Введите хотя бы один автомобиль")
            return
        }

        if (batch.hasErrors) {
            val msg = batch.invalidEntries.joinToString("\n") { entry ->
                "Авто ${entry.index + 1}: ${entry.reason}"
            }
            _uiState.value = UiState.Failure("Исправьте ошибки ввода:\n$msg")
            return
        }

        val queries = batch.validQueries
        if (queries.size > CarQueryValidator.MAX_CARS) {
            _uiState.value = UiState.Failure("Максимум ${CarQueryValidator.MAX_CARS} автомобилей")
            return
        }

        // 2. Отмена предыдущего расчёта
        currentJob?.cancel()
        completedCount = 0

        currentJob = viewModelScope.launch {
            _uiState.value = UiState.Loading(0, queries.size)

            // 3. Параллельная загрузка характеристик
            val deferreds = queries.map { query ->
                async {
                    val result = repository.fetch(query.rawInput)
                    progressMutex.withLock {
                        completedCount++
                        _uiState.value = UiState.Loading(completedCount, queries.size)
                    }
                    result
                }
            }

            val results = runCatching { deferreds.awaitAll() }.getOrElse { e ->
                _uiState.value = UiState.Failure("Ошибка загрузки: ${e.message}")
                return@launch
            }

            val successes = results.filterIsInstance<FetchResult.Success>()
            val errors    = results.filterIsInstance<FetchResult.Error>()

            // 4. Если вообще нет данных — полный провал
            if (successes.isEmpty()) {
                _uiState.value = UiState.Failure(
                    buildString {
                        appendLine("Не удалось получить данные:")
                        errors.forEach { appendLine("• ${it.query}: ${it.message}") }
                    }
                )
                return@launch
            }

            // 5. Строим план (частичный успех: errors идут в fetchErrors)
            val plan = runCatching {
                distributeUC.execute(successes.map { it.specs })
            }.getOrElse { e ->
                _uiState.value = UiState.Failure("Ошибка алгоритма: ${e.message}")
                return@launch
            }

            _uiState.update {
                UiState.Result(plan = plan, fetchErrors = errors)
            }
        }
    }

    fun reset() {
        currentJob?.cancel()
        completedCount = 0
        _uiState.value = UiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}
