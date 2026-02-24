package com.shawon.gdrive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.shawon.gdrive.MainActivity
import com.shawon.gdrive.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that monitors a folder for new files and uploads them to Google Drive.
 */
class FolderWatchService : Service() {

    companion object {
        const val ACTION_START = "com.shawon.gdrive.START_WATCH"
        const val ACTION_STOP = "com.shawon.gdrive.STOP_WATCH"
        const val EXTRA_FOLDER_PATH = "folder_path"
        const val EXTRA_FOLDER_URI = "folder_uri"
        
        private const val NOTIFICATION_CHANNEL_ID = "folder_watch_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPLOAD_NOTIFICATION_ID = 1002
        
        /**
         * Start the folder watch service.
         */
        fun start(context: Context, folderPath: String, folderUri: Uri) {
            val intent = Intent(context, FolderWatchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FOLDER_PATH, folderPath)
                putExtra(EXTRA_FOLDER_URI, folderUri.toString())
            }
            context.startForegroundService(intent)
        }
        
        /**
         * Stop the folder watch service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, FolderWatchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fileObserver: RecursiveFileObserver? = null
    private var driveService: Drive? = null
    private val uploadedFiles = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeDriveService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH)
                if (folderPath != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Watching: $folderPath"))
                    startWatching(folderPath)
                }
            }
            ACTION_STOP -> {
                stopWatching()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopWatching()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Folder Watch",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for folder monitoring"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("GDrive Auto-Upload")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun initializeDriveService() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            val credential = GoogleAccountCredential.usingOAuth2(
                this,
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
    }

    private fun startWatching(folderPath: String) {
        stopWatching()
        
        fileObserver = RecursiveFileObserver(folderPath) { file ->
            // Avoid duplicate uploads
            val filePath = file.absolutePath
            if (!uploadedFiles.contains(filePath)) {
                uploadedFiles.add(filePath)
                uploadFile(file)
            }
        }
        fileObserver?.startWatching()
    }

    private fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
    }

    private fun uploadFile(file: File) {
        if (driveService == null) {
            initializeDriveService()
        }
        
        val drive = driveService ?: return

        serviceScope.launch {
            try {
                updateNotification("Uploading: ${file.name}")

                val fileMetadata = DriveFile().apply {
                    name = file.name
                }

                val inputStream = file.inputStream()
                val mimeType = getMimeType(file.name)
                val mediaContent = InputStreamContent(mimeType, inputStream)
                mediaContent.length = file.length()

                drive.files().create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()

                inputStream.close()

                showUploadCompleteNotification(file.name)
                updateNotification("Watching folder...")

            } catch (e: Exception) {
                showUploadFailedNotification(file.name, e.message ?: "Unknown error")
                // Remove from uploaded set so it can be retried
                uploadedFiles.remove(file.absolutePath)
            }
        }
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showUploadCompleteNotification(fileName: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Upload Complete")
            .setContentText("$fileName uploaded to Google Drive")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showUploadFailedNotification(fileName: String, error: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Upload Failed")
            .setContentText("$fileName: $error")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
