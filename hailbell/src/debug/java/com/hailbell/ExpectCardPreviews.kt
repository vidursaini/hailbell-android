package com.hailbell

// Previews of the reply time card (ExpectCard.kt), in debug builds only, so the library and the copies
// the Flutter and React Native SDKs make of src/main need no preview library.
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.util.Locale

// Previews of each state, in English. Rendered with Android Studio's preview or Paparazzi-free screenshots.

private val previewNow = 1_764_171_000_000L // 2025-11-26 15:30 in London, 21:00 in Kolkata
private val previewBack = Expect("back", "Bello", backAt = (previewNow + 8 * 3_600_000L).toDouble(), notify = true, ownerZone = "America/New_York", ownerCountry = "US")

@Composable
private fun PreviewCard(expect: Expect, notify: ReplyTime.Notify, dark: Boolean, is24: Boolean = true) {
    val theme = ChatTheme(Appearance(), dark)
    Column(Modifier.background(theme.background).padding(12.dp)) {
        ExpectCardContent(
            ReplyTime.words(expect, previewNow, ZoneId.of("Europe/London"), Locale.UK, is24, "en"),
            expect.name, notify, theme, {},
        )
    }
}

@Preview(name = "Back, button", widthDp = 360)
@Composable
private fun ExpectBackPreview() = PreviewCard(previewBack, ReplyTime.Notify.ASK, dark = false)

@Preview(name = "Back, notified", widthDp = 360)
@Composable
private fun ExpectNotifiedPreview() = PreviewCard(previewBack, ReplyTime.Notify.DONE, dark = false, is24 = false)

@Preview(name = "Typical", widthDp = 360)
@Composable
private fun ExpectTypicalPreview() = PreviewCard(Expect("typical", "Bello", hours = 3), ReplyTime.Notify.SETTINGS, dark = false)

@Preview(name = "Back, dark", widthDp = 360)
@Composable
private fun ExpectDarkPreview() = PreviewCard(previewBack, ReplyTime.Notify.ASK, dark = true)
