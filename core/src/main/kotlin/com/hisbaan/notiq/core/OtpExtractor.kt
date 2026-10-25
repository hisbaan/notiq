package com.hisbaan.notiq.core

/**
 * Finds a one-time code in a message. Precision over recall: a wrong code pasted into a field
 * is worse than none, so text with no code keyword (code, OTP, verification, ...) yields
 * nothing, and a candidate far from every keyword is ignored.
 *
 * First pass (Phase 0). Phase 1 hardens it against a corpus of real messages.
 */
object OtpExtractor {
    fun extract(text: String): String? {
        val keywords = keywords(text)
        if (keywords.isEmpty()) return null
        return CANDIDATE.findAll(text)
            .mapNotNull { match -> score(text, match, keywords)?.let { match to it } }
            .maxByOrNull { it.second }
            ?.first
            ?.let(::normalize)
    }

    private fun keywords(text: String): List<IntRange> =
        KEYWORD.findAll(text)
            .filterNot { it.value.lowercase() in NOT_CODE_WORDS }
            .filterNot { NOT_CODE_BEFORE.containsMatchIn(text.substring(0, it.range.first)) }
            .map { it.range }
            .toList()

    /** Higher is better; null rejects the candidate. */
    private fun score(text: String, match: MatchResult, keywords: List<IntRange>): Int? {
        val code = match.groupValues[1]
        val digitsOnly = code.all { it.isDigit() || it == ' ' || it == '-' }
        if (!digitsOnly && (code.none { it.isDigit() } || code.none { it.isLetter() })) return null

        val nearest = keywords.minBy { distance(it, match.range) }
        val distance = distance(nearest, match.range)
        if (distance > MAX_DISTANCE) return null

        var score = 100 - distance
        // "code: 123456" / "code is 123456" is the most common shape.
        if (nearest.last < match.range.first) score += 10
        if (!digitsOnly) score -= 30
        if (digitsOnly && code.length == 4 && YEAR.matches(code)) score -= 50
        return score
    }

    private fun distance(keyword: IntRange, candidate: IntRange): Int = when {
        keyword.last < candidate.first -> candidate.first - keyword.last - 1
        candidate.last < keyword.first -> keyword.first - candidate.last - 1
        else -> 0
    }

    /** "482-913" and "482 913" → "482913"; a "G-" style prefix is dropped by the regex group. */
    private fun normalize(match: MatchResult): String = match.groupValues[1].filterNot { it == ' ' || it == '-' }

    private const val MAX_DISTANCE = 80

    private val KEYWORD = Regex(
        """(?i)\b(?:otp|pin|2fa|mfa|tan|passcode|log[- ]?in|sign[- ]?in|one[- ]time)\b""" +
            """|verif\p{L}*|authenti\p{L}*|\p{L}*code(?:s)?\b|c[óo]digo|token""",
    )

    /** Words the keyword regex catches that aren't about one-time codes. */
    private val NOT_CODE_WORDS = setOf("barcode", "decode", "encode", "promocode", "zipcode", "postcode", "qrcode")

    /** "promo code", "zip code", ...: the word right before a keyword. */
    private val NOT_CODE_BEFORE = Regex("""(?i)\b(?:promo|promotional|coupon|discount|referral|zip|postal|area|gift|voucher|qr|source)[\s-]*$""")

    /**
     * A 4-8 digit number, a 3+3 or 4+4 digit group ("482 913", "4829-1300"), or a 4-8 character
     * upper-case alphanumeric, optionally after a short prefix ("G-482913"). It must stand alone:
     * not part of an amount, phone number, time, date, decimal, order number (#) or longer number.
     */
    private val CANDIDATE = Regex(
        """(?<![\p{L}\p{N}$€£¥₹#@+/.:,(\-])(?<!\p{N} )(?:[A-Z]{1,3}-)?""" +
            """(\d{3}[ \-]\d{3}|\d{4}[ \-]\d{4}|\d{4,8}|[A-Z0-9]{4,8})""" +
            """(?![\p{L}\p{N}%]|[.,:/\-]\p{N}| \p{N})""",
    )

    private val YEAR = Regex("""(?:19|20)\d\d""")
}
