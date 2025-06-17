package nl.tudelft.trustchain.p2playstore.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import nl.tudelft.trustchain.p2playstore.R
import java.io.File

object AppUtils {
    /**
     * Recursively searches for files with a specific extension inside a given directory.
     *
     * @param folder The root folder to search.
     * @param extension File extension to match (e.g. ".apk").
     * @param recursive Whether to search subdirectories recursively. Defaults to true.
     * @return A list of [File] objects that match the given extension.
     *
     * @throws IllegalArgumentException if the folder path is not a valid directory.
     */
    fun findFilesByExtension(
        folder: File,
        extension: String,
        recursive: Boolean = true
    ): List<File> {
        require(folder.exists() && folder.isDirectory) {
            "Directory not found or invalid: ${folder.absolutePath}"
        }

        val files: MutableList<File> = emptyList<File>().toMutableList()

        folder.listFiles()?.forEach { file ->
            if (file.isDirectory && recursive) {
                files.addAll(findFilesByExtension(file, extension, recursive))
            } else if (file.name.endsWith(extension)) {
                files += file
            }
        }

        return files
    }

    /**
     * Displays a temporary on-screen message (toast) to the user.
     *
     * This method shows a {@link Toast} message using the provided application context and message string.
     * It's primarily intended for debugging, error notifications, or quick user feedback.
     *
     * @param applicationContext the context used to display the toast; usually the app or activity context
     * @param s the message to be shown to the user
     *
     * Example usage:
     * ```
     * printToast(requireContext(), "APK loaded successfully")
     * ```
     */
    @JvmStatic
    fun printToast(applicationContext: Context, s: String) {
        Toast.makeText(applicationContext, s, Toast.LENGTH_LONG).show()
    }
}
