package com.mejdi14.kmpstartupmvp.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailFormValidationTest {
    @Test fun emailRules() {
        assertEquals("Email is required", EmailFormValidation.email(""))
        assertEquals("Invalid email", EmailFormValidation.email("nope"))
        assertEquals("Invalid email", EmailFormValidation.email("a@b"))
        assertEquals("Invalid email", EmailFormValidation.email("a b@c.com"))
        assertNull(EmailFormValidation.email("a@b.co"))
    }

    @Test fun passwordRules() {
        assertEquals("Password is required", EmailFormValidation.password(""))
        assertEquals("Password must be at least 6 characters long", EmailFormValidation.password("12345"))
        assertNull(EmailFormValidation.password("123456"))
    }

    @Test fun confirmRules() {
        assertEquals("Password confirmation is required", EmailFormValidation.confirmPassword("123456", ""))
        assertEquals("Passwords do not match", EmailFormValidation.confirmPassword("123456", "1234567"))
        assertNull(EmailFormValidation.confirmPassword("123456", "123456"))
    }
}
