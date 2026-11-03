# Hailbell for Android

In-app support chat and push notifications for Android 8 (API 26) and later, in Kotlin and Jetpack Compose. Customers write from a native chat screen inside your app; you answer from the [Hailbell](https://hailbell.com) dashboard, Discord or Telegram, and your reply reaches them live or as a push notification. It mirrors the iOS SDK: the same API names, the same screens and the same server.

## Install

The library is published through [JitPack](https://jitpack.io/#vidursaini/hailbell-android). Add the JitPack repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then the dependency in the app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.vidursaini:hailbell-android:1.0.0")
}
```

Requirements:

- minSdk 26, compileSdk 36 or later, Java 17.
- The library brings OkHttp, Coil, Compose, `lifecycle-process` and Play's in-app review library.
- It has no Firebase dependency. The app passes FCM tokens and messages in.
- Its manifest adds `INTERNET`, `POST_NOTIFICATIONS` and the chat activity, and Gradle merges them.

## Quick start

1. Create an app at [app.hailbell.com](https://app.hailbell.com) with platform Android. Copy its **App ID** (`app_...`). The App ID is public and belongs in your code. For push, enter the Firebase project ID from your `google-services.json` in the dashboard's push settings.
2. Configure once in `Application.onCreate()`, then open the chat from a button:

```kotlin
import com.hailbell.Hailbell

Hailbell.configure(this, "app_XXXXXXXX")
Hailbell.setMetadata(mapOf("plan" to "free"))   // shown beside the chat in the dashboard

// A "Chat with us" button
Hailbell.open(activity)
```

3. For replies as push notifications, pass FCM tokens and messages from your `FirebaseMessagingService`:

```kotlin
class SupportPushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Hailbell.setPushToken(token)
    override fun onMessageReceived(message: RemoteMessage) {
        val handled = HailbellPush.handleMessage(this, message.data, message.notification?.title, message.notification?.body)
        if (!handled) {
            // your own pushes
        }
    }
}
```

Pass every message to `handleMessage`. Campaign pushes are data-only and the SDK shows them itself, and some Hailbell data messages are silent checks that must show nothing.

4. On sign-out call `Hailbell.reset()`. When a user deletes their account, call `Hailbell.deleteData()` and stop if it throws.

The full guide, with entry points, signed-in users, offers, the Play data safety answers and a checklist, is at [hailbell.com/docs](https://hailbell.com/docs). AI coding agents can install the Hailbell skill from `https://hailbell.com/skills/hailbell/SKILL.md`.

## Hailbell

| API | Use |
| --- | --- |
| `Hailbell.configure(context, appId, baseUrl)` | Call once in `Application.onCreate()`. Also creates the reply notification channel. |
| `Hailbell.open(activity)` | Opens the chat as a full-height sheet in its own activity. |
| `Hailbell.unreadCount: StateFlow<Int>` | Replies not yet read; use it for a badge. |
| `Hailbell.teamPhoto: Bitmap?` | A photo bundled in the app for the chat header. Without it, the dashboard photo loads. |
| `Hailbell.identify(userId, name, email, identityToken)` | Who the user is. Only `identityToken`, an HS256 JWT signed with the app's identity secret, links the chat across devices. |
| `Hailbell.identify(name, email) { firebaseIdToken }` | For apps on Firebase Auth: the lambda returns the current ID token. Hailbell verifies it and uses the Firebase uid as the user id. |
| `Hailbell.asksForNotifications` | Default true. After the first message, the chat offers to turn on notifications if they're off. |
| `Hailbell.darkMode` | Default null, which follows the phone. Set true for an always-dark app so the chat matches it. |
| `Hailbell.setMetadata(map)` | Context shown beside the chat, merged with what you sent before. `appVersion`, `os` and `locale` are sent automatically. |
| `Hailbell.track(event, properties)` | A signal shown beside the chat, such as `paywall_decline` or `chat_opened`. |
| `Hailbell.setPaywallPlacements(list)` | The placements an offer can open. |
| `Hailbell.onPaywall` | `(placement, purchased) -> Unit`: open the paywall, and call `purchased()` if they buy. |
| `Hailbell.setPushToken(token)` | The FCM registration token. |
| `Hailbell.reset()` | On sign-out: clears the identity and the cached chat. |
| `suspend Hailbell.deleteData()` | Deletes the user's chat, images and details from Hailbell. Throws if the server can't be reached. |

`Message`, `Attachment` and `Card` are public, for apps that read the conversation.

## HailbellPush

| API | Use |
| --- | --- |
| `handleMessage(context, data, title, body): Boolean` | Call from `FirebaseMessagingService.onMessageReceived`. Shows a reply that arrived with the app open, unless the chat is on screen. Returns false for pushes that aren't Hailbell's. |
| `isHailbell(data)` | True when an FCM data map came from Hailbell. |
| `canNotify(context)` | Whether notifications are allowed for the app and the reply channel. |
| `setTags(map)` | Key/value data for segments; `null` removes a tag. |
| `setMarketing(enabled)` / `optOut()` | Promotional opt-in, or stop all pushes to this device. |
| `offersChannel` | The channel for campaign pushes. Set it before `Hailbell.configure` to reuse a channel your app already has. |

## Files

| File | What it holds |
| --- | --- |
| `Hailbell.kt` | Public entry point, session and retry |
| `Client.kt`, `Models.kt` | HTTP client, install id, public models |
| `ChatModel.kt` | Chat state: cache, paging, sending, uploads, ticks, typing, presence, cards, the WebSocket |
| `ChatActivity.kt`, `ChatScreen.kt` | The sheet and the chat screen, with the header |
| `MessageRow.kt`, `Components.kt`, `Topics.kt`, `Composer.kt` | Bubbles, cards, avatars, topics, ticks and the composer |
| `ChatTheme.kt` | Colours from the dashboard's Chat design |
| `ImageEncoder.kt` | Photo resizing to JPEG under 1.4 MB |
| `NotifyAsk.kt`, `Push.kt` | The notification offer, the reply channel and `HailbellPush` |
| `L10n.kt` | The chat's own words in 14 languages |

## Building from source

`./gradlew :hailbell:assembleRelease` builds the library with JDK 17 or later and the Android SDK. `./gradlew :hailbell:testReleaseUnitTest` runs the unit tests.

## Support and license

Questions: hello@hailbell.com, or the chat on [hailbell.com](https://hailbell.com).

MIT. See [LICENSE](LICENSE).
