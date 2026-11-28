package com.hailbell

import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The reply time card's words, worked out on the device from `expect` (the server's reply-time.js
 * does the same for the plain text older SDKs show). Recomputed from the clock each time the card
 * draws, so a card read the next day still says "tomorrow" or "tonight" correctly.
 */
internal object ReplyTime {
    private const val HOUR = 3_600_000L

    /**
     * The card's text. [line] is the body under the title (null shows the title only), and [bold] is
     * the part of it to show in bold (the "23:30 tonight" phrase). [ownerLine] is the smaller line
     * with the owner's local time.
     */
    data class Words(val title: String, val line: String?, val bold: String?, val ownerLine: String?)

    /** What the card offers under its text. */
    enum class Notify { NONE, ASK, SETTINGS, DONE }

    fun words(
        e: Expect, now: Long,
        zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault(), is24: Boolean = false,
        lang: String = L10n.language,
    ): Words {
        fun t(key: String, vars: Map<String, String> = emptyMap()) = L10n.text(key, vars, lang)
        val name = mapOf("name" to e.name)
        val joiner = if (lang == "ja" || lang == "zh") "" else " "
        var line: String? = null
        var bold: String? = null
        val title: String
        when (e.kind) {
            "back" -> {
                title = t("expectOffline", name)
                val backAt = e.backAt?.toLong()
                if (backAt != null && backAt > now) {
                    val w = whenPhrase(backAt, now, zone, locale, is24, lang)
                    bold = w
                    val n = ((backAt - now).toDouble() / HOUR).roundToLong()
                    val parts = mutableListOf(t("expectBackBy", mapOf("when" to w)))
                    if (n < 2) parts += t("expectInHour") else if (n < 24) parts += t("expectInHours", mapOf("n" to n.toString()))
                    line = parts.joinToString(joiner)
                }
            }
            "typical" -> {
                title = t("expectWillReply", name)
                val hours = e.hours ?: 1
                line = if (hours <= 1) t("expectTypicalHour", name) else t("expectTypical", name + ("n" to hours.toString()))
            }
            else -> {
                title = t("expectWillReply", name)
                line = t("expectSoon", name)
            }
        }
        return Words(title, line, bold, ownerLine(e, now, locale, is24, lang))
    }

    /** "23:30 tonight", "7:00 AM tomorrow", "9:00 AM on Monday", in the reader's zone and language. */
    fun whenPhrase(at: Long, now: Long, zone: ZoneId, locale: Locale, is24: Boolean, lang: String = L10n.language): String {
        val atTime = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = clock(at, zone, locale, is24)
        return when (atTime.toLocalDate()) {
            today -> L10n.text(if (atTime.hour >= 18) "expectTonight" else "expectToday", mapOf("time" to time), lang)
            today.plusDays(1) -> L10n.text("expectTomorrow", mapOf("time" to time), lang)
            else -> L10n.text("expectOnDay", mapOf("time" to time, "day" to atTime.dayOfWeek.getDisplayName(TextStyle.FULL, locale)), lang)
        }
    }

    /**
     * Countries whose English name takes "the": "in the United States", "in the Netherlands". The other
     * languages' sentences put the country in brackets or need no article.
     */
    private val englishThe = setOf("US", "GB", "AE", "NL", "PH", "BS", "GM", "MV", "SC", "KM", "DO", "CF", "MH", "SB", "KY", "FK", "FO", "CK", "VG", "VI", "TC", "MP", "IM", "AX")

    /** "It's 02:22 for Bello in the United States.", or null without a valid zone and country. */
    fun ownerLine(e: Expect, now: Long, locale: Locale, is24: Boolean, lang: String = L10n.language): String? {
        val zoneId = e.ownerZone ?: return null
        val country = e.ownerCountry ?: return null
        val zone = try { ZoneId.of(zoneId) } catch (_: DateTimeException) { return null }
        // The country in the card's language, so the sentence never mixes two.
        var place = Locale("", country).getDisplayCountry(Locale(lang)).ifEmpty { country }
        if (lang == "en" && country in englishThe) place = "the $place"
        return L10n.text("expectOwnerTime", mapOf("time" to clock(now, zone, locale, is24), "name" to e.name, "place" to place), lang)
    }

    /** A short clock time in [locale]'s style, in 24-hour or 12-hour form as the phone is set. */
    fun clock(at: Long, zone: ZoneId, locale: Locale, is24: Boolean): String =
        DateTimeFormatter.ofPattern(clockPattern(locale, is24), locale).format(Instant.ofEpochMilli(at).atZone(zone))

    private val GAP = "[\\s\\u00A0\\u202F]*"

    /** The locale's short time pattern, switched to the phone's 12- or 24-hour setting. */
    fun clockPattern(locale: Locale, is24: Boolean): String {
        val base = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale)
        val has12 = base.contains('h') || base.contains('K')
        return when {
            is24 && has12 -> base.replace(Regex("[hK]+"), "HH").replace(Regex("${GAP}[aB]+$GAP"), " ").trim()
            !is24 && !has12 -> base.replace(Regex("[Hk]+"), "h") + " a"
            else -> base
        }
    }

    /**
     * Whether the card offers a notification: only when it asks to, and until the team replies. Then
     * "done" when notifications already reach the phone, the system prompt when it may still be shown,
     * else a way to Settings.
     */
    fun notify(e: Expect, teamReplied: Boolean, canNotify: Boolean, canAsk: Boolean): Notify = when {
        !e.notify || teamReplied -> Notify.NONE
        canNotify -> Notify.DONE
        canAsk -> Notify.ASK
        else -> Notify.SETTINGS
    }

    /** True when a reply from a person comes after [card] in [messages]. */
    fun teamReplied(card: Message, messages: List<Message>): Boolean {
        val i = messages.indexOfFirst { it.id == card.id }
        return i >= 0 && messages.drop(i + 1).any { it.author == "agent" }
    }
}
