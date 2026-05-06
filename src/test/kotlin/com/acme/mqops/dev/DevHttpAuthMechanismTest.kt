package com.acme.mqops.dev

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevHttpAuthMechanismTest {
    @Test
    fun `does not require identity provider credential types`() {
        assertTrue(DevHttpAuthMechanism().credentialTypes.isEmpty())
    }
}
