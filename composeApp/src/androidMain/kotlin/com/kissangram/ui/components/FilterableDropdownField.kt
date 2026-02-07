package com.kissangram.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kissangram.ui.home.BackgroundColor
import com.kissangram.ui.home.PrimaryGreen
import com.kissangram.ui.home.TextPrimary
import com.kissangram.ui.home.TextSecondary

private val InputBackground = Color(0xFFF8F9F1)
private val CardBackground = Color.White
private val DisabledInputBackground = Color(0xFFE5E6DE).copy(alpha = 0.3f)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FilterableDropdownField(
    label: String,
    items: List<String>,
    selectedItem: String?,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    placeholder: String = "Select $label"
) {
    var searchText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Update search text when selected item changes
    LaunchedEffect(selectedItem) {
        searchText = selectedItem ?: ""
    }

    val filteredItems = remember(items, searchText) {
        if (searchText.isBlank()) items
        else items.filter { it.contains(searchText, ignoreCase = true) }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Label (matching Name and Bio style)
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )

        // Dropdown Field
        Column {
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    if (!expanded && enabled) {
                        expanded = true
                    }
                },
                placeholder = {
                    Text(
                        when {
                            isLoading -> "Loading..."
                            !enabled -> placeholder
                            selectedItem != null -> selectedItem
                            else -> placeholder
                        },
                        color = TextPrimary.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                readOnly = false,
                enabled = enabled && !isLoading,
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (searchText.isNotEmpty() && enabled && expanded) {
                            IconButton(
                                onClick = {
                                    searchText = ""
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryGreen
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (!expanded) {
                                        searchText = selectedItem ?: ""
                                    }
                                    expanded = !expanded && enabled
                                },
                                enabled = enabled,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    if (expanded) "Collapse" else "Expand",
                                    modifier = Modifier
                                        .rotate(if (expanded) 180f else 0f)
                                        .size(24.dp),
                                    tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InputBackground,
                    unfocusedContainerColor = InputBackground,
                    disabledContainerColor = DisabledInputBackground,
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = Color.Black.copy(alpha = 0.1f),
                    disabledBorderColor = Color.Black.copy(alpha = 0.05f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            )

            // Dropdown List
            AnimatedVisibility(
                visible = expanded && enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 4.dp,
                    color = CardBackground,
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f))
                ) {
                    if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (searchText.isNotEmpty()) "No results found" else "No $label available",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(filteredItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onItemSelected(item)
                                            searchText = item
                                            expanded = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 16.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selectedItem == item) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = PrimaryGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
