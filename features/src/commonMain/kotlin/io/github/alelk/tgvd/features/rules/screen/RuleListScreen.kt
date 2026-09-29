package io.github.alelk.tgvd.features.rules.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.features.common.component.EmptyContent
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.component.LoadingContent
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.theme.StatusCancelled
import io.github.alelk.tgvd.features.common.util.categoryLabel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class RuleListScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val client = koinInject<TgVideoDownloaderClient>()
        val navigator = LocalNavigator.currentOrThrow

        var rules by remember { mutableStateOf<List<RuleDto>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var deleteConfirmId by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        fun loadRules() {
            scope.launch {
                try {
                    isLoading = rules.isEmpty()
                    client.getRules().fold(
                        ifLeft = { errorMessage = it.message ?: "Failed to load rules" },
                        ifRight = { response ->
                            rules = response.items
                            errorMessage = null
                        },
                    )
                } finally {
                    isLoading = false
                }
            }
        }

        LaunchedEffect(Unit) { loadRules() }

        // Delete confirmation dialog
        deleteConfirmId?.let { ruleId ->
            AlertDialog(
                onDismissRequest = { deleteConfirmId = null },
                title = { Text("Delete Rule") },
                text = { Text("Are you sure you want to delete this rule?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            client.deleteRule(ruleId)
                            deleteConfirmId = null
                            loadRules()
                        }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") }
                },
            )
        }

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(RuleEditorScreen(ruleId = null, onSaved = { loadRules() })) },
                ) {
                    Icon(TgvdIcons.Add, contentDescription = "Add Rule")
                }
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            ) {
                Text("Rules", style = MaterialTheme.typography.headlineMedium)

                Spacer(modifier = Modifier.height(12.dp))

                errorMessage?.let {
                    ErrorCard(message = it, onRetry = { loadRules() })
                    Spacer(modifier = Modifier.height(8.dp))
                }

                when {
                    isLoading && rules.isEmpty() -> LoadingContent()
                    rules.isEmpty() -> EmptyContent(
                        "No rules yet. Rules let you automate downloads for specific channels.",
                    )
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(rules, key = { it.id }) { rule ->
                                RuleCard(
                                    rule = rule,
                                    onEdit = {
                                        navigator.push(RuleEditorScreen(ruleId = rule.id, onSaved = { loadRules() }))
                                    },
                                    onDelete = { deleteConfirmId = rule.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: RuleDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${categoryLabel(rule.category)} • priority: ${rule.priority}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    if (!rule.enabled) {
                        Surface(
                            color = StatusCancelled.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                "Disabled",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusCancelled,
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Match description
            Text(
                text = describeMatch(rule.match),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Actions
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onEdit) {
                    Icon(TgvdIcons.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(TgvdIcons.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun describeMatch(match: RuleMatchDto): String = when (match) {
    is RuleMatchDto.ChannelId -> "Channel ID: ${match.value}"
    is RuleMatchDto.ChannelName -> "Channel: ${match.value}"
    is RuleMatchDto.TitleRegex -> "Title: /${match.pattern}/"
    is RuleMatchDto.UrlRegex -> "URL: /${match.pattern}/"
    is RuleMatchDto.CategoryEquals -> "Category: ${match.category}"
    is RuleMatchDto.HasTag -> "Tag: ${match.tag}"
    is RuleMatchDto.AllOf -> "All of: ${match.matches.size} conditions"
    is RuleMatchDto.AnyOf -> "Any of: ${match.matches.size} conditions"
}
