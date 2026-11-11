package com.hailbell

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Messages may quote another message (`replyTo`); payloads and cached copies without it still load. */
class ReplyTest {
    @Test fun parsesTheQuoteAndRoundTripsIt() {
        val m = Message.parse(JSONObject(
            """{"id":"m2","author":"agent","authorName":"Ana","body":"Sure","createdAt":2,
               "replyTo":{"id":"m1","author":"user","authorName":null,"body":"Can I get a refund?"}}""",
        ))
        assertEquals(ReplyTo("m1", "user", null, "Can I get a refund?", false), m.replyTo)
        assertEquals(m, Message.parse(m.json()))
    }

    @Test fun imageOnlyQuotes() {
        val m = Message.parse(JSONObject("""{"id":"m3","author":"user","body":"this one","createdAt":3,"replyTo":{"id":"m1","author":"agent","body":"","image":true}}"""))
        val q = m.replyTo!!
        assertEquals("", q.body)
        assertTrue(q.image)
        assertEquals(m, Message.parse(m.json()))
    }

    @Test fun messagesWithoutAQuoteStillParse() {
        assertNull(Message.parse(JSONObject("""{"id":"m0","author":"agent","body":"hi","createdAt":0}""")).replyTo)
        assertNull(Message.parse(JSONObject("""{"id":"m0","author":"agent","body":"hi","createdAt":0,"replyTo":null}""")).replyTo)
        // A malformed quote is dropped, the message is kept.
        assertNull(Message.parse(JSONObject("""{"id":"m0","author":"agent","body":"hi","createdAt":0,"replyTo":{"body":"x"}}""")).replyTo)
    }

    @Test fun quotingAMessageTakesASnapshot() {
        val long = "a".repeat(300)
        val m = Message("m1", "agent", "Ana", long, listOf(Attachment("a1", "/x", null, null)), null, 1.0, null)
        val q = ReplyTo.of(m)
        assertEquals("m1", q.id)
        assertEquals(200, q.body.length)
        assertTrue(q.image)
        assertFalse(ReplyTo.of(m.copy(attachments = emptyList())).image)
    }

    @Test fun namesFollowTheCustomerSideRules() {
        assertEquals(L10n.text("you"), replyName("user", "Sam", "Acme Support", null))
        assertEquals("Ana", replyName("agent", "Ana", "Acme Support", null))
        assertEquals("Acme Support", replyName("agent", null, "Acme Support", null))
        assertEquals("names hidden in the dashboard", "Acme Support", replyName("agent", "Ana", "Acme Support", false))
        assertEquals("Max · ${L10n.text("ai")}", replyName("bot", "Max", "Acme Support", null))
        assertEquals("Acme Support", replyName("system", null, "Acme Support", null))
    }
}
