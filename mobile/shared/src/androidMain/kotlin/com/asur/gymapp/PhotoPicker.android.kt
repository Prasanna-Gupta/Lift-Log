package com.asur.gymapp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class PhotoPickerLauncher(private val launcher: () -> Unit) {
    actual fun launch() = launcher()
}

@Composable
actual fun rememberPhotoPickerLauncher(onPhotoPicked: (ByteArray, String) -> Unit): PhotoPickerLauncher {
    val context = androidx.compose.ui.platform.LocalContext.current

    val activityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                // Default to jpg — good enough for typical gallery photos.
                onPhotoPicked(bytes, "jpg")
            }
        }
    }

    return remember {
        PhotoPickerLauncher { activityLauncher.launch("image/*") }
    }
}