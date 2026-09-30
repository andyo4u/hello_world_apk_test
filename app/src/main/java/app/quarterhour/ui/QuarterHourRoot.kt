package app.quarterhour.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.quarterhour.AppContainer
import app.quarterhour.MainActivity
import app.quarterhour.core.profile.Signal
import app.quarterhour.ui.screens.FilteredScreen
import app.quarterhour.ui.screens.NewsScreen
import app.quarterhour.ui.screens.OnboardingScreen
import app.quarterhour.ui.screens.ProfileScreen
import app.quarterhour.ui.screens.PublisherLoginScreen
import app.quarterhour.ui.screens.ReaderScreen
import app.quarterhour.ui.screens.SettingsScreen
import app.quarterhour.ui.screens.SocialScreen
import kotlinx.coroutines.launch

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val READER = "reader/{id}"
    const val FILTERED = "filtered"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val PUBLISHER = "publisher/{domain}"
    fun reader(id: String) = "reader/${Uri.encode(id)}"
    fun publisher(domain: String) = "publisher/${Uri.encode(domain)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarterHourRoot(
    container: AppContainer,
    openExternal: (String) -> Unit,
    shareProfile: () -> Unit,
    toast: (String) -> Unit,
) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val news = container.news
    val social = container.social
    val budget = container.budget

    val budgetUi by budget.ui.collectAsStateWithLifecycle()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val feed by news.feed.flow.collectAsStateWithLifecycle()
    val model by news.model.flow.collectAsStateWithLifecycle()
    val refreshing by news.refreshing.collectAsStateWithLifecycle()
    val newsError by news.error.collectAsStateWithLifecycle()
    val dailyLog by news.dailyLog.flow.collectAsStateWithLifecycle()
    val daily by social.daily.flow.collectAsStateWithLifecycle()
    val accounts by social.accounts.collectAsStateWithLifecycle()
    val socialLoading by social.loading.collectAsStateWithLifecycle()
    val socialError by social.error.collectAsStateWithLifecycle()

    val today = java.time.LocalDate.now().toString()
    val readToday = if (dailyLog.day == today) dailyLog.read else emptyList()

    // One-time two-minute warning.
    LaunchedEffect(budgetUi.showWarning) {
        if (budgetUi.showWarning) {
            // Launched separately: warningShown() flips the key and would cancel this effect.
            scope.launch { snackbar.showSnackbar("2 minutes left today") }
            budget.warningShown()
        }
    }
    // Minutes used, for the profile's reading-habits section.
    val usedMinutes = ((budgetUi.limitMillis - budgetUi.remainingMillis) / 60_000).toInt()
    LaunchedEffect(usedMinutes) { news.recordMinutesToday(usedMinutes) }

    val openProfile = { nav.navigate(Routes.PROFILE) }
    // Social posts already counted as viewed this session.
    val seenPosts = remember { mutableSetOf<String>() }
    val start = remember { if (settings.onboarded) Routes.HOME else Routes.ONBOARDING }

    NavHost(nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen { topics, keywords ->
                scope.launch {
                    news.setFollowed(topics, keywords)
                    container.settings.update { it.copy(onboarded = true) }
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    news.refresh()
                }
            }
        }

        composable(Routes.HOME) {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            LaunchedEffect(Unit) { if (news.isStale()) news.refresh() }
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("QuarterHour", style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { Box(Modifier.padding(start = 8.dp)) { BudgetRing(budgetUi) } },
                        actions = {
                            IconButton(onClick = { nav.navigate(Routes.FILTERED) }) { Icon(Icons.Default.FilterAlt, contentDescription = "Filtered stories") }
                            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Newspaper, null) }, label = { Text("News") })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.PhotoLibrary, null) }, label = { Text("Social") })
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    Counted(budgetUi, budget::enterCounted, budget::exitCounted, readToday, openProfile) {
                        if (tab == 0) {
                            NewsScreen(
                                items = feed.items,
                                refreshing = refreshing,
                                error = newsError,
                                filteredCount = feed.filtered.size,
                                onRefresh = { scope.launch { news.refresh() } },
                                onOpen = { item ->
                                    scope.launch { news.recordOpen(item) }
                                    nav.navigate(Routes.reader(item.article.id))
                                },
                                onThumb = { item, up -> scope.launch { news.thumb(item, up) } },
                                onLessLikeThis = { item -> scope.launch { news.lessLikeThis(item) } },
                                onBlockSource = { item -> scope.launch { news.blockSource(item.article.sourceName) } },
                                onShowFiltered = { nav.navigate(Routes.FILTERED) },
                            )
                        } else {
                            SocialScreen(
                                posts = daily.posts,
                                hasAccounts = accounts.isNotEmpty(),
                                loading = socialLoading,
                                error = socialError,
                                onLoad = { scope.launch { social.loadToday() } },
                                onConnect = { nav.navigate(Routes.SETTINGS) },
                                onSeen = { post -> if (seenPosts.add(post.id)) scope.launch { news.socialSignal(post, Signal.Opened) } },
                                onThumb = { post, up -> scope.launch { news.socialSignal(post, if (up) Signal.ThumbUp else Signal.ThumbDown) } },
                            )
                        }
                    }
                }
            }
        }

        composable(Routes.READER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val item = news.item(id)
            SubScreen(nav, item?.article?.sourceName ?: "Article") {
                Counted(budgetUi, budget::enterCounted, budget::exitCounted, readToday, openProfile) {
                    if (item == null) {
                        Text("This story is no longer in your feed.", modifier = Modifier.padding(24.dp))
                    } else {
                        ReaderScreen(
                            item = item,
                            onDwell = { s -> scope.launch { news.recordDwell(item, s) } },
                            onThumb = { up -> scope.launch { news.thumb(item, up) } },
                        )
                    }
                }
            }
        }

        composable(Routes.FILTERED) {
            SubScreen(nav, "Filtered for you") {
                FilteredScreen(
                    items = feed.filtered,
                    onNotClickbait = { f -> scope.launch { news.notClickbait(f); toast("Got it — similar headlines will get through") } },
                    onMarkAi = { f -> scope.launch { news.markAiFarm(f); toast("${f.sourceDomain} will be hidden as AI content") } },
                    onDismiss = { f -> scope.launch { news.removeFiltered(f) } },
                )
            }
        }

        composable(Routes.SETTINGS) {
            SubScreen(nav, "Settings") {
                SettingsScreen(
                    settings = settings,
                    topics = model.followedTopics,
                    keywords = model.followedKeywords,
                    accounts = accounts,
                    onInterests = { t, k -> scope.launch { news.setFollowed(t, k) } },
                    onPublisherSignIn = { input ->
                        MainActivity.normalizeDomain(input)?.let { nav.navigate(Routes.publisher(it)) } ?: toast("That doesn't look like a website")
                    },
                    onRemoveSubscription = { d -> scope.launch { news.removeSubscription(d) } },
                    onAddPaywallDomain = { d -> scope.launch { news.addPaywallDomain(d) } },
                    onAllowPaywallDomain = { d -> scope.launch { news.allowPaywallDomain(d) } },
                    onConnectBluesky = { h, p ->
                        scope.launch {
                            runCatching { social.connectBluesky(h, p) }
                                .onSuccess { toast("Bluesky connected"); social.loadToday(force = true) }
                                .onFailure { toast(it.message ?: "Bluesky sign-in failed") }
                        }
                    },
                    onConnectMastodon = { instance ->
                        scope.launch {
                            runCatching { social.startMastodon(instance) }
                                .onSuccess(openExternal)
                                .onFailure { toast(it.message ?: "Couldn't reach that Mastodon server") }
                        }
                    },
                    onDisconnect = { a -> scope.launch { social.disconnect(a) } },
                    onOpenProfile = { nav.navigate(Routes.PROFILE) },
                )
            }
        }

        composable(Routes.PROFILE) {
            var text by remember { mutableStateOf("") }
            LaunchedEffect(model) {
                news.writeProfile()
                text = news.profileFile.takeIf { it.exists() }?.readText().orEmpty()
            }
            SubScreen(nav, "user_profile.md") {
                ProfileScreen(text, onShare = shareProfile, onReset = { scope.launch { news.resetProfile() } })
            }
        }

        composable(Routes.PUBLISHER, arguments = listOf(navArgument("domain") { type = NavType.StringType })) { entry ->
            val domain = entry.arguments?.getString("domain").orEmpty()
            SubScreen(nav, domain) {
                PublisherLoginScreen(domain) {
                    scope.launch {
                        news.addSubscription(domain)
                        toast("$domain added — its stories will appear on your next refresh")
                        nav.popBackStack()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(nav: NavHostController, title: String, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding -> Box(Modifier.padding(padding)) { content() } }
}
