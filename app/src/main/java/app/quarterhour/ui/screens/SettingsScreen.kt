package app.quarterhour.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.quarterhour.core.social.SocialAccount
import app.quarterhour.data.AppSettings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    topics: Set<String>,
    keywords: Set<String>,
    accounts: List<SocialAccount>,
    onInterests: (Set<String>, Set<String>) -> Unit,
    onPublisherSignIn: (String) -> Unit,
    onRemoveSubscription: (String) -> Unit,
    onAddPaywallDomain: (String) -> Unit,
    onAllowPaywallDomain: (String) -> Unit,
    onConnectBluesky: (handle: String, appPassword: String) -> Unit,
    onConnectMastodon: (instance: String) -> Unit,
    onDisconnect: (SocialAccount) -> Unit,
    onOpenProfile: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        InterestPicker(topics, keywords, onInterests)

        Section("Social accounts")
        Text(
            "QuarterHour reads your home timeline to pick 15 photos and videos a day. Tokens are stored encrypted on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        accounts.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${a.network.name.lowercase().replaceFirstChar { it.uppercase() }}: ${a.displayHandle}", modifier = Modifier.weight(1f))
                TextButton(onClick = { onDisconnect(a) }) { Text("Disconnect") }
            }
        }
        BlueskyForm(onConnectBluesky)
        MastodonForm(onConnectMastodon)

        Section("Publisher subscriptions")
        Text(
            "Already pay for a publication? Sign in once and its articles appear in your feed, cleaned up like everything else.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        settings.subscribedDomains.sorted().forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d, modifier = Modifier.weight(1f))
                TextButton(onClick = { onRemoveSubscription(d) }) { Text("Remove") }
            }
        }
        DomainField("Publisher website, e.g. nytimes.com", "Sign in", onPublisherSignIn)

        Section("Paywalled sites")
        Text(
            "Stories from these sites are replaced with a free version of the same story, or hidden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        settings.extraPaywallDomains.sorted().forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAllowPaywallDomain(d) }) { Text("Remove") }
            }
        }
        DomainField("Add a paywalled site", "Add", onAddPaywallDomain)
        if (settings.allowedPaywallDomains.isNotEmpty()) {
            Text("Treated as free: ${settings.allowedPaywallDomains.sorted().joinToString()}", style = MaterialTheme.typography.bodySmall)
        }

        Section("Your profile")
        OutlinedButton(onClick = onOpenProfile) { Text("Open user_profile.md") }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DomainField(hint: String, action: String, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text(hint) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { if (text.isNotBlank()) onSubmit(text.trim()); text = "" }) { Text(action) }
    }
}

@Composable
private fun BlueskyForm(onConnect: (String, String) -> Unit) {
    var handle by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Spacer(Modifier.height(12.dp))
    Text("Bluesky", style = MaterialTheme.typography.titleSmall)
    Text(
        "Use an app password (Bluesky → Settings → Privacy and security → App passwords), not your main password.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(handle, { handle = it }, singleLine = true, label = { Text("Handle") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        password, { password = it }, singleLine = true, label = { Text("App password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onConnect(handle, password); password = "" },
        enabled = handle.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.padding(top = 8.dp),
    ) { Text("Connect Bluesky") }
}

@Composable
private fun MastodonForm(onConnect: (String) -> Unit) {
    var instance by remember { mutableStateOf("") }
    Spacer(Modifier.height(12.dp))
    Text("Mastodon", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        instance, { instance = it }, singleLine = true, label = { Text("Your server, e.g. mastodon.social") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onConnect(instance) }, enabled = instance.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
        Text("Connect Mastodon")
    }
}
