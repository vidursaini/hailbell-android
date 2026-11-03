package com.hailbell

import org.junit.Test

class PushTest {
    // Apps sync the offers switch from onResume, which can run before the app calls Hailbell.configure.
    @Test fun marketingAndOptOutBeforeConfigureDoNotCrash() {
        HailbellPush.setMarketing(true)
        HailbellPush.setMarketing(false)
        HailbellPush.optOut()
    }
}
