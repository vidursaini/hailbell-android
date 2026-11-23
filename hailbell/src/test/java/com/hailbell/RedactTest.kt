package com.hailbell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The same cases as test/bug-report-logs.test.js: the phone and the server must clean alike.
class RedactTest {
    @Test fun urlDropsTheQueryFragmentAndLoginAndRedactsSecretLookingParts() {
        val cases = listOf(
            "https://user:pw@API.Example.com/v1/receipts?token=abc&user=bob#top" to "https://api.example.com/v1/receipts",
            "https://api.example.com/v1/items/12345" to "https://api.example.com/v1/items/12345",
            "https://api.example.com/users/bob@example.com/profile" to "https://api.example.com/users/:redacted/profile",
            "https://api.example.com/users/bob%40example.com" to "https://api.example.com/users/:redacted",
            "https://api.example.com/u/550e8400-e29b-41d4-a716-446655440000/orders" to "https://api.example.com/u/:redacted/orders",
            "https://api.example.com/verify/eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln" to "https://api.example.com/verify/:redacted",
            "https://api.example.com/files/9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08" to "https://api.example.com/files/:redacted",
            "https://api.example.com/reset/Zx81kdh2" to "https://api.example.com/reset/:redacted",
            "https://api.example.com/keys/sk_live_abcdefghijklmnop1234" to "https://api.example.com/keys/:redacted",
            "https://api.example.com/password/forgot" to "https://api.example.com/password/forgot",
            "https://api.example.com/call/+1 (415) 555-0100-2000" to "https://api.example.com/call/:redacted",
            "https://api.example.com/v1/receipt;jsessionid=ABC123" to "https://api.example.com/v1/receipt",
            "/v1/restore?receipt=MIIT" to "/v1/restore",
            "https://api.example.com" to "https://api.example.com",
        )
        for ((raw, want) in cases) assertEquals(raw, want, Redact.url(raw))
        assertNull(Redact.url(""))
        assertNull(Redact.url(null))
        assertNull(Redact.url("https://@/x"))
        assertTrue(Redact.url("https://a.com/" + "a/".repeat(400))!!.length <= 300)
        assertFalse(Redact.secretSegment("restore"))
        assertFalse(Redact.secretSegment("v2"))
        assertFalse(Redact.secretSegment("pro_yearly_2026"))
    }

    @Test fun logLineHidesTokensPasswordsKeysAndQueryStringsAndKeepsTheRest() {
        val cases = listOf(
            "restore failed: 422 for sku pro_yearly" to "restore failed: 422 for sku pro_yearly",
            "POST https://api.example.com/v1/receipt?key=abc failed" to "POST https://api.example.com/v1/receipt failed",
            "Authorization: Bearer abc.def.ghi" to "Authorization: [redacted]",
            "sent with Bearer abcdef123456" to "sent with Bearer [redacted]",
            "login password=hunter2 user=bob" to "login password=[redacted] user=bob",
            """{"apiKey":"abc123", "n":1}""" to """{"apiKey":[redacted], "n":1}""",
            "x-api-key: zzz" to "x-api-key: [redacted]",
            "refresh_token=abc&next=1" to "refresh_token=[redacted]&next=1",
            "pin=1234" to "pin=[redacted]",
            "jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln here" to "jwt [redacted] here",
            "key a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6 used" to "key [redacted] used",
            "shipping: 3 items" to "shipping: 3 items",
            "monkey=3" to "monkey=3",
            "  two\n lines\t here " to "two lines here",
        )
        for ((raw, want) in cases) assertEquals(raw, want, Redact.logLine(raw))
        assertNull(Redact.logLine("   "))
        assertNull(Redact.logLine(null))
        assertEquals(300, Redact.logLine("x".repeat(1000))!!.length)
    }
}
