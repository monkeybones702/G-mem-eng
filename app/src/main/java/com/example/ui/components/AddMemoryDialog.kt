package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.entity.MemoryEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMemoryDialog(
    initialMemory: MemoryEntity? = null,
    onDismiss: () -> Unit,
    onSave: (tag: String, content: String, importance: Int, isEnabled: Boolean) -> Unit
) {
    var content by remember { mutableStateOf(initialMemory?.content.orEmpty()) }
    var selectedTag by remember { mutableStateOf(initialMemory?.tag ?: "Preference") }
    var customTag by remember { mutableStateOf("") }
    var isCustomTagSelected by remember { mutableStateOf(false) }
    var importance by remember { mutableIntStateOf(initialMemory?.importance ?: 3) }
    var isEnabled by remember { mutableStateOf(initialMemory?.isEnabled ?: true) }

    val presetTags = listOf("Preference", "Fact", "Constraint", "Project", "Work")

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_memory_dialog"),
        title = {
            Text(
                text = if (initialMemory == null) "New Context Memory" else "Edit Memory",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Category Tag",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetTags.forEach { tag ->
                        FilterChip(
                            selected = (!isCustomTagSelected && selectedTag == tag),
                            onClick = {
                                isCustomTagSelected = false
                                selectedTag = tag
                            },
                            label = { Text(tag) }
                        )
                    }
                    FilterChip(
                        selected = isCustomTagSelected,
                        onClick = { isCustomTagSelected = true },
                        label = { Text("Custom") }
                    )
                }

                if (isCustomTagSelected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customTag,
                        onValueChange = { customTag = it },
                        label = { Text("Custom Tag Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_tag_input"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Memory Content / Fact") },
                    placeholder = { Text("e.g. Always generate responses with least possible tokens") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_content_input"),
                    minLines = 3,
                    maxLines = 6
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Importance Stars
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Priority:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    for (i in 1..5) {
                        IconButton(
                            onClick = { importance = i },
                            modifier = Modifier.testTag("importance_star_$i")
                        ) {
                            Icon(
                                imageVector = if (i <= importance) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "Importance $i",
                                tint = if (i <= importance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Enable Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.testTag("memory_enabled_checkbox_dialog")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Enable for dynamic injection",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Uncheck to preserve locally without injecting to Gemini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTag = if (isCustomTagSelected) customTag.ifBlank { "Custom" } else selectedTag
                    if (content.isNotBlank()) {
                        onSave(finalTag, content, importance, isEnabled)
                    }
                },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("save_memory_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_memory_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
