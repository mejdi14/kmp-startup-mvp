package com.mejdi14.kmpstartupmvp.metadata

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProfileValidationTest {
    @Test
    fun avatarUrl() {
        assertNull(ProfileValidation.avatarUrl(""))
        assertNull(ProfileValidation.avatarUrl("https://cdn.example.com/a.png"))
        assertNotNull(ProfileValidation.avatarUrl("not a url"))
        assertNotNull(ProfileValidation.avatarUrl("ftp://example.com/a.png"))
    }

    @Test
    fun fullNameLength() {
        assertNull(ProfileValidation.fullName("Ada"))
        assertNotNull(ProfileValidation.fullName("x".repeat(101)))
    }
}
