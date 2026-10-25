package com.hisbaan.notiq.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `file is trimmed to whole entries once it passes the cap`() {
        val file = folder.root.resolve("diagnostics.txt")
        val log = DiagnosticsLog(file)
        // Multi-line events whose body lines look like entry starts, to check the cut point.
        val body = "line\n12:00:00 not an entry\n" + "x".repeat(1_000)
        repeat(1_500) { log.event("event $it\n$body") }

        val text = file.readText()
        assertTrue("file is ${text.length} chars", text.length <= 512 * 1024)
        assertTrue(text.length > 128 * 1024)
        assertTrue(Regex("""^\d\d:\d\d:\d\d event \d+""").containsMatchIn(text))
        assertTrue(text.trimEnd().endsWith("x".repeat(1_000)))
        assertTrue(text.contains("event 1499"))
    }
}
