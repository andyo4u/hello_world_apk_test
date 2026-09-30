# QuarterHour

The best 15 minutes of your day: an Android app for clean news and social highlights, with a hard daily limit.

- **News from Google News, personalized on the device.** QuarterHour learns what you like from the topics you pick, what you open, how long you read, and thumbs up/down.
- **A clean reader.** Articles show text, photos and captions only. Ads, trackers, pop-ups, newsletter boxes, "recommended" rails and comments are removed. There is no WebView and no publisher JavaScript.
- **No paywalls.** Paywalled stories are never shown. QuarterHour doesn't get around paywalls; it uses three legitimate methods instead:
  1. **Detect and hide** a paywalled story. Signals are the publisher domain, schema.org `isAccessibleForFree`, paywall vendor scripts or markup, and cut-off text.
  2. **Swap in the same story from a free outlet.** Candidates come from Google News' coverage cluster or a headline search. Wire services (AP, Reuters) are preferred.
  3. **Use your own subscriptions.** Sign in once to a publication you pay for. Its articles then appear, cleaned up like everything else.
- **Filters.** Clickbait headlines and obvious AI-generated articles are removed. AI signals are leftover model phrases, template text, sources you mark, and IPTC/C2PA "AI-generated" image metadata. The *Filtered* screen shows what was hidden and why, and lets you correct mistakes.
- **15 social posts a day** from **Bluesky** and **Mastodon**: the best photos and short videos from people you follow. Ads, sponsored posts, sensitive-labelled posts and bait are left out.
- **`user_profile.md`.** A readable summary of your interests is kept up to date on the device. You can view, share or reset it, and it never leaves the phone unless you share it.
- **15 minutes a day**, shared by news and social. A ring shows the time left and you get a warning at 2 minutes. At zero, a lock screen says "See you tomorrow" and the budget resets at local midnight. Changing the clock, editing the stored state or clearing app data doesn't get you more time.

Why Bluesky and Mastodon rather than Instagram/Facebook: Meta closed personal-feed access to third-party apps in December 2024. Scraping the feed with your login breaks Meta's terms and can get your account banned. See [`docs/PROMPT.md`](docs/PROMPT.md) for the full spec.

## Project layout

| Module | What's in it |
|---|---|
| `core/` | Pure Kotlin/JVM, with all the logic: RSS parsing, Google News link resolution, reader extraction, paywall detection and free-source finder, clickbait/AI filters, interest model and ranker, profile writer, daily budget, Bluesky/Mastodon clients, social picker. Fully unit-tested. |
| `app/` | Android (Jetpack Compose) UI and platform glue: file-backed stores, Keystore signing and encryption, the budget controller, the nightly WorkManager job, and the screens. |

## Build

Requires JDK 17+ and the Android SDK (compileSdk 35).

```bash
./gradlew :core:test              # logic tests, no Android SDK needed
./gradlew :app:testDebugUnitTest  # Robolectric UI tests
./gradlew :app:assembleDebug      # APK in app/build/outputs/apk/debug/
```

Without an Android SDK, only `:core` is included in the build (see `settings.gradle.kts`). GitHub Actions (`.github/workflows/android.yml`) runs all tests and uploads the debug APK as a build artifact.

## Known limitations

- Google News article links are resolved through Google's own unofficial redirect endpoint, which may change. Articles that can't be resolved are hidden, never shown with a paywall.
- Paywall detection errs on the side of hiding. The paywall list can be edited in Settings.
- The AI-farm list starts empty. Sources are added from the Filtered screen, so the app ships no unverified accusations.
- The time limit can't survive uninstalling and reinstalling the app.
- Signing in to a publisher uses that publisher's own page in a WebView, which may show its ads until you're signed in.
