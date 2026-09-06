package com.asur.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun ProgressPhotosScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<ProgressPhotoRow>>(emptyList()) }
    var photoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var weightHistory by remember { mutableStateOf<List<Pair<kotlinx.datetime.LocalDate, Double>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var uploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var previewPhoto by remember { mutableStateOf<ProgressPhotoRow?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    suspend fun refresh() {
        photos = fetchProgressPhotos()
        photoUrls = photos.associate { it.id to getPhotoUrl(it.storage_path) }
        weightHistory = fetchWeightHistory()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val pickerLauncher = rememberPhotoPickerLauncher { bytes, ext ->
        scope.launch {
            uploading = true
            errorMessage = null
            try {
                uploadProgressPhoto(bytes, ext)
                refresh()
            } catch (e: Exception) {
                errorMessage = "Couldn't upload that photo. ${e.message}"
            } finally {
                uploading = false
            }
        }
    }

    val preview = previewPhoto
    if (preview != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(22.dp).clickable { previewPhoto = null }
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatDayMonth(preview.date),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    weightNearDate(weightHistory, preview.date)?.let {
                        Text(
                            "${it.oneDecimal()} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextTertiary
                        )
                    }
                }
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete photo",
                    tint = AppColors.Destructive,
                    modifier = Modifier.size(20.dp).clickable { confirmDelete = true }
                )
            }
            AsyncImage(
                model = photoUrls[preview.id],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Spacer(Modifier.height(120.dp))
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                title = { Text("Delete this photo?", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Text(
                        "This can't be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        scope.launch {
                            deleteProgressPhoto(preview.id, preview.storage_path)
                            previewPhoto = null
                            refresh()
                        }
                    }) { Text("Delete", color = AppColors.Destructive) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(22.dp).clickable(onClick = onBack)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                "Progress photos",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (photos.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = !uploading) { pickerLauncher.launch() },
                    contentAlignment = Alignment.Center
                ) {
                    if (uploading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add photo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        errorMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (photos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = AppColors.TextTertiary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Start your photo timeline",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "Photos are stored privately. Nobody in your group can see them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !uploading) { pickerLauncher.launch() }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (uploading) "Uploading" else "Add first photo",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            val grouped = photos
                .sortedByDescending { it.date }
                .groupBy { it.date.take(7) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = AppColors.TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Only visible to you",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                grouped.forEach { (monthKey, monthPhotos) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            formatMonthYear("$monthKey-01").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(monthPhotos, key = { it.id }) { photo ->
                        Column {
                            val url = photoUrls[photo.id]
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { previewPhoto = photo }
                            ) {
                                if (url != null) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            val nearbyWeight = weightNearDate(weightHistory, photo.date)
                            Text(
                                if (nearbyWeight != null) "${formatDayMonth(photo.date)} · ${nearbyWeight.oneDecimal()} kg"
                                else formatDayMonth(photo.date),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Most recent weight logged on or before the given date — matches how someone
 * would naturally think of "what did I weigh around this photo," since weigh-ins
 * and photos are rarely on the exact same day.
 */
private fun weightNearDate(history: List<Pair<kotlinx.datetime.LocalDate, Double>>, date: String): Double? {
    val target = runCatching { kotlinx.datetime.LocalDate.parse(date) }.getOrNull() ?: return null
    return history.lastOrNull { it.first <= target }?.second
}