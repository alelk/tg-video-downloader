package io.github.alelk.tgvd.features.common.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dialog for creating a new workspace.
 * Validates slug format: lowercase letters, digits, hyphens, 3–50 chars.
 */
@Composable
fun CreateWorkspaceDialog(
    onDismiss: () -> Unit,
    onCreate: (slug: String, name: String) -> Unit,
    isCreating: Boolean = false,
    errorMessage: String? = null,
) {
    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var slugError by remember { mutableStateOf<String?>(null) }

    // Auto-generate slug from name
    fun autoSlug(input: String): String =
        input.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(50)

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("New Workspace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { newName ->
                        name = newName
                        // Auto-generate slug while user hasn't manually edited it
                        slug = autoSlug(newName)
                        slugError = null
                    },
                    label = { Text("Name") },
                    placeholder = { Text("My Team") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = slug,
                    onValueChange = { newSlug ->
                        slug = newSlug.lowercase().filter { it.isLetterOrDigit() || it == '-' }
                        slugError = null
                    },
                    label = { Text("Slug") },
                    placeholder = { Text("my-team") },
                    supportingText = {
                        Text(slugError ?: errorMessage ?: "URL identifier: /workspaces/$slug/...")
                    },
                    isError = slugError != null || errorMessage != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        slug.length < 3 -> slugError = "Minimum 3 characters"
                        !slug.matches(Regex("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$")) ->
                            slugError = "Only lowercase letters, digits and hyphens"
                        else -> onCreate(slug, name)
                    }
                },
                enabled = name.isNotBlank() && slug.isNotBlank() && !isCreating,
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isCreating) "Creating..." else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Cancel")
            }
        },
    )
}

