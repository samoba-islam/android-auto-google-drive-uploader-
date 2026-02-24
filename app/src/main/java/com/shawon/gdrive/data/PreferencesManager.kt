package com.shawon.gdrive.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gdrive_prefs")

/**
 * Manages app preferences using DataStore.
 */
class PreferencesManager(private val context: Context) {

    companion object {
        private val WATCHED_FOLDER_URI = stringPreferencesKey("watched_folder_uri")
        private val WATCHED_FOLDER_NAME = stringPreferencesKey("watched_folder_name")
        private val WATCH_ENABLED = booleanPreferencesKey("watch_enabled")
    }

    /**
     * Flow of the watched folder URI.
     */
    val watchedFolderUri: Flow<Uri?> = context.dataStore.data.map { prefs ->
        prefs[WATCHED_FOLDER_URI]?.let { Uri.parse(it) }
    }

    /**
     * Flow of the watched folder display name.
     */
    val watchedFolderName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[WATCHED_FOLDER_NAME]
    }

    /**
     * Flow of whether watching is enabled.
     */
    val watchEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[WATCH_ENABLED] ?: false
    }

    /**
     * Set the watched folder.
     */
    suspend fun setWatchedFolder(uri: Uri, name: String) {
        context.dataStore.edit { prefs ->
            prefs[WATCHED_FOLDER_URI] = uri.toString()
            prefs[WATCHED_FOLDER_NAME] = name
        }
    }

    /**
     * Set whether watching is enabled.
     */
    suspend fun setWatchEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[WATCH_ENABLED] = enabled
        }
    }

    /**
     * Clear the watched folder.
     */
    suspend fun clearWatchedFolder() {
        context.dataStore.edit { prefs ->
            prefs.remove(WATCHED_FOLDER_URI)
            prefs.remove(WATCHED_FOLDER_NAME)
            prefs[WATCH_ENABLED] = false
        }
    }
}
