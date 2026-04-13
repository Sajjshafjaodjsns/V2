package com.autoloader.scania.ui
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Color
import android.content.Context

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.*
import androidx.core.content.ContextCompat
import com.autoloader.scania.R
import com.autoloader.scania.model.*

/**
 * Строит View-дерево результата. Не зависит от Activity.
 * Принимает UiState.Result целиком — видит fetchErrors и plan.
 */
class ResultRenderer(private val ctx: Context) {

    fun render(state: UiState.Result, container: LinearLayout) {
        val plan = state.plan

        // 1. Частичные ошибки загрузки (если часть авто не найдена)
        if (state.hasPartialErrors) {
            container.addView(buildBanner(
                icon = "⚠",
                title = "Часть данных получена из встроенной базы",
                lines = state.fetchErrors.map { "• ${it.query}" },
                bgRes = R.drawable.bg_warning,
                textColorRes = R.color.text_warning
            ))
        }

        // 2. Предупреждения алгоритма
        if (plan.algorithmWarnings.isNotEmpty()) {
            container.addView(buildBanner(
                icon = "⚠",
                title = "Предупреждения алгоритма",
                lines = plan.algorithmWarnings,
                bgRes = R.drawable.bg_warning,
                textColorRes = R.color.text_warning
            ))
        }

        // 3. Перегруз осей — отдельный критичный блок
        if (plan.overaxleWarnings.isNotEmpty()) {
            container.addView(buildBanner(
                icon = "⛔",
                title = "Перегруз осей!",
                lines = plan.overaxleWarnings,
                bgRes = R.drawable.bg_card_error,
                textColorRes = R.color.status_error
            ))
        }

        // 4. Карточки слотов
        plan.assignments.forEach { slot ->
            container.addView(buildSlotCard(slot))
        }

        // 5. Пустые слоты (если авто меньше 8)
        if (plan.emptySlots.isNotEmpty()) {
            container.addView(buildEmptySlotsNote(plan.emptySlots))
        }

        // 6. Нагрузка по осям
        container.addView(buildAxleCard(plan.axleLoads))

        // 7. Итог
        container.addView(buildSummaryCard(plan))
    }

    // ─── Баннер (предупреждения / ошибки) ────────────────────────────────────

