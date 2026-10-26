package com.hisbaan.notiq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFieldDetectorTest {
    private fun assess(field: FieldInfo, siblings: List<FieldInfo> = listOf(field), screen: ScreenInfo = ScreenInfo(), length: Int = 6) =
        CodeFieldDetector.assess(field, siblings, screen, length)

    @Test
    fun `labelled numeric code field is sure`() {
        assertEquals(Confidence.SURE, assess(FieldInfo(hint = "Verification code", numeric = true)).confidence)
        assertEquals(Confidence.SURE, assess(FieldInfo(viewId = "com.example:id/otp_input", maxLength = 6)).confidence)
        assertEquals(Confidence.SURE, assess(FieldInfo(viewId = "com.example:id/smsCodeField", numeric = true)).confidence)
    }

    @Test
    fun `code words alone are likely`() {
        assertEquals(Confidence.LIKELY, assess(FieldInfo(hint = "Enter code")).confidence)
        assertEquals(Confidence.LIKELY, assess(FieldInfo(label = "One-time password")).confidence)
    }

    @Test
    fun `split boxes are sure`() {
        val box = FieldInfo(numeric = true, maxLength = 1)
        val result = assess(box, List(6) { box })
        assertEquals(Confidence.SURE, result.confidence)
        assertTrue(result.split)
    }

    @Test
    fun `boxes of unknown length need the screen to mention a code`() {
        val box = FieldInfo(numeric = true)
        assertEquals(false, assess(box, List(6) { box }).split)
        val screen = ScreenInfo("Enter the code we sent to your phone", editableCount = 6)
        assertTrue(assess(box, List(6) { box }, screen).split)
    }

    @Test
    fun `boxes not matching the code length are not split`() {
        val box = FieldInfo(numeric = true, maxLength = 1)
        assertEquals(false, assess(box, List(4) { box }).split)
    }

    @Test
    fun `numeric field of the code's length is likely`() {
        assertEquals(Confidence.LIKELY, assess(FieldInfo(numeric = true, maxLength = 6)).confidence)
    }

    @Test
    fun `screen text tips a numeric or lone field`() {
        val screen = ScreenInfo("We sent a code to •••• 5608", editableCount = 1)
        assertEquals(Confidence.LIKELY, assess(FieldInfo(), screen = screen).confidence)
        val twoFields = ScreenInfo("Two-step verification", editableCount = 2)
        assertEquals(Confidence.LIKELY, assess(FieldInfo(numeric = true), screen = twoFields).confidence)
        assertEquals(Confidence.NONE, assess(FieldInfo(), screen = twoFields).confidence)
    }

    @Test
    fun `password and other fields are not code fields`() {
        val screen = ScreenInfo("Enter your verification code", editableCount = 1)
        assertEquals(Confidence.NONE, assess(FieldInfo(hint = "Password", password = true), screen = screen).confidence)
        assertEquals(Confidence.NONE, assess(FieldInfo(password = true), screen = screen).confidence)
        assertEquals(Confidence.NONE, assess(FieldInfo(hint = "Message", viewId = "app:id/compose_message_text"), screen = screen).confidence)
        assertEquals(Confidence.NONE, assess(FieldInfo(hint = "Phone number", numeric = true), screen = screen).confidence)
        assertEquals(Confidence.NONE, assess(FieldInfo(hint = "Search")).confidence)
    }

    @Test
    fun `masked code field is still a code field`() {
        assertEquals(Confidence.SURE, assess(FieldInfo(hint = "One-time passcode", password = true, numeric = true)).confidence)
    }

    @Test
    fun `card security code is not a one-time code`() {
        assertEquals(Confidence.NONE, assess(FieldInfo(hint = "Card security code", numeric = true, maxLength = 3), length = 6).confidence)
    }

    @Test
    fun `filled field is left alone`() {
        assertEquals(Confidence.NONE, assess(FieldInfo(hint = "Verification code", text = "482913")).confidence)
    }

    @Test
    fun `unrelated numeric field is nothing`() {
        assertEquals(Confidence.NONE, assess(FieldInfo(hint = "Quantity", numeric = true)).confidence)
    }

    @Test
    fun `fill modes`() {
        assertEquals(FillMode.Action.CHIP, FillMode.CHIP.decide(Confidence.SURE))
        assertEquals(FillMode.Action.CHIP, FillMode.AUTO_WHEN_SURE.decide(Confidence.LIKELY))
        assertEquals(FillMode.Action.FILL, FillMode.AUTO_WHEN_SURE.decide(Confidence.SURE))
        assertEquals(FillMode.Action.FILL, FillMode.AUTO.decide(Confidence.LIKELY))
        FillMode.entries.forEach { assertEquals(FillMode.Action.NOTHING, it.decide(Confidence.NONE)) }
    }
}
