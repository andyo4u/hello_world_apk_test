package app.quarterhour.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Paywall method 3: the user signs in to a publisher they already pay for.
 * The session cookie stays in the WebView cookie store and is sent only to
 * that publisher's domain when fetching its articles.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PublisherLoginScreen(domain: String, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Sign in to $domain with your subscription, then tap Done. QuarterHour never sees your password.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    settings.javaScriptEnabled = true // publisher login forms need it
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl("https://$domain/")
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Button(
            onClick = { CookieManager.getInstance().flush(); onDone() },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) { Text("Done — I'm signed in") }
    }
}
