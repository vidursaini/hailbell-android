package com.hailbell

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

class HostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Hailbell.open(this)
    }
}

/**
 * The chat is open, the person goes to the home screen, a reply arrives and they tap it.
 * The tap must bring back the chat they had open, not stack a second chat on top of it.
 */
@RunWith(AndroidJUnit4::class)
class ChatTapTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun chats(): Int {
        var n = 0
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            for (stage in listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED)) {
                n += monitor.getActivitiesInStage(stage).count { it is ChatActivity }
            }
        }
        return n
    }

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    private fun waitFor(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!check()) {
            if (System.currentTimeMillis() > until) throw AssertionError("timed out waiting for $what")
            Thread.sleep(100)
        }
    }

    @After
    fun closeAll() {
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            for (stage in listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED, Stage.CREATED)) {
                monitor.getActivitiesInStage(stage).forEach { it.finish() }
            }
        }
    }

    private fun tapWithChatInBackground(tap: Intent) {
        // No real server: the chat's session requests fail and retry quietly.
        instrumentation.runOnMainSync { Hailbell.configure(context, "test-app", "http://127.0.0.1:9") }
        context.startActivity(Intent(context, HostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("the chat to open") { ChatActivity.visible }
        assertEquals(1, chats())

        shell("input keyevent KEYCODE_HOME")
        waitFor("the chat to go to the background") { !ChatActivity.visible }

        context.startActivity(tap)
        waitFor("the chat to come back") { ChatActivity.visible }
        Thread.sleep(1_000)
        assertEquals("a reply tap stacked a second chat", 1, chats())
    }

    /** The notification the SDK shows for a reply that arrives while the app is open. */
    @Test
    fun sdkNotificationTapReusesTheOpenChat() = tapWithChatInBackground(HailbellPush.chatTapIntent(context))

    /** The notification Android builds itself from the push's click_action while the app is in the background. */
    @Test
    fun systemNotificationTapReusesTheOpenChat() = tapWithChatInBackground(
        Intent(HailbellPush.OPEN_ACTION).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
