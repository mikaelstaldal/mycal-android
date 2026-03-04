package nu.staldal.mycal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nu.staldal.mycal.ui.event.EVENT_COLORS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)?,
    onSaved: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Server Configuration", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { viewModel.updateBaseUrl(it) },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:8080") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.updateUsername(it) },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.save(onSaved) },
                    enabled = state.baseUrl.isNotBlank(),
                ) {
                    Text("Save")
                }

                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    enabled = state.baseUrl.isNotBlank() && !state.isTesting,
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Test Connection")
                }
            }

            state.testResult?.let { result ->
                Text(
                    text = result,
                    color = if (result.startsWith("Connection")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Default Event Color", style = MaterialTheme.typography.titleMedium)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EVENT_COLORS.filter { it.name.isNotEmpty() }.forEach { colorOpt ->
                    val isSelected = state.defaultEventColor == colorOpt.name
                    @OptIn(ExperimentalMaterial3Api::class)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(colorOpt.name)
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorOpt.color)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { viewModel.updateDefaultEventColor(colorOpt.name) },
                        )
                    }
                }
            }
        }
    }
}
