package net.brightroom.mindstock.frontend.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    displayName: String,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TopAppBar(
            title = { Text("mindstock") },
            actions = { TextButton(onClick = onLogout) { Text("ログアウト") } },
        )
        Spacer(Modifier.height(24.dp))
        Text("Hello, $displayName", style = MaterialTheme.typography.headlineMedium)
    }
}
