package app.quarterhour.data

import android.content.Context
import app.quarterhour.core.net.Http
import app.quarterhour.core.social.BlueskyClient
import app.quarterhour.core.social.MastodonClient
import app.quarterhour.core.social.SocialAccount
import app.quarterhour.core.social.SocialPicker
import app.quarterhour.core.social.SocialPost
import app.quarterhour.core.profile.InterestModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.LocalDate

@Serializable
data class EncryptedBlob(val data: String? = null)

@Serializable
data class DailySocial(
    val day: String = "",
    val posts: List<SocialPost> = emptyList(),
    /** Ids shown on earlier days, so the reel never repeats. Capped. */
    val history: List<String> = emptyList(),
)

/** Connected Bluesky/Mastodon accounts and the day's 15 posts. */
class SocialRepository(
    context: Context,
    http: Http,
    private val settings: JsonStore<AppSettings>,
    private val model: () -> InterestModel,
) {
    private val cipher = KeystoreCipher()
    private val bluesky = BlueskyClient(http)
    private val mastodon = MastodonClient(http)
    private val accountsStore = JsonStore(File(context.filesDir, "accounts.json"), EncryptedBlob.serializer()) { EncryptedBlob() }
    private val listSerializer = ListSerializer(SocialAccount.serializer())

    val daily = JsonStore(File(context.filesDir, "social_today.json"), DailySocial.serializer()) { DailySocial() }

    private val _accounts = MutableStateFlow(decodeAccounts())
    val accounts: StateFlow<List<SocialAccount>> = _accounts.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Loads today's picks once per day; later calls return the same 15. */
    suspend fun loadToday(force: Boolean = false) {
        val today = LocalDate.now().toString()
        val current = daily.value
        if (!force && current.day == today && current.posts.isNotEmpty()) return
        if (_accounts.value.isEmpty()) return
        _loading.value = true
        _error.value = null
        try {
            val posts = fetchAll()
            val shown = current.history.toSet() + if (current.day == today) emptySet() else current.posts.map { it.id }
            val picks = SocialPicker(model()).pick(posts, shown, System.currentTimeMillis()).picks
            val history = (current.history + current.posts.map { it.id }).distinct().takeLast(HISTORY_CAP)
            daily.set(DailySocial(today, picks, history))
        } catch (e: Exception) {
            _error.value = e.message ?: "Couldn't load your timelines"
        } finally {
            _loading.value = false
        }
    }

    private suspend fun fetchAll(): List<SocialPost> = coroutineScope {
        _accounts.value.map { account ->
            async {
                runCatching {
                    when (account) {
                        is SocialAccount.Bluesky -> {
                            val (posts, refreshed) = bluesky.timeline(account)
                            if (refreshed != account) replaceAccount(account, refreshed)
                            posts
                        }
                        is SocialAccount.Mastodon -> mastodon.homeTimeline(account)
                    }
                }.onFailure { _error.value = "${account.displayHandle}: ${it.message}" }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    suspend fun connectBluesky(handle: String, appPassword: String) {
        val account = bluesky.signIn(handle, appPassword)
        saveAccounts(_accounts.value.filterNot { it is SocialAccount.Bluesky && it.did == account.did } + account)
    }

    /** Registers the app on the instance and returns the browser URL to approve access. */
    suspend fun startMastodon(instance: String): String {
        val reg = mastodon.registerApp(instance)
        settings.update { it.copy(pendingMastodon = reg) }
        return mastodon.authorizeUrl(reg)
    }

    suspend fun completeMastodon(code: String) {
        val reg = settings.value.pendingMastodon ?: throw IllegalStateException("No Mastodon sign-in in progress")
        val account = mastodon.exchangeCode(reg, code)
        settings.update { it.copy(pendingMastodon = null) }
        saveAccounts(_accounts.value.filterNot { it is SocialAccount.Mastodon && it.instance == account.instance } + account)
    }

    suspend fun disconnect(account: SocialAccount) = saveAccounts(_accounts.value - account)

    private suspend fun replaceAccount(old: SocialAccount, new: SocialAccount) =
        saveAccounts(_accounts.value.map { if (it == old) new else it })

    private suspend fun saveAccounts(list: List<SocialAccount>) {
        _accounts.value = list
        accountsStore.set(EncryptedBlob(cipher.encrypt(JsonStore.json.encodeToString(listSerializer, list))))
    }

    private fun decodeAccounts(): List<SocialAccount> {
        val blob = accountsStore.value.data ?: return emptyList()
        val plain = cipher.decrypt(blob) ?: return emptyList()
        return runCatching { JsonStore.json.decodeFromString(listSerializer, plain) }.getOrDefault(emptyList())
    }

    companion object {
        private const val HISTORY_CAP = 1000
    }
}
