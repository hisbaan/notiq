package com.hisbaan.notiq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {
    private fun code(text: String) = OtpExtractor.extract(text)

    @Test
    fun `code after keyword`() {
        assertEquals("482913", code("Your verification code is 482913"))
        assertEquals("4829", code("Your Uber code: 4829. Never share this code."))
    }

    @Test
    fun `code before keyword`() {
        assertEquals("482913", code("482913 is your Instagram code. Don't share it."))
    }

    @Test
    fun `prefixed and grouped codes`() {
        assertEquals("482913", code("G-482913 is your Google verification code."))
        assertEquals("482913", code("Your code: 482-913"))
        assertEquals("482913", code("Your login code is 482 913"))
    }

    @Test
    fun `compound and non-English keywords`() {
        assertEquals("482913", code("Ihr Bestätigungscode lautet 482913"))
        assertEquals("482913", code("Tu código de verificación es 482913"))
    }

    @Test
    fun `alphanumeric code`() {
        assertEquals("AB12CD", code("Your one-time passcode is AB12CD. It expires in 10 minutes."))
    }

    @Test
    fun `ignores times dates phone numbers and origin-bound suffix`() {
        assertEquals("482913", code("Your code is 482913. It expires at 10:30 on 2026-10-25."))
        assertEquals(
            "48291355",
            code("Chase: Your one-time code is 48291355. Call 1-800-935-9935 if you didn't request it."),
        )
        assertEquals("482913", code("Your verification code is 482913\n\n@example.com #482913"))
    }

    @Test
    fun `year-like code still found when it is the only candidate`() {
        assertEquals("2025", code("Your code is 2025"))
    }

    @Test
    fun `no keyword, no code`() {
        assertNull(code("Meeting at 10:30 in room 4021"))
        assertNull(code("Your payment of 1500 was received"))
    }

    @Test
    fun `rejects order numbers, amounts, promo and zip codes`() {
        assertNull(code("Your order #482913 has shipped. Tracking code to follow."))
        assertNull(code("Use promo code SAVE20 for 20% off"))
        assertNull(code("Your zip code is 94107"))
        assertNull(code("Verification of your payment of $1234 is complete"))
    }

    @Test
    fun `rejects a phone number near a keyword`() {
        assertNull(code("Call me at 555 123 4567 about the code review"))
    }

    @Test
    fun `rejects numbers too long to be codes`() {
        assertNull(code("Your verification reference is 123456789012"))
    }
}
