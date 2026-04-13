package com.autoloader.scania.domain

import com.autoloader.scania.model.ValidationResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CarQueryValidatorTest {

    private lateinit var validator: CarQueryValidator

    @Before fun setUp() { validator = CarQueryValidator() }

    @Test fun `valid input passes`() {
        val batch = validator.validate(listOf("Toyota Camry 2020"))
        assertEquals(1, batch.validQueries.size)
        assertFalse(batch.hasErrors)
    }

    @Test fun `blank input is not an error, just skipped`() {
        val batch = validator.validate(listOf("   ", "", "\t"))
        assertTrue(batch.isEmpty)
        assertFalse(batch.hasErrors)
    }

    @Test fun `too short input is invalid`() {
        val batch = validator.validate(listOf("ab"))
        assertTrue(batch.hasErrors)
        assertTrue(batch.validQueries.isEmpty())
    }

    @Test fun `digits only rejected`() {
        val batch = validator.validate(listOf("12345"))
        assertTrue(batch.hasErrors)
    }

    @Test fun `mixed valid and blank`() {
        val batch = validator.validate(listOf("Toyota Camry 2020", "", "BMW X5 2019"))
        assertEquals(2, batch.validQueries.size)
        assertFalse(batch.hasErrors)
    }

    @Test fun `index preserved in valid query`() {
        val batch = validator.validate(listOf("", "Honda Civic 2021"))
        assertEquals(1, batch.validQueries.first().index)
    }

    @Test fun `cyrillic brand name accepted`() {
        // Некоторые пользователи могут писать по-русски
        val batch = validator.validate(listOf("Лада Гранта 2022"))
        assertEquals(1, batch.validQueries.size)
    }
}
