package com.shawon.gdrive.drive

import android.net.Uri

/**
 * Represents a file selected for upload.
 */
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long
)

/**
 * Represents the upload progress of a single file.
 */
data class UploadProgress(
    val file: SelectedFile,
    val bytesUploaded: Long = 0,
    val totalBytes: Long = file.size,
    val status: UploadStatus = UploadStatus.PENDING
)

/**
 * Status of a file upload.
 */
enum class UploadStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED
}

/**
 * Result of an upload operation.
 */
sealed class UploadResult {
    data class Success(
        val file: SelectedFile,
        val driveFileId: String,
        val webViewLink: String?
    ) : UploadResult()

    data class Failure(
        val file: SelectedFile,
        val error: String
    ) : UploadResult()
}

/**
 * Overall upload state for the UI.
 */
data class UploadState(
    val isUploading: Boolean = false,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentProgress: Float = 0f,
    val results: List<UploadResult> = emptyList(),
    val errorMessage: String? = null
)
