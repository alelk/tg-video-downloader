package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.settings.model.SettingsForm

private val proxyTypes = listOf("HTTP", "SOCKS5")

@Composable
internal fun ProxySection(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    SectionCard(title = "Proxy") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Enable Proxy", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = form.proxyEnabled, onCheckedChange = { onFormChange(form.copy(proxyEnabled = it)) })
        }

        if (form.proxyEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            ProxyTypeDropdown(form, onFormChange)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.proxyHost,
                    onValueChange = { onFormChange(form.copy(proxyHost = it)) },
                    label = { Text("Host") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = form.proxyPort,
                    onValueChange = { onFormChange(form.copy(proxyPort = it)) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.proxyUsername,
                    onValueChange = { onFormChange(form.copy(proxyUsername = it)) },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.proxyPassword,
                    onValueChange = { onFormChange(form.copy(proxyPassword = it)) },
                    label = { Text("Password") },
                    placeholder = { Text("unchanged") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyTypeDropdown(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    var typeExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
        OutlinedTextField(
            value = form.proxyType,
            onValueChange = {},
            readOnly = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
            proxyTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type) },
                    onClick = {
                        onFormChange(form.copy(proxyType = type))
                        typeExpanded = false
                    },
                )
            }
        }
    }
}
