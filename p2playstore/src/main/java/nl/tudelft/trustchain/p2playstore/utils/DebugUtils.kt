package nl.tudelft.trustchain.p2playstore.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.File

object DebugUtils {
    /**
     * Logs all files in the given subfolder within the app's cache directory.
     *
     * This function checks whether the specified subfolder exists within the application's
     * cache directory. If it does, it retrieves and logs the absolute paths of all files
     * inside it. This is useful for debugging file presence and structure when working
     * with dynamically loaded code or cached assets.
     *
     * @param subfolderInCache The relative subfolder path inside the cache directory to inspect.
     *                         Defaults to the root of the cache directory ("/").
     *
     * Example usage:
     * ```
     * printFiles("/p2p-apps/")
     * ```
     */
    fun printFiles(applicationContext: Context, subfolderInCache: String = "") {
        val targetDir = File(applicationContext.cacheDir, subfolderInCache)

        if (!targetDir.exists() || !targetDir.isDirectory) {
            Log.w("P2P", "Directory not found or invalid: $targetDir")
            return
        }

        val files = targetDir.listFiles()
        if (files.isNullOrEmpty()) {
            Log.d("P2P", "No files found in: $targetDir")
        } else {
            for (file in files) {
                Log.d("P2P", "File: $file")
            }
        }
    }

    /**
     * Display a short message on the screen (mainly for debugging purposes).
     */
    fun printToast(applicationContext: Context, s: String) {
        Toast.makeText(applicationContext, s, Toast.LENGTH_LONG).show()
    }
}
