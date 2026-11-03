package com.hailbell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampaignsTest {
    @Test fun readsTheCampaignFromFcmData() {
        val hb = Campaigns.parse(mapOf("hb" to """{"n":"ntf_1","u":"app://sale","s":"sub_1","v":"b"}"""))!!
        assertEquals("ntf_1", hb.getString("n"))
        val ping = Campaigns.ping(hb)
        assertEquals(setOf("n", "s", "v"), ping.keys().asSequence().toSet())
        assertEquals("sub_1", ping.getString("s"))
    }

    @Test fun chatRepliesAndOtherPushesAreNotCampaigns() {
        assertNull(Campaigns.parse(mapOf("hailbell" to "1", "conversationId" to "cv_1")))
        assertNull(Campaigns.parse(mapOf("hb" to "not json")))
        assertNull(Campaigns.parse(mapOf("hb" to """{"u":"app://x"}""")))
    }
}
