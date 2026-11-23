package com.hailbell

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportTest {
    @After fun clear() {
        BugReports.resetScreens()
        BugReports.resetLogs()
    }

    @Test fun trackScreenKeepsTheLastTenAndSkipsRepeatsAndBlanks() {
        BugReports.resetScreens()
        Hailbell.trackScreen("Home")
        Hailbell.trackScreen("Home")
        Hailbell.trackScreen("  ")
        Hailbell.trackScreen("")
        Hailbell.trackScreen(" Settings ")
        Hailbell.trackScreen("Home")
        assertEquals(listOf("Home", "Settings", "Home"), BugReports.screens())

        BugReports.resetScreens()
        (1..14).forEach { Hailbell.trackScreen("Screen $it") }
        assertEquals((5..14).map { "Screen $it" }, BugReports.screens())

        BugReports.resetScreens()
        Hailbell.trackScreen("x".repeat(120))
        assertEquals(80, BugReports.screens().single().length)
    }

    private val still = floatArrayOf(0f, 0f, 9.81f)
    private val hard = floatArrayOf(20f, 18f, 9.81f) // about 2.9 g

    private fun ShakeDetector.read(v: FloatArray, at: Long) = onReading(v[0], v[1], v[2], at)

    @Test fun shakeFiresOnABackAndForthShake() {
        val d = ShakeDetector()
        assertFalse(d.read(still, 0))
        assertFalse(d.read(hard, 100))
        assertFalse(d.read(still, 180))
        assertTrue(d.read(hard, 260))
    }

    @Test fun aSingleBumpDoesNotFire() {
        val d = ShakeDetector()
        // One knock: a few strong readings close together count as one peak.
        assertFalse(d.read(hard, 0))
        assertFalse(d.read(hard, 20))
        assertFalse(d.read(hard, 40))
        assertFalse(d.read(still, 60))
        // A second knock long after is a new bump, not a shake.
        assertFalse(d.read(hard, 2000))
        // Gentle movement never counts.
        assertFalse(d.read(floatArrayOf(12f, 5f, 9.81f), 2200))
        assertFalse(d.read(floatArrayOf(12f, 5f, 9.81f), 2400))
    }

    @Test fun nothingFiresDuringTheCooldown() {
        val d = ShakeDetector()
        assertFalse(d.read(hard, 0))
        assertTrue(d.read(hard, 200))
        assertFalse(d.read(hard, 400))
        assertFalse(d.read(hard, 600))
        assertFalse(d.read(hard, 1500))
        assertFalse(d.read(hard, 1690))
        // Cooldown over at 1700: a fresh shake fires again.
        assertFalse(d.read(hard, 1800))
        assertTrue(d.read(hard, 2000))
    }

    @Test fun reportFieldsHaveTheKeysTheServerReads() {
        val fields = BugReports.fields(
            appVersion = "2.3", build = 45, device = "Google Pixel 7a", os = "Android 16", locale = "en_US",
            screens = listOf("Home", "Paywall"),
        )
        assertEquals(setOf("appVersion", "build", "device", "os", "locale", "screens"), fields.keys)
        assertEquals("45", fields["build"])
        assertEquals("Google Pixel 7a", fields["device"])
        assertEquals(listOf("Home", "Paywall"), fields["screens"])
        // No version name: the key is left out rather than sent empty.
        val bare = BugReports.fields(null, null, "Google Pixel 7a", "Android 16", "en_US", emptyList())
        assertEquals(setOf("device", "os", "locale", "screens"), bare.keys)
    }

    @Test fun logKeepsTheLastFiftyLinesCleaned() {
        BugReports.resetLogs()
        (0..50).forEach { BugReports.log("line $it", now = 1_700_000_000_000L + it) }
        val logs = BugReports.logs()
        assertEquals(50, logs.size)
        // The 51st line pushed out the first.
        assertEquals(mapOf("t" to 1_700_000_000_001L, "m" to "line 1"), logs.first())
        assertEquals("line 50", logs.last()["m"])
        Hailbell.log("receipt token=abc")
        assertEquals("receipt token=[redacted]", BugReports.logs().last()["m"])
        Hailbell.log("   ")
        assertEquals(50, BugReports.logs().size)
    }

    @Test fun onlyFailedRequestsAreKeptAndOnlyTheLastTen() {
        BugReports.resetLogs()
        Hailbell.logRequest("GET", "https://api.example.com/v1/ok", 200)
        Hailbell.logRequest("GET", "https://api.example.com/v1/moved", 302)
        assertEquals(emptyList<Map<String, Any>>(), BugReports.requests())
        (0..13).forEach { BugReports.logRequest("post", "https://api.example.com/v1/r/$it?secret=1", 500, now = 7L) }
        Hailbell.logRequest("BREW", "https://api.example.com/v1/restore", 0)
        val requests = BugReports.requests()
        assertEquals(10, requests.size)
        assertEquals(mapOf("t" to 7L, "method" to "POST", "url" to "https://api.example.com/v1/r/5", "status" to 500), requests.first())
        assertEquals("GET", requests.last()["method"])
        assertEquals(0, requests.last()["status"])
        assertEquals(setOf("t", "method", "url", "status"), requests.last().keys)
    }

    @Test fun reportFieldsCarryTheLogAndFailedRequestsWhenThereAreAny() {
        val logs = listOf(mapOf<String, Any>("t" to 1L, "m" to "Restore tapped"))
        val requests = listOf(mapOf<String, Any>("t" to 2L, "method" to "POST", "url" to "https://a.com/x", "status" to 422))
        val fields = BugReports.fields(null, null, "Google Pixel 7a", "Android 16", "en_US", emptyList(), logs, requests)
        assertEquals(logs, fields["logs"])
        assertEquals(requests, fields["requests"])
        val empty = BugReports.fields(null, null, "Google Pixel 7a", "Android 16", "en_US", emptyList(), emptyList(), emptyList())
        assertFalse("logs" in empty)
        assertFalse("requests" in empty)
    }

    @Test fun reportCardsAlwaysShow() {
        assertTrue(Card("report", "Send a bug report", null, null, "sent").isShown)
        assertFalse(Card("review", "Rate us", null, null, "sent").isShown)
    }

    @Test fun messagesParseWithAndWithoutAReport() {
        val plain = Message.parse(JSONObject("""{"id":"m1","author":"user","body":"hi","createdAt":1}"""))
        assertNull(plain.report)
        val report = Message.parse(JSONObject(
            """{"id":"m2","author":"user","body":"","createdAt":2,"attachments":[{"id":"att_1","url":"/v1/files/att_1/k"}],
               "report":{"appVersion":"2.3","build":"45","device":"Google Pixel 7a","deviceName":"Pixel 7a","os":"Android 16","locale":"en_US","screens":["Home","Settings"]}}""",
        ))
        assertEquals(BugReport("2.3", "45", "Google Pixel 7a", "Pixel 7a", "Android 16", "en_US", listOf("Home", "Settings")), report.report)
        assertEquals(report, Message.parse(report.json()))
        // A server that sends only some fields.
        assertEquals(BugReport(os = "Android 16"), Message.parse(JSONObject("""{"id":"m3","author":"user","createdAt":3,"report":{"os":"Android 16"}}""")).report)
    }

    @Test fun screenshotsScaleDownToTheLongestSideLimit() {
        assertEquals(1080 to 2400, ImageEncoder.fit(1080, 2400, 2400))
        assertEquals(720 to 1600, ImageEncoder.fit(1080, 2400, 1600))
        assertEquals(1600 to 720, ImageEncoder.fit(2400, 1080, 1600))
        assertEquals(800 to 600, ImageEncoder.fit(800, 600, 1600))
        val (w, h) = ImageEncoder.fit(1439, 3201, 1600)
        assertTrue(w <= 1600 && h <= 1600)
    }
}