    private fun buildBanner(
        icon: String, title: String, lines: List<String>,
        bgRes: Int, textColorRes: Int
    ): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(bgRes)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = lp(bottomMargin = dp(10))
        addView(tv("$icon  $title", 14f, bold = true, colorRes = textColorRes))
        lines.forEach { line ->
            addView(tv(line, 12f, colorRes = textColorRes, topPad = dp(3)))
        }
    }

    // ─── Карточка слота ──────────────────────────────────────────────────────

    private fun buildSlotCard(slot: SlotAssignment): LinearLayout {
        val (bgRes, statusIcon, statusColorRes) = when (slot.status) {
            SlotStatus.OK            -> Triple(R.drawable.bg_card_ok,      "✓", R.color.status_ok)
            SlotStatus.WARNING_CLOSE -> Triple(R.drawable.bg_card_warning, "⚠", R.color.status_warning)
            SlotStatus.OVERSIZE      -> Triple(R.drawable.bg_card_error,   "⛔", R.color.status_error)
        }

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(bgRes)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = lp(bottomMargin = dp(8))

            // Строка 1: бейдж + имя + статус
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(tv("${slot.slotNumber}", 11f, bold = true,
                    colorRes = R.color.text_slot_number).apply {
                    setBackgroundResource(R.drawable.bg_slot_badge)
                    setPadding(dp(9), dp(3), dp(9), dp(3))
                    gravity = Gravity.CENTER
                    minWidth = dp(28)
                })
                addView(tv(slot.car.displayName, 15f, bold = true,
                    colorRes = R.color.text_primary).apply {
                    layoutParams = LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(10)
                    }
                })
                addView(tv(statusIcon, 20f, bold = true, colorRes = statusColorRes))
            })

            // Строка 2: описание позиции
            addView(tv(slot.config.label, 12f,
                colorRes = R.color.text_secondary, topPad = dp(5)))

            // Строка 3: масса, высота, длина, источник
            val srcLabel = when (slot.car.source) {
                SpecSource.NETWORK     -> "сеть ✓"
                SpecSource.FALLBACK_DB -> "база ~"
                SpecSource.UNKNOWN     -> "?"
            }
            addView(tv(
                "${slot.car.weightKg} кг  ·  выс. ${slot.car.heightMm} мм  ·  " +
                "дл. ${slot.car.lengthMm} мм  [$srcLabel]",
                12f, colorRes = R.color.text_secondary, topPad = dp(3)))

            // Строка 4: высота с платформой → итог
            addView(tv(
                "Платф. ${slot.platformHeightMm} + авто ${slot.car.heightMm} " +
                "= ${slot.totalHeightMm} мм  /  4 000 мм",
                13f, bold = true, colorRes = statusColorRes, topPad = dp(5)))

            // Строка 5: обоснование
            addView(tv("↳ ${slot.reason}", 11f,
                colorRes = R.color.text_hint, topPad = dp(3)))
        }
    }

    // ─── Пустые слоты ────────────────────────────────────────────────────────

    private fun buildEmptySlotsNote(empty: List<SlotConfig>): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_neutral)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = lp(bottomMargin = dp(8))
            addView(tv("Незаполненные слоты (${empty.size}):", 13f,
                colorRes = R.color.text_secondary))
            empty.forEach { cfg ->
                addView(tv("  ${cfg.number}. ${cfg.label}", 12f,
                    colorRes = R.color.text_hint, topPad = dp(2)))
            }
        }

    // ─── Нагрузка по осям ────────────────────────────────────────────────────

    private fun buildAxleCard(ax: AxleLoads): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_neutral)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = lp(topMargin = dp(4), bottomMargin = dp(8))

            addView(tv("Нагрузка по осям", 14f, bold = true, colorRes = R.color.text_primary))
            addView(divider())

            data class AxleRow(val label: String, val kg: Int, val limit: Int)
            listOf(
                AxleRow("Перед. ось тягача (сл. 1+3)",   ax.tractorFrontAxleKg,  7_100),
                AxleRow("Зад. ось тягача (сл. 2)",       ax.tractorRearAxleKg,   11_500),
                AxleRow("Тягач итого",                   ax.tractorTotalKg,      11_500),
                AxleRow("Перед. ось прицепа (сл. 4+7)", ax.trailerFrontAxleKg,  10_000),
                AxleRow("Зад. ось прицепа (сл. 5+6+8)", ax.trailerRearAxleKg,   10_000),
                AxleRow("Прицеп итого",                  ax.trailerTotalKg,      18_000),
            ).forEach { row ->
                val over = row.kg > row.limit
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = lp(topMargin = dp(4))
                    addView(tv(row.label, 12f,
                        colorRes = R.color.text_secondary).apply {
                        layoutParams = LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(tv(
                        "${row.kg} / ${row.limit} кг",
                        12f, bold = over,
                        colorRes = if (over) R.color.status_error else R.color.text_primary))
                })
            }
        }

    // ─── Итоговая карточка ────────────────────────────────────────────────────

    private fun buildSummaryCard(plan: LoadPlan): LinearLayout {
        val ok = !plan.hasOversize && plan.overaxleWarnings.isEmpty()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (ok) R.drawable.bg_card_ok else R.drawable.bg_card_error)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = lp(topMargin = dp(4))

            addView(tv(
                if (ok) "✓  Погрузка безопасна" else "⛔  Требуется корректировка",
                17f, bold = true,
                colorRes = if (ok) R.color.status_ok else R.color.status_error))
            addView(tv("Груз: ${plan.totalCargoWeightKg} кг", 13f,
                colorRes = R.color.text_primary, topPad = dp(6)))
            addView(tv("Макс. высота: ${plan.maxTotalHeightMm} мм / 4 000 мм", 13f,
                colorRes = if (plan.hasOversize) R.color.status_error else R.color.text_primary,
                topPad = dp(3)))
        }
    }

    // ─── Утилиты ─────────────────────────────────────────────────────────────

    private fun tv(
        text: String, sizeSp: Float, bold: Boolean = false,
        colorRes: Int = R.color.text_primary, topPad: Int = 0
    ) = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(ContextCompat.getColor(ctx, colorRes))
        if (bold) setTypeface(null, Typeface.BOLD)
        if (topPad > 0) setPadding(0, topPad, 0, 0)
    }

    private fun divider() = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(8) }
        setBackgroundColor(ContextCompat.getColor(ctx, R.color.card_neutral_border))
    }

    private fun lp(topMargin: Int = 0, bottomMargin: Int = 0) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin    = topMargin
            this.bottomMargin = bottomMargin
        }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
