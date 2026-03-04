package io.github.alelk.tgvd.features.rules.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.common.util.categoryLabel
import io.github.alelk.tgvd.features.rules.model.CompositeOperator
import io.github.alelk.tgvd.features.rules.model.MatchConditionState

private val MATCH_TYPES = listOf(
    "channel-id" to "Channel ID",
    "channel-name" to "Channel Name",
    "title-regex" to "Title Pattern",
    "url-regex" to "URL Pattern",
    "category-equals" to "Category",
)

/**
 * Recursive match condition editor supporting Simple conditions and composite AllOf/AnyOf groups.
 *
 * @param state current match condition state
 * @param onChanged callback invoked when state changes (parent must replace in its list)
 * @param onRemove if non-null, shows a remove button; called when user clicks it
 * @param depth current nesting depth; limits recursion to avoid too-deep UIs
 */
@Composable
fun MatchConditionEditor(
    state: MatchConditionState,
    onChanged: (MatchConditionState) -> Unit,
    onRemove: (() -> Unit)? = null,
    depth: Int = 0,
) {
    val maxDepth = 3

    // Top-level: choose between Simple and Composite
    var isComposite by remember(state) { mutableStateOf(state is MatchConditionState.Composite) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            1.dp,
            if (depth == 0) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header row: mode selector + remove button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isComposite,
                        onClick = {
                            if (isComposite) {
                                isComposite = false
                                onChanged(MatchConditionState.Simple())
                            }
                        },
                        label = { Text("Simple") },
                    )
                    if (depth < maxDepth) {
                        FilterChip(
                            selected = isComposite,
                            onClick = {
                                if (!isComposite) {
                                    isComposite = true
                                    val composite = MatchConditionState.Composite()
                                    // seed with current simple as first child
                                    if (state is MatchConditionState.Simple && state.value.isNotBlank()) {
                                        composite.children.add(state.copy())
                                    }
                                    composite.children.add(MatchConditionState.Simple())
                                    onChanged(composite)
                                }
                            },
                            label = { Text("Group") },
                        )
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            TgvdIcons.Delete,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            when (state) {
                is MatchConditionState.Simple -> {
                    SimpleMatchEditor(state, onChanged)
                }
                is MatchConditionState.Composite -> {
                    CompositeMatchEditor(state, onChanged, depth)
                }
            }
        }
    }
}

@Composable
private fun SimpleMatchEditor(
    state: MatchConditionState.Simple,
    onChanged: (MatchConditionState) -> Unit,
) {
    var type by remember(state) { mutableStateOf(state.type) }
    var value by remember(state) { mutableStateOf(state.value) }
    var ignoreCase by remember(state) { mutableStateOf(state.ignoreCase) }

    // Match type chips
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MATCH_TYPES.forEach { (key, label) ->
            FilterChip(
                selected = type == key,
                onClick = {
                    type = key
                    onChanged(MatchConditionState.Simple(type = key, value = value, ignoreCase = ignoreCase))
                },
                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            )
        }
    }

    // Value input — category dropdown for category-equals, text field for others
    if (type == "category-equals") {
        CategoryDropdown(
            selectedValue = value,
            onSelected = { categoryName ->
                value = categoryName
                onChanged(MatchConditionState.Simple(type = type, value = categoryName, ignoreCase = ignoreCase))
            },
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                onChanged(MatchConditionState.Simple(type = type, value = it, ignoreCase = ignoreCase))
            },
            label = { Text(MATCH_TYPES.find { it.first == type }?.second ?: "Value") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = value.isBlank(),
        )
    }

    // Ignore case checkbox for channel-name
    if (type == "channel-name") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ignoreCase,
                onCheckedChange = {
                    ignoreCase = it
                    onChanged(MatchConditionState.Simple(type = type, value = value, ignoreCase = it))
                },
            )
            Text("Ignore case", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CompositeMatchEditor(
    state: MatchConditionState.Composite,
    onChanged: (MatchConditionState) -> Unit,
    depth: Int,
) {
    var operator by remember(state) { mutableStateOf(state.operator) }

    // Operator selector
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = operator == CompositeOperator.ALL_OF,
            onClick = {
                operator = CompositeOperator.ALL_OF
                state.operator = CompositeOperator.ALL_OF
                onChanged(state)
            },
            label = { Text("All Of (AND)") },
        )
        FilterChip(
            selected = operator == CompositeOperator.ANY_OF,
            onClick = {
                operator = CompositeOperator.ANY_OF
                state.operator = CompositeOperator.ANY_OF
                onChanged(state)
            },
            label = { Text("Any Of (OR)") },
        )
    }

    Text(
        if (operator == CompositeOperator.ALL_OF) "All conditions must match"
        else "At least one condition must match",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Children conditions
    Column(
        modifier = Modifier.padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.children.forEachIndexed { index, child ->
            MatchConditionEditor(
                state = child,
                onChanged = { newChild ->
                    state.children[index] = newChild
                    onChanged(state)
                },
                onRemove = if (state.children.size > 1) {
                    {
                        state.children.removeAt(index)
                        onChanged(state)
                    }
                } else null,
                depth = depth + 1,
            )
        }
    }

    // Add condition button
    OutlinedButton(
        onClick = {
            state.children.add(MatchConditionState.Simple())
            onChanged(state)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(TgvdIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add Condition")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = CategoryDto.entries
    val selectedCategory = categories.firstOrNull { it.name == selectedValue }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedCategory?.let { categoryLabel(it) } ?: "Select category",
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
            isError = selectedValue.isBlank(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(categoryLabel(cat)) },
                    onClick = {
                        onSelected(cat.name)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

