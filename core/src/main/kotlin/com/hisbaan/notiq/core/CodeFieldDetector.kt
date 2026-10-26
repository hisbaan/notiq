package com.hisbaan.notiq.core

/** What an input field says about itself, as far as the toolkit exposes it. */
data class FieldInfo(
    val hint: String? = null,
    /** e.g. "com.example:id/otp_input". */
    val viewId: String? = null,
    /** A label pointing at the field (labeledBy), or a web form's label. */
    val label: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val numeric: Boolean = false,
    val password: Boolean = false,
    /** Null when unknown (Compose, most web fields). */
    val maxLength: Int? = null,
)

/** The rest of the screen around the focused field. */
data class ScreenInfo(
    /** Visible text, joined. */
    val text: String = "",
    /** Editable fields on screen, the focused one included. */
    val editableCount: Int = 1,
)

enum class Confidence { NONE, LIKELY, SURE }

data class Assessment(
    val confidence: Confidence,
    /** The code goes one character per box into the field and its editable siblings. */
    val split: Boolean,
    /** Why, for Diagnostics. */
    val reason: String,
)

/**
 * Whether the focused field wants a one-time code. Wrong guesses put a code in the wrong field
 * (or, with the chip, just offer it), so field-level evidence counts most and the screen's text
 * only tips a numeric or lone field.
 */
object CodeFieldDetector {
    /** [siblings]: editable fields sharing the focused field's parent, the focused one included, in order. */
    fun assess(field: FieldInfo, siblings: List<FieldInfo>, screen: ScreenInfo, codeLength: Int): Assessment {
        val words = describe(field)
        val codeWords = CODE_WORDS.containsMatchIn(words) && !CARD.containsMatchIn(words)
        val screenTalksCode = SCREEN_CODE.containsMatchIn(screen.text)

        if (field.text?.filter { it.isLetterOrDigit() }?.length?.let { it >= codeLength } == true) {
            return Assessment(Confidence.NONE, split = false, reason = "already filled")
        }
        if (splitBoxes(siblings, codeLength) && (codeWords || screenTalksCode || siblings.all { it.maxLength == 1 })) {
            return Assessment(Confidence.SURE, split = true, reason = "$codeLength one-character boxes")
        }
        if (!codeWords && (field.password || NOT_CODE.containsMatchIn(words))) {
            return Assessment(Confidence.NONE, split = false, reason = "not a code field ($words)")
        }
        val fitsLength = field.maxLength == codeLength
        return when {
            codeWords && (field.numeric || fitsLength) -> Assessment(Confidence.SURE, false, "code words, numeric or fits")
            codeWords -> Assessment(Confidence.LIKELY, false, "code words")
            field.numeric && fitsLength -> Assessment(Confidence.LIKELY, false, "numeric, max length $codeLength")
            screenTalksCode && (field.numeric || screen.editableCount == 1) ->
                Assessment(Confidence.LIKELY, false, "screen asks for a code, ${if (field.numeric) "numeric" else "only field"}")
            else -> Assessment(Confidence.NONE, false, "no sign of a code field")
        }
    }

    private fun splitBoxes(siblings: List<FieldInfo>, codeLength: Int): Boolean =
        codeLength in 4..8 &&
            siblings.size == codeLength &&
            siblings.none { it.password && !CODE_WORDS.containsMatchIn(describe(it)) } &&
            siblings.all { it.maxLength == null || it.maxLength == 1 }

    /** Everything the field says about itself, lower case, ids split into words ("otp_input" → "otp input"). */
    private fun describe(field: FieldInfo): String =
        listOfNotNull(field.hint, field.viewId?.substringAfter(":id/"), field.label, field.contentDescription)
            .joinToString(" ")
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .lowercase()

    private val CODE_WORDS = Regex(
        """\b(?:otp|one ?time|code|passcode|verification|verify|2fa|mfa|totp|token|pin|sms|auth\w*|security code|tan)\b""",
    )

    /** Card security codes say "code" too. */
    private val CARD = Regex("""\b(?:card|cvv|cvc|csc|expir\w*)\b""")

    private val NOT_CODE = Regex(
        """\b(?:password|e ?mail|user ?name|login|phone|mobile|card|zip|postal|amount|search|message|reply|chat|name|address|url|comment)\b""",
    )

    private val SCREEN_CODE = Regex(
        """(?i)\b(?:verification code|security code|one[- ]time|otp|2fa|two[- ](?:factor|step)|enter (?:the|your) code|code (?:we|was) sent|sent (?:you )?a code)""",
    )
}

/** How a likely code field is filled (a setting). */
enum class FillMode {
    /** Show a "Fill" chip; the user taps it. */
    CHIP,

    /** Fill SURE matches, chip for LIKELY ones. */
    AUTO_WHEN_SURE,

    /** Fill LIKELY and SURE matches. */
    AUTO,
    ;

    enum class Action { NOTHING, CHIP, FILL }

    fun decide(confidence: Confidence): Action = when (confidence) {
        Confidence.NONE -> Action.NOTHING
        Confidence.LIKELY -> if (this == AUTO) Action.FILL else Action.CHIP
        Confidence.SURE -> if (this == CHIP) Action.CHIP else Action.FILL
    }
}
