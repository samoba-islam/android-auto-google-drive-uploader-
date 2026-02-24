package com.shawon.gdrive.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.shawon.gdrive.auth.GoogleAuthManager
import com.shawon.gdrive.data.PreferencesManager
import com.shawon.gdrive.drive.DriveService
import com.shawon.gdrive.drive.SelectedFile
import com.shawon.gdrive.drive.UploadResult
import com.shawon.gdrive.drive.UploadState
import com.shawon.gdrive.service.FolderWatchService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the main screen.
 * Manages authentication state, file selection, upload operations, and folder watching.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val authManager = GoogleAuthManager(application)
    private val driveService = DriveService(application)
    private val preferencesManager = PreferencesManager(application)

    // Authentication state
    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    // Selected files
    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    // Upload state
    private val _uploadState = MutableStateFlow(UploadState())
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // Folder watch state
    val watchedFolderUri: StateFlow<Uri?> = preferencesManager.watchedFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val watchedFolderName: StateFlow<String?> = preferencesManager.watchedFolderName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val watchEnabled: StateFlow<Boolean> = preferencesManager.watchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    init {
        // Check if already signed in
        _signedInAccount.value = authManager.getSignedInAccount()
        _signedInAccount.value?.let { driveService.initialize(it) }
    }

    /**
     * Called when sign-in is successful.
     */
    fun onSignInSuccess(account: GoogleSignInAccount) {
        _signedInAccount.value = account
        driveService.initialize(account)
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        viewModelScope.launch {
            // Stop watching if running
            if (_isServiceRunning.value) {
                stopWatching()
            }
            authManager.signOut()
            _signedInAccount.value = null
            _selectedFiles.value = emptyList()
            _uploadState.value = UploadState()
        }
    }

    /**
     * Add files from URIs.
     */
    fun addFiles(uris: List<Uri>) {
        val newFiles = uris.mapNotNull { uri ->
            driveService.getFileInfo(uri)
        }
        _selectedFiles.update { currentFiles ->
            (currentFiles + newFiles).distinctBy { it.uri }
        }
    }

    /**
     * Remove a file from the selection.
     */
    fun removeFile(file: SelectedFile) {
        _selectedFiles.update { files ->
            files.filter { it.uri != file.uri }
        }
    }

    /**
     * Clear all selected files.
     */
    fun clearFiles() {
        _selectedFiles.value = emptyList()
    }

    /**
     * Upload all selected files to Google Drive.
     */
    fun uploadFiles() {
        val files = _selectedFiles.value
        if (files.isEmpty()) return

        viewModelScope.launch {
            _uploadState.value = UploadState(
                isUploading = true,
                totalFiles = files.size,
                results = emptyList()
            )

            val results = mutableListOf<UploadResult>()

            files.forEachIndexed { index, file ->
                _uploadState.update { state ->
                    state.copy(
                        currentFileIndex = index + 1,
                        currentProgress = (index.toFloat() / files.size)
                    )
                }

                val result = driveService.uploadFileWithResult(file)
                results.add(result)

                _uploadState.update { state ->
                    state.copy(
                        currentProgress = ((index + 1).toFloat() / files.size),
                        results = results.toList()
                    )
                }
            }

            _uploadState.update { state ->
                state.copy(
                    isUploading = false,
                    currentProgress = 1f
                )
            }

            // Clear files after successful upload
            val allSuccess = results.all { it is UploadResult.Success }
            if (allSuccess) {
                _selectedFiles.value = emptyList()
            }
        }
    }

    /**
     * Reset upload state (after showing results).
     */
    fun resetUploadState() {
        _uploadState.value = UploadState()
    }

    // ==================== Folder Watch Methods ====================

    /**
     * Set the folder to watch.
     */
    fun setWatchedFolder(uri: Uri, name: String) {
        viewModelScope.launch {
            // Take persistable URI permission
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission might already be taken
            }
            preferencesManager.setWatchedFolder(uri, name)
        }
    }

    /**
     * Clear the watched folder.
     */
    fun clearWatchedFolder() {
        viewModelScope.launch {
            if (_isServiceRunning.value) {
                stopWatching()
            }
            preferencesManager.clearWatchedFolder()
        }
    }

    /**
     * Start watching the selected folder.
     */
    fun startWatching() {
        val uri = watchedFolderUri.value ?: return
        val folderPath = getFolderPath(uri) ?: return
        
        viewModelScope.launch {
            preferencesManager.setWatchEnabled(true)
            FolderWatchService.start(getApplication(), folderPath, uri)
            _isServiceRunning.value = true
        }
    }

    /**
     * Stop watching the folder.
     */
    fun stopWatching() {
        viewModelScope.launch {
            preferencesManager.setWatchEnabled(false)
            FolderWatchService.stop(getApplication())
            _isServiceRunning.value = false
        }
    }

    /**
     * Get the file system path from a document URI.
     */
    private fun getFolderPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            
            if ("primary".equals(type, ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory()}/${split.getOrElse(1) { "" }}"
            } else {
                // External SD card or other storage
                "/storage/$type/${split.getOrElse(1) { "" }}"
            }
        } catch (e: Exception) {
            null
        }
    }
}
