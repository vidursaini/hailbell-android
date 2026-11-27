package com.hailbell

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A reply the team edited or deleted carries `edited` or `deleted`; older payloads and cached copies don't. */
class EditedMessageTest {
    @Test fun parsesEditedAndRoundTripsIt() {
        val m = Message.parse(JSONObject("""{"id":"m1","author":"agent","body":"Refund sent today","createdAt":1,"edited":true}"""))
        assertTrue(m.edited)
        assertFalse(m.deleted)
        assertEquals(m, Message.parse(m.json()))
    }

    @Test fun parsesDeletedAndRoundTripsIt() {
        val m = Message.parse(JSONObject("""{"id":"m1","author":"agent","body":"This message was deleted.","attachments":[],"card":null,"createdAt":1,"deleted":true}"""))
        assertTrue(m.deleted)
        assertEquals(m, Message.parse(m.json()))
    }

    @Test fun messagesWithoutTheFlagsStillParse() {
        val m = Message.parse(JSONObject("""{"id":"m0","author":"agent","body":"hi","createdAt":0}"""))
        assertFalse(m.edited)
        assertFalse(m.deleted)
    }

    @Test fun bothWordsExistInEveryLanguage() {
        for (lang in listOf("en", "es", "fr", "de", "it", "pt", "nl", "ja", "ko", "zh", "hi", "tr", "ru", "id")) {
            for (key in listOf("edited", "deleted")) assertFalse("$lang $key", L10n.text(key, emptyMap(), lang) == key)
        }
        assertEquals("Edited", L10n.text("edited", emptyMap(), "en"))
    }
}
