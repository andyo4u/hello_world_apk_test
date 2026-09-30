# QuarterHour — Improved Build Prompt

> A calm, ad-free news + social viewer that gives you the best 15 minutes of your day, then gets out of your way.

## 1. Name

**QuarterHour** (package `app.quarterhour`). The name says what the app does: you get 15 minutes a day.

A name search in September 2026 found no news app called "QuarterHour" or "Quarter Hour". The names that came up were only loosely related ("Hourly News", a quarter-hour chime app). Backup names: **Fifteen Fresh**, **Skim15**. Check trademarks and the Play Store again before publishing.

### Similar apps that already exist
| App | Overlap | How QuarterHour differs |
|---|---|---|
| Google News | Personalized headlines | Shows ads and paywalled links, and has no time limit |
| Sip (Product Hunt) | Short daily digest | Tech news only, no social content |
| Glance lock screen | News on the lock screen | Ad-supported and preinstalled |
| Mindful / Forest / StayFocusd | Daily time limits | Only block apps; they don't show any content |

No existing app combines a personalized clean-reader, filtering, a social highlights reel and a hard daily limit.

## 2. Goal

Build a native Android app (Kotlin, Jetpack Compose, minSdk 26, targetSdk 35). It shows a personalized feed of news articles and social media posts with no ads, no paywalled articles, no clickbait and no obvious AI-generated content. It strictly limits total use to **15 minutes per day**.

## 3. Features and acceptance criteria

### 3.1 Learning what the user likes
- **Onboarding:** the user picks topics (Google News topic list) and optionally follows specific publishers or keywords.
- **Source:** Google News RSS (`news.google.com/rss`, plus the `/topics/…` and `/search?q=…` feeds), fetched for the user's topics and region. *Note: Google has no API that exposes a user's Google News history or interests. Preferences are learned inside the app instead.*
- **Learning signals** (all stored on the device): opened articles, time spent reading each one, thumbs up/down, and "less like this" on a topic or source. Each topic gets a score that slowly fades over time.
- **Acceptance:** after about 20 interactions, the feed ranking clearly reflects what the user engaged with.

### 3.2 Clean reader view
- Open each article in a native reader view built with Readability4J (or similar). It keeps the headline, byline, date, body text, **inline photos and captions**, and embedded video poster images.
- Remove ads, trackers, pop-ups, newsletter prompts, "recommended for you" rails and comment sections. Show no WebView chrome, run no JavaScript and use no third-party ad SDKs.
- Always show a link to the original article and credit the publisher.

### 3.3 Paywalls: detect, skip, and find a free source
The app **does not bypass paywalls**. Getting around a paywall usually breaks the publisher's terms of service and can break copyright law. The app never shows the user a paywalled article or an ad. It uses three legitimate methods:
1. **Detect and hide.** Mark an article as paywalled if any of these are true:
   - its schema.org data says `isAccessibleForFree: false`
   - the page contains known paywall markup (for example Piano, Tinypass or Poool)
   - the extracted text is too short or cut off
   - the publisher is on a known-paywall list that users can update

   Paywalled articles are removed before the feed is ranked.
2. **Find a free version of the same story.** Google News groups coverage of one story from several outlets. If the top result is paywalled, show the best free source in that group instead, or use wire copy from AP or Reuters syndication.
3. **Use the user's own subscriptions.** The user can sign in to publishers they already pay for, in a secure in-app login. Those articles open in the clean reader with the user's own access.

Acceptance: no article in the feed opens to a paywall prompt, and no ad ever renders.

### 3.4 Filtering clickbait and AI content
- **Clickbait:** a small on-device classifier (TFLite) plus rules. Examples of what it flags: "You won't believe…", headlines that hide the key fact, listicle bait, too many superlatives or ALL-CAPS words, and headlines that don't match the article body.
- **Obvious AI content:** flag a source when it
  - is on a known AI content-farm list
  - publishes large numbers of articles with no author named
  - has repetitive, template-like text
  - shows leftover AI phrasing ("As an AI language model", "In conclusion, it's important to note")
  - carries C2PA or IPTC "AI-generated" metadata on its images
- **Optional:** an LLM check (for example the Claude API) for borderline cases. It is off by default because it needs an API key and costs money.
- Every hidden article can be viewed in a "Filtered (n)" drawer, so the user can report false positives. Those reports feed back into the filter.

### 3.5 Social highlights: 15 items a day
*This needs a decision (see §5).* Meta no longer lets outside apps read a personal Instagram or Facebook feed (the Instagram Basic Display API was shut down on 4 Dec 2024). Scraping the feed with the user's login breaks Meta's terms and can get the account banned. Possible approaches:
- **A. Curated public accounts.** The user lists public Instagram/Facebook accounts or post URLs. Posts load through Meta's oEmbed endpoint. This needs a Meta developer app token.
- **B. Business/Creator Graph API.** This only works if the user's own account is a Business or Creator account, and it only shows that account's own media, not their feed.
- **C. Open networks.** Use Bluesky, Mastodon, Reddit and similar networks, whose APIs support reading the user's own feed with their login.

Under any option: pick the 15 best image or short-video posts a day (ranked with the same interest scores), play videos with Media3/ExoPlayer, and remove ads and sponsored posts.

### 3.6 `user_profile.md`
- A readable Markdown file saved in app storage and rewritten every night by a WorkManager job.
- Sections: top topics (with scores and trend), favorite and blocked sources, reading times, how much content was filtered, social interests, and the last updated time.
- The user can view it in the app, export or share it, and delete it. **It never leaves the device** unless the user shares it.

### 3.7 15-minute daily limit
- A single timer counts time while the news or social screens are in the foreground. It pauses when the app is in the background or the screen is off.
- A progress ring shows the time left. A gentle warning appears at 2 minutes left.
- At 0:00 a lock screen says "See you tomorrow" and shows a summary of what the user read. The timer resets at local midnight.
- To make the limit hard to get around:
  - the timer is stored and signed in DataStore
  - changing the device clock is detected by comparing elapsed-realtime to wall-clock time
  - clearing app data sets the day to "used up" rather than "fresh"
- Settings, `user_profile.md` and the Filtered drawer do not use up time.

## 4. Technical constraints
- Architecture: MVVM, Hilt, Room (articles, interest scores), DataStore, WorkManager, OkHttp/Retrofit, Coil, Media3.
- Privacy: no analytics SDKs, no ad SDKs, and all personalization stays on the device.
- Tests: unit tests for the paywall detector, the clickbait classifier rules, interest scoring and timer logic. Include a UI test for the lockout.
- CI: a GitHub Actions workflow that builds a debug APK and runs the tests.

## 5. Open decisions
1. Which option for social content: A, B, C, or a mix?
2. Should the optional LLM filter be included? It needs an API key.
3. Should the 15 minutes be one shared budget, or split (for example 12 minutes of news and 3 of social)?
4. Keep the name QuarterHour?

## 6. Out of scope
- Paywall bypass techniques: archive mirrors, pretending to be a search crawler, script-blocking tricks, cookie resets.
- Scraping Instagram/Facebook, or storing the user's Meta password.
- iOS, web and multi-device sync (a later version).
