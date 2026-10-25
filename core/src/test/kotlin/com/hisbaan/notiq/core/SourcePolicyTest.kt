package com.hisbaan.notiq.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePolicyTest {
    private val messages = "com.google.android.apps.messaging"
    private val gmail = "com.google.android.gm"
    private val bank = "com.example.bank"

    @Test
    fun `defaults take messaging apps and anything the system flags`() {
        val policy = SourcePolicy()
        assertTrue(policy.accepts(messages, systemFlagged = false))
        assertTrue(policy.accepts(bank, systemFlagged = true))
        assertFalse(policy.accepts(bank, systemFlagged = false))
        assertFalse(policy.accepts(gmail, systemFlagged = null))
    }

    @Test
    fun `email apps when enabled`() {
        assertTrue(SourcePolicy(emailApps = true).accepts(gmail, systemFlagged = false))
    }

    @Test
    fun `all apps`() {
        assertTrue(SourcePolicy(allApps = true).accepts(bank, systemFlagged = null))
    }

    @Test
    fun `system flag ignored when that source is off`() {
        assertFalse(SourcePolicy(systemFlagged = false).accepts(bank, systemFlagged = true))
    }
}
