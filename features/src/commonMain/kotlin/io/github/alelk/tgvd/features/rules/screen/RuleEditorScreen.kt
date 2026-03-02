package io.github.alelk.tgvd.features.rules.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.PostProcessPolicyDto
import io.github.alelk.tgvd.api.contract.storage.StoragePolicyDto
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.util.categoryLabel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class RuleEditorScreen(
    private val ruleId: String?,
    private val onSaved: () -> Unit,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val client = koinInject<TgVideoDownloaderClient>()
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val isEdit = ruleId != null

        // Form state
        var name by remember { mutableStateOf("") }
        var enabled by remember { mutableStateOf(true) }
        var priority by remember { mutableStateOf("0") }
        var category by remember { mutableStateOf(CategoryDto.OTHER) }

        // Match type
        var matchType by remember { mutableStateOf("channel-name") }
        var matchValue by remember { mutableStateOf("") }
        var matchIgnoreCase by remember { mutableStateOf(true) }

        // Storage
        var originalTemplate by remember { mutableStateOf("/media/{category}/{channelName}/{title} [{videoId}].{ext}") }

        // Status
        var isLoadingRule by remember { mutableStateOf(isEdit) }
        var isSaving by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // Load existing rule if editing
        LaunchedEffect(ruleId) {
            if (ruleId != null) {
                try {
                    val rule = client.getRule(ruleId)
                    name = rule.name
                    enabled = rule.enabled
                    priority = rule.priority.toString()
                    category = rule.category
                    originalTemplate = rule.storagePolicy.originalTemplate
                    populateMatch(rule) { type, value, ignoreCase ->
                        matchType = type
                        matchValue = value
                        matchIgnoreCase = ignoreCase
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to load rule"
                } finally {
                    isLoadingRule = false
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEdit) "Edit Rule" else "Create Rule") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(TgvdIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (isLoadingRule) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Enabled + Priority
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enabled")
                    }
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it.filter { c -> c.isDigit() } },
                        label = { Text("Priority") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                // Category
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryDto.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(categoryLabel(cat)) },
                        )
                    }
                }

                // Match Type
                val matchTypes = listOf(
                    "channel-id" to "Channel ID",
                    "channel-name" to "Channel Name",
                    "title-regex" to "Title Pattern",
                    "url-regex" to "URL Pattern",
                )
                Text("Match Condition", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    matchTypes.forEach { (value, label) ->
                        FilterChip(
                            selected = matchType == value,
                            onClick = { matchType = value },
                            label = { Text(label) },
                        )
                    }
                }

                OutlinedTextField(
                    value = matchValue,
                    onValueChange = { matchValue = it },
                    label = { Text(matchTypes.find { it.first == matchType }?.second ?: "Value") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (matchType == "channel-name") {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = matchIgnoreCase, onCheckedChange = { matchIgnoreCase = it })
                        Text("Ignore case")
                    }
                }

                // Storage template
                OutlinedTextField(
                    value = originalTemplate,
                    onValueChange = { originalTemplate = it },
                    label = { Text("Path Template") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Text(
                    "Variables: {channelName}, {title}, {artist}, {seriesName}, {season}, {episode}, {videoId}, {year}, {date}, {ext}, {category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Error
                errorMessage?.let { ErrorCard(message = it) }

                // Save button
                Button(
                    onClick = {
                        if (name.isBlank() || matchValue.isBlank()) {
                            errorMessage = "Name and match value are required"
                            return@Button
                        }
                        isSaving = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val request = CreateRuleRequestDto(
                                    name = name,
                                    enabled = enabled,
                                    priority = priority.toIntOrNull() ?: 0,
                                    match = buildMatch(matchType, matchValue, matchIgnoreCase),
                                    category = category,
                                    metadataTemplate = buildMetadataTemplate(category),
                                    downloadPolicy = DownloadPolicyDto(),
                                    storagePolicy = StoragePolicyDto(originalTemplate = originalTemplate),
                                    postProcessPolicy = PostProcessPolicyDto(),
                                )
                                if (ruleId != null) client.updateRule(ruleId, request)
                                else client.createRule(request)
                                onSaved()
                                navigator.pop()
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to save rule"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isSaving) "Saving..." else "Save")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun buildMatch(type: String, value: String, ignoreCase: Boolean): RuleMatchDto = when (type) {
    "channel-id" -> RuleMatchDto.ChannelId(value)
    "channel-name" -> RuleMatchDto.ChannelName(value, ignoreCase)
    "title-regex" -> RuleMatchDto.TitleRegex(value)
    "url-regex" -> RuleMatchDto.UrlRegex(value)
    else -> RuleMatchDto.ChannelName(value, ignoreCase)
}

private fun buildMetadataTemplate(category: CategoryDto): MetadataTemplateDto = when (category) {
    CategoryDto.MUSIC_VIDEO -> MetadataTemplateDto.MusicVideo()
    CategoryDto.SERIES_EPISODE -> MetadataTemplateDto.SeriesEpisode()
    CategoryDto.OTHER -> MetadataTemplateDto.Other()
}

private fun populateMatch(
    rule: RuleDto,
    setValues: (type: String, value: String, ignoreCase: Boolean) -> Unit,
) {
    when (val match = rule.match) {
        is RuleMatchDto.ChannelId -> setValues("channel-id", match.value, true)
        is RuleMatchDto.ChannelName -> setValues("channel-name", match.value, match.ignoreCase)
        is RuleMatchDto.TitleRegex -> setValues("title-regex", match.pattern, true)
        is RuleMatchDto.UrlRegex -> setValues("url-regex", match.pattern, true)
        is RuleMatchDto.AllOf -> setValues("channel-name", "", true)
        is RuleMatchDto.AnyOf -> setValues("channel-name", "", true)
    }
}


