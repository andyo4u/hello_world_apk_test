package app.quarterhour

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import app.quarterhour.core.news.Domains
import app.quarterhour.ui.QuarterHourRoot
import app.quarterhour.ui.theme.QuarterHourTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as QuarterHourApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthRedirect(intent)
        setContent {
            QuarterHourTheme {
                QuarterHourRoot(
                    container = container,
                    openExternal = ::openExternal,
                    shareProfile = ::shareProfile,
                    toast = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent)
    }

    /** quarterhour://oauth/mastodon?code=… from the instance's authorize page. */
    private fun handleOAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "quarterhour" || data.host != "oauth") return
        val code = data.getQueryParameter("code")
        if (code == null) {
            Toast.makeText(this, "Mastodon sign-in was cancelled", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            runCatching { container.social.completeMastodon(code) }
                .onSuccess { Toast.makeText(this@MainActivity, "Mastodon connected", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this@MainActivity, it.message ?: "Mastodon sign-in failed", Toast.LENGTH_LONG).show() }
        }
    }

    /** Used only for account sign-in pages (Mastodon OAuth), never for articles. */
    private fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun shareProfile() {
        val file = container.news.profileFile
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share user_profile.md"))
    }

    companion object {
        fun normalizeDomain(input: String): String? =
            Domains.of(if ("://" in input) input.trim() else "https://${input.trim()}")
    }
}
