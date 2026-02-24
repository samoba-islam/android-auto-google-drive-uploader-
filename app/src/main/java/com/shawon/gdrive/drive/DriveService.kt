package com.shawon.gdrive.drive

import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Service for uploading files to Google Drive.
 */
class DriveService(
    private val context: Context
) {
    private var driveService: Drive? = null

    /**
     * Initialize the Drive service with the signed-in account.
     */
    fun initialize(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("GDrive Uploader")
            .build()
    }

    /**
     * Check if the service is initialized.
     */
    fun isInitialized(): Boolean = driveService != null

    /**
     * Upload a single file to Google Drive.
     * Emits progress updates as a Flow.
     */
    fun uploadFile(selectedFile: SelectedFile): Flow<UploadProgress> = flow {
        val drive = driveService ?: throw IllegalStateException("Drive service not initialized")

        emit(UploadProgress(
            file = selectedFile,
            status = UploadStatus.UPLOADING
        ))

        try {
            val fileMetadata = File().apply {
                name = selectedFile.name
            }

            val inputStream = context.contentResolver.openInputStream(selectedFile.uri)
                ?: throw Exception("Cannot open file: ${selectedFile.name}")

            val mediaContent = InputStreamContent(selectedFile.mimeType, inputStream)
            mediaContent.length = selectedFile.size

            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, webViewLink")
                .execute()

            inputStream.close()

            emit(UploadProgress(
                file = selectedFile,
                bytesUploaded = selectedFile.size,
                status = UploadStatus.COMPLETED
            ))

        } catch (e: Exception) {
            emit(UploadProgress(
                file = selectedFile,
                status = UploadStatus.FAILED
            ))
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Upload a file and return the result.
     */
    suspend fun uploadFileWithResult(selectedFile: SelectedFile): UploadResult = withContext(Dispatchers.IO) {
        val drive = driveService ?: throw IllegalStateException("Drive service not initialized")

        try {
            val fileMetadata = File().apply {
                name = selectedFile.name
            }

            val inputStream = context.contentResolver.openInputStream(selectedFile.uri)
                ?: throw Exception("Cannot open file: ${selectedFile.name}")

            val mediaContent = InputStreamContent(selectedFile.mimeType, inputStream)
            mediaContent.length = selectedFile.size

            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, webViewLink")
                .execute()

            inputStream.close()

            UploadResult.Success(
                file = selectedFile,
                driveFileId = uploadedFile.id,
                webViewLink = uploadedFile.webViewLink
            )
        } catch (e: Exception) {
            UploadResult.Failure(
                file = selectedFile,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Get file info from a URI.
     */
    fun getFileInfo(uri: Uri): SelectedFile? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)

                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                    SelectedFile(
                        uri = uri,
                        name = name,
                        mimeType = mimeType,
                        size = size
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
