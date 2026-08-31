package com.asur.gymapp

import androidx.compose.runtime.Composable

expect class PhotoPickerLauncher {
    fun launch()
}

@Composable
expect fun rememberPhotoPickerLauncher(onPhotoPicked: (ByteArray, String) -> Unit): PhotoPickerLauncher