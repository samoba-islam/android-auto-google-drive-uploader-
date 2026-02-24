package com.shawon.gdrive.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages Google Sign-In authentication for Drive API access.
 */
class GoogleAuthManager(private val context: Context) {

    private val signInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }

    val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, signInOptions)
    }

    /**
     * Returns the currently signed-in account, or null if not signed in.
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Creates an intent for the Google Sign-In flow.
     */
    fun getSignInIntent(): Intent {
        return signInClient.signInIntent
    }

    /**
     * Signs out the current user.
     */
    suspend fun signOut(): Unit = suspendCancellableCoroutine { continuation ->
        signInClient.signOut()
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    /**
     * Revokes access for the current user.
     */
    suspend fun revokeAccess(): Unit = suspendCancellableCoroutine { continuation ->
        signInClient.revokeAccess()
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}
