package com.example.model

import android.net.Uri

data class ModelStatus(
    val modelType: AIModelType,
    val isLinked: Boolean = false,
    val fileUri: Uri? = null,
    val fileName: String? = null,
    val fileSizeBytes: Long = 0L,
    val isSessionReady: Boolean = false,
    val errorMessage: String? = null
) {
    val formattedFileSize: String
        get() {
            if (fileSizeBytes <= 0) return "0 MB"
            val mb = fileSizeBytes / (1024.0 * 1024.0)
            return String.format("%.1f MB", mb)
        }
}
