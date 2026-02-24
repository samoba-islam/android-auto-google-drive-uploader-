package com.shawon.gdrive.ui.screens

import android.app.Activity
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.shawon.gdrive.drive.SelectedFile
import com.shawon.gdrive.drive.UploadResult
import com.shawon.gdrive.ui.viewmodel.MainViewModel

// Premium color palette
private val SuccessColor = Color(0xFF10B981)
private val ErrorColor = Color(0xFFEF4444)
private val SurfaceColor = Color(0xFF1A1A2E)
private val CardColor = Color(0xFF16213E)
private val AccentBlue = Color(0xFF4F8CFF)
private val AccentPurple = Color(0xFF8B5CF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val signedInAccount by viewModel.signedInAccount.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    
    // Folder watch state
    val watchedFolderUri by viewModel.watchedFolderUri.collectAsState()
    val watchedFolderName by viewModel.watchedFolderName.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                viewModel.onSignInSuccess(account)
            } catch (e: ApiException) {
                // Handle sign-in failure
            }
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        viewModel.addFiles(uris)
    }
    
    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val docId = DocumentsContract.getTreeDocumentId(it)
            val folderName = docId.substringAfterLast("/").ifEmpty { docId.substringAfterLast(":") }
            viewModel.setWatchedFolder(it, folderName)
        }
    }

    Scaffold(
        containerColor = SurfaceColor,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            "GDrive Uploader",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceColor,
                    titleContentColor = Color.White
                ),
                actions = {
                    if (signedInAccount != null) {
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sign Out",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Auth Section
            AnimatedVisibility(
                visible = signedInAccount == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SignInCard(
                    onSignInClick = {
                        signInLauncher.launch(viewModel.authManager.getSignInIntent())
                    }
                )
            }

            // Signed In Content
            AnimatedVisibility(
                visible = signedInAccount != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // User Info Card
                    signedInAccount?.let { account ->
                        UserInfoCard(
                            email = account.email ?: "Unknown",
                            displayName = account.displayName ?: "User"
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // ========== AUTO-UPLOAD SECTION ==========
                    SectionHeader(
                        icon = Icons.Default.Sync,
                        title = "Auto-Upload",
                        color = AccentPurple
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    FolderWatchCard(
                        watchedFolderName = watchedFolderName,
                        isWatching = isServiceRunning,
                        onSelectFolder = { folderPickerLauncher.launch(null) },
                        onClearFolder = { viewModel.clearWatchedFolder() },
                        onToggleWatch = {
                            if (isServiceRunning) {
                                viewModel.stopWatching()
                            } else {
                                viewModel.startWatching()
                            }
                        },
                        hasFolder = watchedFolderUri != null
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // ========== MANUAL UPLOAD SECTION ==========
                    SectionHeader(
                        icon = Icons.Default.CloudUpload,
                        title = "Manual Upload",
                        color = AccentBlue
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Select Files Button
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentBlue
                        ),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !uploadState.isUploading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Files", fontWeight = FontWeight.SemiBold)
                    }

                    // Selected Files List
                    if (selectedFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardColor),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Selected Files (${selectedFiles.size})",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TextButton(
                                        onClick = { viewModel.clearFiles() },
                                        enabled = !uploadState.isUploading
                                    ) {
                                        Text("Clear All", color = AccentBlue)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                selectedFiles.take(5).forEach { file ->
                                    FileItem(
                                        file = file,
                                        onRemove = { viewModel.removeFile(file) },
                                        enabled = !uploadState.isUploading
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                
                                if (selectedFiles.size > 5) {
                                    Text(
                                        "+${selectedFiles.size - 5} more files",
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Upload Button
                        Button(
                            onClick = { viewModel.uploadFiles() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessColor
                            ),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !uploadState.isUploading
                        ) {
                            if (uploadState.isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Uploading ${uploadState.currentFileIndex}/${uploadState.totalFiles}...",
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload to Drive", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Upload Progress
                        if (uploadState.isUploading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { uploadState.currentProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AccentBlue,
                                trackColor = CardColor
                            )
                        }
                    }

                    // Upload Results
                    if (uploadState.results.isNotEmpty() && !uploadState.isUploading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        UploadResultsCard(
                            results = uploadState.results,
                            onDismiss = { viewModel.resetUploadState() }
                        )
                    }

                    // Empty state for manual upload
                    if (selectedFiles.isEmpty() && uploadState.results.isEmpty()) {
                        Spacer(modifier = Modifier.height(32.dp))
                        EmptyState()
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun FolderWatchCard(
    watchedFolderName: String?,
    isWatching: Boolean,
    onSelectFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onToggleWatch: () -> Unit,
    hasFolder: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Folder selection row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(40.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (hasFolder) "Watching Folder" else "Select a Folder",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        watchedFolderName ?: "Tap to choose folder",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (hasFolder) {
                    IconButton(onClick = onClearFolder) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Select folder / Watch toggle
            if (!hasFolder) {
                Button(
                    onClick = onSelectFolder,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Folder to Watch")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (isWatching) "Auto-Upload Active" else "Auto-Upload Paused",
                            color = if (isWatching) SuccessColor else Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (isWatching) "New files will be uploaded" else "Tap to start watching",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Switch(
                        checked = isWatching,
                        onCheckedChange = { onToggleWatch() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SuccessColor,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onSelectFolder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AccentPurple
                    )
                ) {
                    Text("Change Folder")
                }
            }
        }
    }
}

@Composable
private fun SignInCard(onSignInClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = AccentBlue
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Welcome to GDrive Uploader",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Sign in with your Google account to start uploading files to Google Drive",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onSignInClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Sign in with Google",
                    color = Color(0xFF4285F4),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UserInfoCard(email: String, displayName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    displayName.firstOrNull()?.uppercase() ?: "U",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    displayName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    email,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FileItem(
    file: SelectedFile,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SurfaceColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatFileSize(file.size),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        IconButton(
            onClick = onRemove,
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White.copy(alpha = if (enabled) 0.7f else 0.3f)
            )
        }
    }
}

@Composable
private fun UploadResultsCard(
    results: List<UploadResult>,
    onDismiss: () -> Unit
) {
    val successCount = results.count { it is UploadResult.Success }
    val failureCount = results.count { it is UploadResult.Failure }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (failureCount == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (failureCount == 0) SuccessColor else ErrorColor,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                if (failureCount == 0) "Upload Complete!" else "Upload Finished with Errors",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "$successCount file(s) uploaded successfully" +
                        if (failureCount > 0) ", $failureCount failed" else "",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = Color.White.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "No files selected",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            "Tap 'Select Files' to choose files to upload",
            color = Color.White.copy(alpha = 0.3f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

