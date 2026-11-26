package com.hailbell

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.util.Locale

private val CARD = RoundedCornerShape(20.dp)
private val ICON = 18.dp
private val ICON_GAP = 8.dp
private val GREEN = Color(0xFF34C759)

/**
 * The reply time card, drawn in place of the system message that carries `expect`. Its words follow
 * the clock (rechecked every minute), and [notify] is what it offers under them. [onNotify] gets the
 * button's state when tapped.
 */
@Composable
internal fun ExpectCard(message: Message, expect: Expect, notify: ReplyTime.Notify, theme: ChatTheme, onNotify: (ReplyTime.Notify) -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val words = ReplyTime.words(expect, now, ZoneId.systemDefault(), Locale.getDefault(), DateFormat.is24HourFormat(context))

    // A short fade and rise the first time it shows; scrolling back to it doesn't replay it.
    var appeared by rememberSaveable(message.id) { mutableStateOf(false) }
    val progress = remember { Animatable(if (appeared) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!appeared) { progress.animateTo(1f, tween(320)); appeared = true }
    }
    val rise = with(LocalDensity.current) { 8.dp.toPx() }
    ExpectCardContent(
        words, expect.name, notify, theme, onNotify,
        Modifier.graphicsLayer { alpha = progress.value; translationY = (1 - progress.value) * rise },
    )
}

/** The card itself, from words already worked out. */
@Composable
internal fun ExpectCardContent(
    words: ReplyTime.Words, name: String, notify: ReplyTime.Notify, theme: ChatTheme,
    onNotify: (ReplyTime.Notify) -> Unit, modifier: Modifier = Modifier,
) {
    val indent = Modifier.padding(start = ICON + ICON_GAP)
    Column(
        modifier.fillMaxWidth().background(theme.incoming, CARD).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Everything but the button reads as one line to TalkBack.
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ICON_GAP)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(ICON), tint = theme.accent)
                Text(words.title, style = ChatType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = theme.incomingText)
            }
            words.line?.let { Text(boldPart(it, words.bold), style = ChatType.subheadline, color = theme.incomingText, modifier = indent) }
            words.ownerLine?.let { Text(it, style = ChatType.footnote, color = theme.secondary, modifier = indent) }
        }
        when (notify) {
            ReplyTime.Notify.ASK, ReplyTime.Notify.SETTINGS -> Text(
                if (notify == ReplyTime.Notify.ASK) L10n.text("expectNotify", mapOf("name" to name)) else L10n.text("expectTurnOn"),
                style = ChatType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = theme.onAccent, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(50))
                    .background(theme.accent).clickable(role = Role.Button) { onNotify(notify) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            ReplyTime.Notify.DONE -> Row(
                indent.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp), tint = GREEN)
                Text(L10n.text("expectNotified", mapOf("name" to name)), style = ChatType.footnote, color = theme.secondary)
            }
            ReplyTime.Notify.NONE -> Unit
        }
    }
}

private fun boldPart(text: String, bold: String?): AnnotatedString = buildAnnotatedString {
    append(text)
    val start = bold?.let { text.indexOf(it) } ?: -1
    if (bold != null && start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, start + bold.length)
}
