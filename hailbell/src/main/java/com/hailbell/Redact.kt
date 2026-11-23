package com.hailbell

import java.net.URLDecoder

/**
 * Strips secrets from Hailbell.log lines and failed request addresses on the phone, when they are
 * recorded, so nothing secret-looking is kept or sent. A copy of cleanUrl, secretSegment and
 * cleanLogLine in worker/src/bug-report.js; the Swift SDK carries the same rules, and all three
 * change together.
 *
 * The patterns spell out ASCII classes ([A-Za-z0-9_] for \w, [0-9] for \d, and [BOUNDARY] for \b)
 * because Android's regex engine reads \w, \d and \b as Unicode, while the server's JavaScript reads
 * them as ASCII.
 */
internal object Redact {
    const val MAX_URL_LENGTH = 300
    const val MAX_LOG_LENGTH = 300
    private const val REDACTED = ":redacted"
    private const val HIDDEN = "[redacted]"

    private const val W = "A-Za-z0-9_"
    private const val BOUNDARY = "(?:(?<=[$W])(?![$W])|(?<![$W])(?=[$W]))"
    private val I = RegexOption.IGNORE_CASE

    // A path segment after one of these words is the secret itself: /reset/<token>, /keys/<key>.
    private val SECRET_WORDS = Regex(
        "^(token|tokens|key|keys|apikey|api[-_]key|secret|secrets|password|passwords|signature|session|sessions|reset|invite|invites|otp|magic|confirm|verify|unsubscribe)$",
        I,
    )

    // A plain route word, which even after one of those words isn't a secret: /password/forgot.
    private val ROUTE_WORD = Regex("^[a-z-]{1,12}$")
    private val JWT = Regex("^eyJ[$W-]{4,}\\.[$W-]{4,}")
    private val DIGIT = Regex("[0-9]")
    private val PHONE = Regex("^[0-9\\s()+.-]+$")
    private val KEY_CHARS = Regex("^[$W.~+=-]+$")
    private val LETTER = Regex("[a-z]", I)
    private val SCHEME = Regex("^([a-z][a-z0-9+.-]*)://([^/]*)(.*)$", I)

    /**
     * Whether one path segment looks like a secret or personal data: an email, a JWT, a long run of
     * letters and digits (API keys, hashes, UUIDs, signed ids) or a long number (a phone or card).
     */
    fun secretSegment(seg: String): Boolean {
        // Like JavaScript's decodeURIComponent: a "+" stays a "+", and a broken escape keeps the segment as it is.
        val s = runCatching { URLDecoder.decode(seg.replace("+", "%2B"), "UTF-8") }.getOrDefault(seg)
        if ('@' in s) return true
        if (JWT.containsMatchIn(s)) return true
        if (DIGIT.findAll(s).count() >= 10 && PHONE.matches(s)) return true
        if (s.length >= 32 && KEY_CHARS.matches(s)) return true
        return s.length >= 20 && DIGIT.containsMatchIn(s) && LETTER.containsMatchIn(s)
    }

    /**
     * A request's address without its query string, fragment, login or secret-looking path segments:
     * "https://api.example.com/v1/receipts/:redacted". Relative paths stay relative. Null when unusable.
     */
    fun url(raw: String?): String? {
        if (raw == null) return null
        var s = raw.trim().replace(Regex("\\s+"), "")
        if (s.isEmpty()) return null
        s = s.split(Regex("[?#]"))[0]
        var origin = ""
        var path = s
        SCHEME.find(s)?.let { m ->
            val host = m.groupValues[2].replace(Regex("^.*@"), "").lowercase()
            if (host.isEmpty()) return null
            origin = "${m.groupValues[1].lowercase()}://$host"
            path = m.groupValues[3]
        }
        val segs = path.split("/")
        val out = segs.mapIndexed { i, seg ->
            val plain = seg.split(";")[0]
            if (plain.isEmpty()) return@mapIndexed plain
            if (secretSegment(plain)) return@mapIndexed REDACTED
            val prev = segs.getOrNull(i - 1)?.split(";")?.get(0)
            if (!prev.isNullOrEmpty() && SECRET_WORDS.matches(prev) && !ROUTE_WORD.matches(plain)) REDACTED else plain
        }.joinToString("/")
        return (origin + out).take(MAX_URL_LENGTH).ifEmpty { null }
    }

    // Words that name a secret anywhere in a key (userPassword, x-api-key), and short ones that must be
    // the whole key (pin, but not shipping).
    private const val SECRET_KEY =
        "(?:[$W-]*(?:password|passwd|secret|token|api[-_]?key|authorization|cookie|session[-_]?id|signature|card[-_]?number)[$W-]*|pass|pwd|pin|cvv|cvc|otp|auth|sig|sid|key)"

    // A key (bare or quoted) that names a secret, then : or =, then its value: a quoted string, a
    // "Bearer <token>" pair, or a word.
    private val SECRET_PAIR = Regex(
        "((?<![$W-])[\"']?$SECRET_KEY[\"']?\\s*[:=]\\s*)((?:bearer|basic) +\\S+|\"[^\"]*\"|'[^']*'|[^\\s,;&}]+)",
        I,
    )
    private val ADDRESS = Regex("$BOUNDARY[a-z][a-z0-9+.-]*://[^\\s\"'<>]+", I)
    private val AUTH = Regex("$BOUNDARY(bearer|basic) +(?!\\[redacted\\])[$W.~+/=-]+", I)
    private val JWT_ANYWHERE = Regex("${BOUNDARY}eyJ[$W-]{4,}\\.[$W-]{4,}(\\.[$W-]*)?")
    private val LONG_KEY = Regex("$BOUNDARY(?=[$W-]*[0-9])(?=[$W-]*[a-z])[$W-]{32,}$BOUNDARY", I)

    /**
     * A log line with its secret-looking parts hidden: addresses lose their query strings and secret
     * segments, "Bearer <token>", JWTs and long keys become [redacted], and so does the value after a
     * word like password, token or api_key. Null when the line is empty.
     */
    fun logLine(raw: String?): String? {
        if (raw == null) return null
        var s = raw.replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty()) return null
        s = ADDRESS.replace(s) { url(it.value) ?: HIDDEN }
        s = SECRET_PAIR.replace(s) { it.groupValues[1] + HIDDEN }
        s = AUTH.replace(s) { "${it.groupValues[1]} $HIDDEN" }
        s = JWT_ANYWHERE.replace(s, HIDDEN)
        s = LONG_KEY.replace(s, HIDDEN)
        return s.take(MAX_LOG_LENGTH)
    }
}
