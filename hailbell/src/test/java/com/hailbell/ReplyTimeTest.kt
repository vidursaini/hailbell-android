package com.hailbell

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** The reply time card: parsing `expect`, its words from a fixed clock, and what it offers. */
class ReplyTimeTest {
    private val london = ZoneId.of("Europe/London")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0, zone: ZoneId = london) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    // Wednesday 26 November 2025, 15:00 in London.
    private val now = at(2025, 11, 26, 15)
    private fun back(at: Long, notify: Boolean = true) = Expect("back", "Vidur", backAt = at.toDouble(), notify = notify)

    @Test fun parsesTheCardAndRoundTripsIt() {
        val m = Message.parse(JSONObject(
            """{"id":"m1","author":"system","body":"Vidur is offline right now.","createdAt":1,
               "expect":{"kind":"back","name":"Vidur","backAt":1764199800000,"notify":true,"ownerZone":"Asia/Kolkata","ownerCountry":"IN"}}""",
        ))
        assertEquals(Expect("back", "Vidur", 1_764_199_800_000.0, null, true, "Asia/Kolkata", "IN"), m.expect)
        assertTrue(m.showsExpectCard)
        assertEquals(m, Message.parse(m.json()))
        val typical = Message.parse(JSONObject("""{"id":"m2","author":"system","body":"x","createdAt":1,"expect":{"kind":"typical","name":"Vidur","hours":3,"notify":false}}"""))
        assertEquals(3, typical.expect?.hours)
        assertTrue(typical.showsExpectCard)
    }

    @Test fun missingOrMalformedExpectKeepsTheBubble() {
        fun parse(extra: String) = Message.parse(JSONObject("""{"id":"m0","author":"system","body":"hi","createdAt":0$extra}"""))
        assertNull(parse("").expect)
        assertNull(parse(""","expect":null""").expect)
        assertNull(parse(""","expect":5""").expect)
        assertNull(parse(""","expect":"not json"""").expect)
        assertNull(parse(""","expect":{"name":"Vidur"}""").expect)
        assertFalse(parse(""","expect":{"kind":"later","name":"Vidur"}""").showsExpectCard) // unknown kind
        assertFalse(parse(""","expect":{"kind":"back","name":"Vidur"}""").showsExpectCard) // no backAt
        assertFalse(parse(""","expect":{"kind":"typical","name":"Vidur"}""").showsExpectCard) // no hours
        assertTrue(parse(""","expect":{"kind":"soon","name":"Vidur"}""").showsExpectCard)
        // Only a system message draws the card.
        assertFalse(Message.parse(JSONObject("""{"id":"m0","author":"agent","body":"hi","createdAt":0,"expect":{"kind":"soon","name":"V"}}""")).showsExpectCard)
    }

    @Test fun dayPhrases() {
        fun phrase(t: Long, is24: Boolean = true) = ReplyTime.whenPhrase(t, now, london, Locale.UK, is24, "en")
        assertEquals("17:30 today", phrase(at(2025, 11, 26, 17, 30)))
        assertEquals("18:00 tonight", phrase(at(2025, 11, 26, 18)))
        assertEquals("23:30 tonight", phrase(at(2025, 11, 26, 23, 30)))
        assertEquals("09:00 tomorrow", phrase(at(2025, 11, 27, 9)))
        assertEquals("09:00 on Saturday", phrase(at(2025, 11, 29, 9)))
        assertTrue(phrase(at(2025, 11, 26, 23, 30), is24 = false).matches(Regex("11:30\\W*pm tonight", RegexOption.IGNORE_CASE)))
        // The device's zone decides the day: 23:30 in London is already tomorrow in Kolkata.
        val kolkata = ZoneId.of("Asia/Kolkata")
        assertEquals("05:00 tomorrow", ReplyTime.whenPhrase(at(2025, 11, 26, 23, 30), now, kolkata, Locale.UK, true, "en"))
        // Words and weekday in the reader's language.
        assertEquals("09:00 samedi", ReplyTime.whenPhrase(at(2025, 11, 29, 9), now, london, Locale.FRANCE, true, "fr"))
        assertEquals("morgen um 09:00", ReplyTime.whenPhrase(at(2025, 11, 27, 9), now, london, Locale.GERMANY, true, "de"))
    }

    @Test fun hourCount() {
        fun line(t: Long) = ReplyTime.words(back(t), now, london, Locale.UK, true, "en").line
        assertEquals("Usually back by 23:30 tonight, your time. That’s in about 9 hours.", line(at(2025, 11, 27, 0, 0) - 30 * 60_000))
        assertEquals("Usually back by 16:20 today, your time. That’s in about an hour.", line(at(2025, 11, 26, 16, 20)))
        assertEquals("Usually back by 16:30 today, your time. That’s in about 2 hours.", line(at(2025, 11, 26, 16, 30))) // 1.5 rounds to 2
        assertEquals("Usually back by 14:00 tomorrow, your time. That’s in about 23 hours.", line(at(2025, 11, 27, 14)))
        assertEquals("Usually back by 15:00 tomorrow, your time.", line(at(2025, 11, 27, 15)))
        // Already past: the title alone.
        val past = ReplyTime.words(back(now - 1), now, london, Locale.UK, true, "en")
        assertEquals("Vidur is offline right now", past.title)
        assertNull(past.line)
        val w = ReplyTime.words(back(at(2025, 11, 26, 23, 30)), now, london, Locale.UK, true, "en")
        assertEquals("23:30 tonight", w.bold)
        // Japanese joins its sentences without a space.
        val ja = ReplyTime.words(back(at(2025, 11, 26, 16, 20)), now, london, Locale.JAPAN, true, "ja").line!!
        assertFalse(ja.contains("。 "))
    }

    @Test fun typicalAndSoon() {
        fun words(e: Expect) = ReplyTime.words(e, now, london, Locale.UK, true, "en")
        assertEquals("Vidur will reply here", words(Expect("typical", "Vidur", hours = 3)).title)
        assertEquals("At this time of day Vidur usually replies within 3 hours.", words(Expect("typical", "Vidur", hours = 3)).line)
        assertEquals("At this time of day Vidur usually replies within an hour.", words(Expect("typical", "Vidur", hours = 1)).line)
        assertEquals("Vidur will reply as soon as possible.", words(Expect("soon", "Vidur")).line)
        assertNull(words(Expect("soon", "Vidur")).bold)
    }

    @Test fun ownerTime() {
        val e = Expect("soon", "Vidur", ownerZone = "Asia/Kolkata", ownerCountry = "IN")
        // 15:00 in London is 20:30 in Kolkata.
        assertEquals("It’s 20:30 for Vidur in India.", ReplyTime.words(e, now, london, Locale.UK, true, "en").ownerLine)
        assertEquals("Bei Vidur in Indien ist es 20:30.", ReplyTime.words(e, now, london, Locale.GERMANY, true, "de").ownerLine)
        assertNull(ReplyTime.words(e.copy(ownerCountry = null), now, london, Locale.UK, true, "en").ownerLine)
        assertNull(ReplyTime.words(e.copy(ownerZone = "Not/AZone"), now, london, Locale.UK, true, "en").ownerLine)
    }

    @Test fun clockFollowsThePhoneSetting() {
        val t = at(2025, 11, 26, 23, 30)
        assertEquals("23:30", ReplyTime.clock(t, london, Locale.US, true))
        assertTrue(ReplyTime.clock(t, london, Locale.UK, false).matches(Regex("11:30\\W*pm", RegexOption.IGNORE_CASE)))
        assertTrue(ReplyTime.clock(t, london, Locale.US, false).matches(Regex("11:30\\W*PM")))
        assertEquals("23:30", ReplyTime.clock(t, london, Locale.KOREA, true))
        // A 24-hour locale switched to 12-hour drops the leading zero.
        assertTrue(ReplyTime.clock(at(2025, 11, 26, 9), london, Locale.GERMANY, false).matches(Regex("9:00\\W*\\S+")))
    }

    @Test fun notifyChoice() {
        val e = back(now + 3_600_000)
        assertEquals(ReplyTime.Notify.ASK, ReplyTime.notify(e, teamReplied = false, canNotify = false, canAsk = true))
        assertEquals(ReplyTime.Notify.SETTINGS, ReplyTime.notify(e, teamReplied = false, canNotify = false, canAsk = false))
        assertEquals(ReplyTime.Notify.DONE, ReplyTime.notify(e, teamReplied = false, canNotify = true, canAsk = true))
        assertEquals(ReplyTime.Notify.NONE, ReplyTime.notify(e, teamReplied = true, canNotify = false, canAsk = true))
        assertEquals(ReplyTime.Notify.NONE, ReplyTime.notify(e.copy(notify = false), teamReplied = false, canNotify = false, canAsk = true))
    }

    @Test fun teamRepliedOnlyCountsAgentsAfterTheCard() {
        fun msg(id: String, author: String) = Message(id, author, null, "x", emptyList(), null, 0.0, null)
        val card = msg("c", "system")
        assertFalse(ReplyTime.teamReplied(card, listOf(msg("a", "agent"), card, msg("b", "bot"), msg("u", "user"))))
        assertTrue(ReplyTime.teamReplied(card, listOf(card, msg("u", "user"), msg("a", "agent"))))
    }
}
