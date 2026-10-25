package com.hisbaan.notiq.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFilterTest {
    private val filter = CodeFilter()
    private val now = 1_000_000_000L

    private fun message(key: String = "sms", time: Long = now) = Message(key, "pkg", "App", null, "text", time)

    @Test
    fun `new code is accepted once`() {
        assertTrue(filter.accept(message(), "482913", now))
        assertFalse(filter.accept(message(), "482913", now + 1_000))
    }

    @Test
    fun `re-post of the same message is ignored`() {
        assertTrue(filter.accept(message(time = now - 5_000), "482913", now))
        // Same conversation re-posted 3 min later (past the dedupe window), same newest message.
        assertFalse(filter.accept(message(time = now - 5_000), "482913", now + 3 * 60_000))
    }

    @Test
    fun `same code from another source within the window is ignored`() {
        assertTrue(filter.accept(message(key = "sms"), "482913", now))
        assertFalse(filter.accept(message(key = "email", time = now + 10_000), "482913", now + 10_000))
    }

    @Test
    fun `same code again after the window is accepted`() {
        assertTrue(filter.accept(message(), "482913", now))
        val later = now + 3 * 60_000
        assertTrue(filter.accept(message(time = later), "482913", later))
    }

    @Test
    fun `newer message in the same conversation is accepted`() {
        assertTrue(filter.accept(message(), "482913", now))
        assertTrue(filter.accept(message(time = now + 30_000), "771204", now + 30_000))
    }

    @Test
    fun `old messages are ignored`() {
        assertFalse(filter.accept(message(time = now - 11 * 60_000), "482913", now))
    }
}
