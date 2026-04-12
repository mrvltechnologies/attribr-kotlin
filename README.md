# Attribr Android SDK

Install attribution + 30-day retention tracking for Android apps.

**Zero third-party dependencies.** Uses only Android framework APIs and the Java standard library.

---

## Requirements

| Item | Minimum |
|---|---|
| Android | 8.0 (API 26) |
| Kotlin | 2.0 |
| Gradle | 8.7 |
| compileSdk | 35 |

---

## Installation

### Option A — Local path (MRVL apps, monorepo)

In your app's `settings.gradle.kts`:

```kotlin
includeBuild("/Users/AMarie/Developer/Marvel-Technologies/Attribr-by-MRVL/sdk/kotlin")
```

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.mrvltechnologies:attribr:1.0.0")
}
```

### Option B — Maven Central (once published)

```kotlin
dependencies {
    implementation("com.mrvltechnologies:attribr:1.0.0")
}
```

---

## Quick Start (3 lines)

### 1. Initialise in `Application.onCreate()`

```kotlin
import com.mrvltechnologies.attribr.Attribr
import com.mrvltechnologies.attribr.ConsentState

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Attribr.initialize(this, apiKey = "attr_live_myapp00x…")
        Attribr.setConsent(ConsentState.GRANTED)
    }
}
```

### 2. Track every foreground event

**Option A — ProcessLifecycleOwner (recommended, no per-Activity boilerplate):**

```kotlin
// In Application.onCreate(), after initialize():
ProcessLifecycleOwner.get().lifecycle.addObserver(
    LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) Attribr.trackLaunch()
    }
)
```

Requires `implementation("androidx.lifecycle:lifecycle-process:2.8.x")`.

**Option B — Base Activity:**

```kotlin
abstract class BaseActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()
        Attribr.trackLaunch()
    }
}
```

---

## API Reference

### `initialize(context, apiKey, config?)`

Initialises the SDK. Call once from `Application.onCreate()`. Subsequent calls are silently ignored.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `context` | `Context` | — | Any context — unwrapped to `applicationContext` internally |
| `apiKey` | `String` | — | Your Attribr live key (`attr_live_…`, 50 chars) |
| `config` | `AttribrConfiguration` | default | Optional SDK configuration |

---

### `setConsent(state)`

Informs the SDK of the user's GDPR consent decision.

```kotlin
Attribr.setConsent(ConsentState.GRANTED)   // after user accepts
Attribr.setConsent(ConsentState.REVOKED)   // after user declines
```

The SDK fires **no network requests** until `GRANTED` is set. When `GRANTED` is set, any events queued due to prior network failures are flushed immediately.

---

### `trackLaunch()`

Records an app foreground event. Call on every `onResume()` / `ON_START` lifecycle event. Silently ignored until consent is `GRANTED`.

---

### `attributeInstall(code, source?, utmSource?, utmCampaign?)`

Records where a user came from (referral link, UTM tag, promo code).

```kotlin
// Referral from another MRVL app
Attribr.attributeInstall(
    code   = "RIPPL-REF-XYZ",
    source = AttributionSource.RIPPL,
)

// UTM-tagged marketing link
Attribr.attributeInstall(
    code        = "spring2026",
    source      = AttributionSource.UTM,
    utmSource   = "instagram",
    utmCampaign = "spring_launch",
)
```

---

### `trackEvent(name, value?, currency?, metadata?)`

Tracks a custom event. Use snake_case names by convention.

```kotlin
Attribr.trackEvent("subscription_started")

Attribr.trackEvent(
    name     = "purchase",
    value    = 999.0,        // pence
    currency = "GBP",
    metadata = mapOf("plan" to "pro", "period" to "monthly"),
)
```

---

### `deleteAllData(callback?)`

Deletes all data for this device from Attribr's servers. Implements GDPR right to erasure. Wire this to a "Delete my data" button in your Settings screen.

```kotlin
Attribr.deleteAllData { success ->
    runOnUiThread {
        if (success) showToast("Data deleted")
        else showToast("Deletion failed — please try again")
    }
}
```

---

## Configuration

```kotlin
val config = AttribrConfiguration(
    baseUrl          = "https://pblzmoxcwpqywuyubdim.supabase.co/functions/v1",
    maxQueueSize     = 500,      // max offline queue depth
    requestTimeoutMs = 15_000,   // per-request timeout
    debugLogging     = false,    // Logcat debug output
    maxRetries       = 3,        // retries on 429/5xx
)
Attribr.initialize(this, apiKey = "attr_live_…", config = config)
```

---

## Privacy

- **No advertising ID used.** The device fingerprint is `SHA-256(ANDROID_ID + packageName + salt)`.
- `ANDROID_ID` is scoped to your signing key (Android 8.0+) — it resets on factory reset but not reinstall, preventing the reinstall-exploit that invalidates local-only free-tier limits.
- The raw `ANDROID_ID` is never transmitted — only the one-way hash.
- No `READ_PHONE_STATE`, `ADVERTISING_ID`, or any dangerous permission is required.
- Declare in your Play Console Data Safety section: **Analytics → App Interactions → Not linked to identity → Not used for tracking**.

---

## Offline Resilience

Events that fail to deliver (network unavailable, server error) are saved to `SharedPreferences` and retried the next time:
1. A network request succeeds (the queue is flushed after each successful delivery), or
2. `setConsent(GRANTED)` is called (which triggers an immediate flush).

The queue holds up to 500 events. When full, the oldest event is evicted.

---

## API Keys

Generate keys using the SQL in `Attribr-by-MRVL/HANDOFF.md`. Each MRVL app has its own `attr_live_…` key already inserted into Supabase. Never commit API keys to source control — store them in `local.properties` or as build config fields.

---

## Architecture

```
Attribr (public singleton)
├── ConsentManager     — GDPR state, SharedPreferences-backed
├── DeviceIdentifier   — SHA-256 fingerprint, SharedPreferences-cached
├── EventQueue         — Offline buffer, SharedPreferences-backed, max 500
├── NetworkClient      — HttpURLConnection, X-Attribr-Key, exponential back-off
└── AttribrLogger      — Logcat wrapper, debug-gated
```

Network I/O runs on a single daemon thread (`AttribrSDK-io`) — never blocks the calling thread.
