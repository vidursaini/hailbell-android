package com.hailbell

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard decides who customers see. A photo bundled in the app (Hailbell.teamPhoto) only fills
 * in while the chat hasn't heard otherwise, and each reply shows its own teammate's photo.
 */
class ReplyIdentityTest {
    private fun team(avatar: String? = null, photo: Boolean? = null, profile: Boolean? = null) = listOf(TeamMember("Vidur", avatar, photo, profile))

    @Test fun bundledPhotoShowsOnlyWhenTheServerHasNoOtherAnswer() {
        assertTrue(allowsBundledPhoto(Appearance(), team()))
        assertTrue(allowsBundledPhoto(Appearance(), emptyList()))
        assertFalse(allowsBundledPhoto(Appearance(agentIdentity = "team"), team(photo = false)))
        assertFalse("an older server without the photo flag", allowsBundledPhoto(Appearance(agentIdentity = "team"), team()))
        assertFalse(allowsBundledPhoto(Appearance(teamPhotos = false), team()))
        assertFalse(allowsBundledPhoto(Appearance(), team(photo = false)))
        assertFalse(allowsBundledPhoto(Appearance(), team("/v1/files/att_1/k")))
        assertFalse(allowsBundledPhoto(Appearance(), team("/v1/files/att_1/k", photo = true, profile = false)))
        // An app that bundles the owner's photo, with the same Profile photo in the dashboard: it stays.
        assertTrue(allowsBundledPhoto(Appearance(), team("/v1/files/att_1/k", photo = true, profile = true)))
    }

    @Test fun sessionsAndRepliesCarryTheIdentityAndOldCopiesStillLoad() {
        val session = Session.parse(JSONObject("""{"token":"t","conversationId":"c","appName":"Acme","appearance":{"agentIdentity":"team"},"team":[{"name":"Acme Support","avatarUrl":null,"photo":false}]}"""))
        assertEquals("team", session.appearance?.agentIdentity)
        assertEquals(false, session.team.first().photo)
        val reply = Message.parse(JSONObject("""{"id":"m1","author":"agent","authorName":"Seva","authorAvatarUrl":"/v1/files/att_2/k","body":"hi","createdAt":1}"""))
        assertEquals("/v1/files/att_2/k", reply.authorAvatarUrl)
        assertEquals(reply, Message.parse(reply.json()))
        // Copies saved by an older version have neither field.
        assertNull(Message.parse(JSONObject("""{"id":"m0","author":"agent","body":"hi","createdAt":0}""")).authorAvatarUrl)
        assertNull(TeamMember.parse(JSONObject("""{"name":"Vidur"}""")).photo)
        val member = TeamMember("Vidur", "/v1/files/att_1/k", true, true)
        assertEquals(member, TeamMember.parse(member.json()))
        assertEquals(Appearance(agentIdentity = "team"), Appearance.parse(Appearance(agentIdentity = "team").json()))
    }
}
