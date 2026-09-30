package app.quarterhour

import android.app.Application
import android.webkit.CookieManager
import app.quarterhour.budget.BudgetController
import app.quarterhour.core.net.CookieSource
import app.quarterhour.core.net.Http
import app.quarterhour.core.net.ServerTimeListener
import app.quarterhour.core.news.Domains
import app.quarterhour.data.AppSettings
import app.quarterhour.data.JsonStore
import app.quarterhour.data.NewsRepository
import app.quarterhour.data.SocialRepository
import app.quarterhour.work.ProfileWorker
import kotlinx.coroutines.MainScope
import java.io.File

class QuarterHourApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProfileWorker.schedule(this)
    }
}

/** Manual dependency wiring; one instance per process. */
class AppContainer(app: Application) {
    val scope = MainScope()

    val settings = JsonStore(File(app.filesDir, "settings.json"), AppSettings.serializer()) { AppSettings() }

    private var budgetRef: BudgetController? = null

    val http = Http(
        // Publisher sessions from the in-app sign-in (paywall method 3), only for domains the user subscribes to.
        cookies = CookieSource { url ->
            val host = Domains.of(url) ?: return@CookieSource null
            if (settings.value.subscribedDomains.none { Domains.matches(host, it) }) return@CookieSource null
            runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        },
        serverTime = ServerTimeListener { budgetRef?.onServerTime(it) },
    )

    val budget = BudgetController(app, scope, checkServerTime = { http.get(TIME_CHECK_URL) }).also { budgetRef = it }

    lateinit var social: SocialRepository
        private set

    val news = NewsRepository(app, http, settings) { social.accounts.value.map { it.displayHandle } }

    init {
        social = SocialRepository(app, http, settings) { news.model.value }
    }

    companion object {
        private const val TIME_CHECK_URL = "https://clients3.google.com/generate_204"
    }
}
