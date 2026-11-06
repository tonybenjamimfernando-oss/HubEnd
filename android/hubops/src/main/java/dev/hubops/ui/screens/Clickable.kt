package dev.hubops.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

fun Modifier.clickableCompat(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
