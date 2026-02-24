package com.shawon.gdrive.service

import android.os.FileObserver
import java.io.File

/**
 * A FileObserver that monitors a directory and its subdirectories recursively.
 */
class RecursiveFileObserver(
    private val rootPath: String,
    private val onNewFile: (File) -> Unit
) {
    private val observers = mutableListOf<FileObserver>()
    private val mask = FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE

    /**
     * Start watching the directory and all subdirectories.
     */
    fun startWatching() {
        stopWatching()
        val root = File(rootPath)
        if (root.exists() && root.isDirectory) {
            addObserversRecursively(root)
        }
    }

    /**
     * Stop watching all directories.
     */
    fun stopWatching() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }

    private fun addObserversRecursively(dir: File) {
        // Create observer for this directory
        val observer = object : FileObserver(dir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                
                val file = File(dir, path)
                
                when (event) {
                    CREATE, MOVED_TO -> {
                        if (file.isDirectory) {
                            // New directory created, add observer for it
                            addObserversRecursively(file)
                        }
                    }
                    CLOSE_WRITE -> {
                        // File write completed
                        if (file.isFile && !isTemporaryFile(file)) {
                            onNewFile(file)
                        }
                    }
                }
            }
        }
        
        observer.startWatching()
        observers.add(observer)

        // Add observers for existing subdirectories
        dir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
            addObserversRecursively(subDir)
        }
    }

    /**
     * Check if a file is temporary (being written, etc.)
     */
    private fun isTemporaryFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.startsWith(".") ||
               name.endsWith(".tmp") ||
               name.endsWith(".temp") ||
               name.endsWith(".part") ||
               name.endsWith(".crdownload")
    }
}
