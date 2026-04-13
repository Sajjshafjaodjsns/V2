package com.autoloader.scania.domain

import com.autoloader.scania.model.CarSpecs
import com.autoloader.scania.model.SlotStatus
import com.autoloader.scania.model.SpecSource
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DistributeLoadUseCaseTest {

    private lateinit var useCase: DistributeLoadUseCase

    // ── Тестовые авто ────────────────────────────────────────────────────────

    /** Маленький хэтчбек: 1440 мм высота — подходит для слота 3 */
    private val smallHatch = car("Golf", 1_380, 1_440, 4_300)

    /** Средний кроссовер */
    private val mediumSuv = car("Tucson", 1_820, 1_680, 4_630)

    /** Высокий минивэн: 1970 мм — не подходит для слота 3 */
    private val tallVan = car("Sprinter", 2_050, 1_970, 5_300)

    /** Авто с высотой ровно на границе слота 3 (1450 мм) */
    private val exactLimitCar = car("Exact", 1_500, 1_450, 4_500)

    /** Авто на 1 мм выше лимита слота 3 */
    private val overLimitCar = car("TooTall", 1_500, 1_451, 4_500)

    @Before
    fun setUp() { useCase = DistributeLoadUseCase() }

    // ── 1. Конфигурация слотов самосогласована ────────────────────────────────

    @Test
    fun `slot catalog: all slots sum to exactly 4000mm`() {
        DistributeLoadUseCase.SLOT_CATALOG.forEach { cfg ->
            assertEquals(
                "Слот ${cfg.number}: ${cfg.platformHeightMm} + ${cfg.maxCarHeightMm} ≠ 4000",
                4_000,
                cfg.platformHeightMm + cfg.maxCarHeightMm
            )
        }
    }

    @Test
    fun `slot 3 max car height is exactly 1450mm`() {
        val slot3 = DistributeLoadUseCase.SLOT_CATALOG.first { it.number == 3 }
        assertEquals(1_450, slot3.maxCarHeightMm)
        assertEquals(2_550, slot3.platformHeightMm)
        assertTrue(slot3.isCriticalOverCab)
    }

    // ── 2. Базовое распределение ──────────────────────────────────────────────

    @Test
    fun `single car is assigned to a slot`() {
        val plan = useCase.execute(listOf(smallHatch))
        assertEquals(1, plan.assignments.size)
        assertEquals(1, plan.emptySlots.size + plan.assignments.size -
            (DistributeLoadUseCase.SLOT_CATALOG.size - 1))
    }

    @Test
    fun `slot 3 gets the lowest car`() {
        val cars = listOf(smallHatch, mediumSuv, tallVan)
        val plan = useCase.execute(cars)

        val slot3 = plan.assignments.first { it.slotNumber == 3 }
        // Самое низкое из тех, что проходят: smallHatch (1440) < mediumSuv (1680)
        assertEquals("Golf", slot3.car.displayName)
        assertEquals(SlotStatus.OK, slot3.status)
    }

    @Test
    fun `car exactly at slot3 limit is OK, not OVERSIZE`() {
        val plan = useCase.execute(listOf(exactLimitCar))
        val slot3 = plan.assignments.first { it.slotNumber == 3 }
        assertTrue("Высота ${exactLimitCar.heightMm} должна проходить в слот 3",
            slot3.status != SlotStatus.OVERSIZE)
        assertEquals(4_000, slot3.totalHeightMm) // 2550 + 1450 = 4000
    }

    @Test
    fun `car 1mm over slot3 limit becomes OVERSIZE`() {
        val plan = useCase.execute(listOf(overLimitCar))
        val slot3 = plan.assignments.first { it.slotNumber == 3 }
        assertEquals(SlotStatus.OVERSIZE, slot3.status)
        assertTrue(plan.hasOversize)
    }

    @Test
    fun `oversize triggers warning in algorithmWarnings`() {
        // Все авто выше 1450 мм → принудительное назначение в слот 3
        val allTall = List(3) { tallVan.copy(query = "Sprinter$it") }
        val plan = useCase.execute(allTall)
        assertTrue(plan.algorithmWarnings.isNotEmpty())
    }

    // ── 3. Проверка суммарной высоты ─────────────────────────────────────────

    @Test
    fun `maxTotalHeightMm never exceeds 4000 for valid cars`() {
        val cars = listOf(smallHatch, mediumSuv)
        val plan = useCase.execute(cars)
        plan.assignments.forEach { slot ->
            assertTrue(
                "Слот ${slot.slotNumber}: ${slot.totalHeightMm} > 4000",
                slot.totalHeightMm <= 4_000 || slot.status == SlotStatus.OVERSIZE
            )
        }
    }

    @Test
    fun `plan maxTotalHeightMm matches maximum assignment`() {
        val cars = listOf(smallHatch, mediumSuv, tallVan)
        val plan = useCase.execute(cars)
        val expected = plan.assignments.maxOf { it.totalHeightMm }
        assertEquals(expected, plan.maxTotalHeightMm)
    }

    // ── 4. Нагрузка по осям ──────────────────────────────────────────────────

    @Test
    fun `axle loads sum equals total cargo weight`() {
        val cars = List(6) { mediumSuv.copy(query = "Car$it") }
        val plan = useCase.execute(cars)
        val ax = plan.axleLoads
        assertEquals(
            plan.totalCargoWeightKg,
            ax.tractorTotalKg + ax.trailerTotalKg
        )
    }

    @Test
    fun `heavy overload triggers overaxle warning`() {
        // 8 тяжёлых Sprinter → точно превысит лимиты осей
        val cars = List(8) { tallVan.copy(query = "Van$it") }
        val plan = useCase.execute(cars)
        assertTrue("Ожидались предупреждения перегруза осей",
            plan.overaxleWarnings.isNotEmpty())
    }

    @Test
    fun `empty slots reported correctly`() {
        val plan = useCase.execute(listOf(smallHatch)) // 1 авто из 8
        assertEquals(7, plan.emptySlots.size)
    }

    // ── 5. Граничные случаи ───────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `more than 8 cars throws`() {
        useCase.execute(List(9) { smallHatch })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty list throws`() {
        useCase.execute(emptyList())
    }

    @Test
    fun `8 cars all assigned`() {
        val cars = List(8) { mediumSuv.copy(query = "Car$it") }
        val plan = useCase.execute(cars)
        assertEquals(8, plan.assignments.size)
        assertTrue(plan.emptySlots.isEmpty())
    }

    @Test
    fun `totalCargoWeightKg is sum of all cars`() {
        val cars = listOf(smallHatch, mediumSuv, tallVan)
        val plan = useCase.execute(cars)
        assertEquals(
            smallHatch.weightKg + mediumSuv.weightKg + tallVan.weightKg,
            plan.totalCargoWeightKg
        )
    }

    // ── Утилита ───────────────────────────────────────────────────────────────

    private fun car(name: String, wKg: Int, hMm: Int, lMm: Int) = CarSpecs(
        query = name, displayName = name,
        weightKg = wKg, heightMm = hMm, lengthMm = lMm,
        source = SpecSource.FALLBACK_DB
    )
}
