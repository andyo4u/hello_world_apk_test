package app.quarterhour.data

import app.quarterhour.core.social.MastodonClient
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val onboarded: Boolean = false,
    val language: String = "en",
    val country: String = "US",
    /** Publisher domains the user pays for and has signed in to (paywall method 3). */
    val subscribedDomains: Set<String> = emptySet(),
    /** User additions/removals on top of the built-in paywall list. */
    val extraPaywallDomains: Set<String> = emptySet(),
    val allowedPaywallDomains: Set<String> = emptySet(),
    /** Domains the user marked as AI content farms. */
    val aiFarmDomains: Set<String> = emptySet(),
    /** Per-rule clickbait weight changes learned from "not clickbait" feedback. */
    val clickbaitAdjustments: Map<String, Double> = emptyMap(),
    /** Mastodon app registration waiting for the OAuth redirect. */
    val pendingMastodon: MastodonClient.AppRegistration? = null,
)
