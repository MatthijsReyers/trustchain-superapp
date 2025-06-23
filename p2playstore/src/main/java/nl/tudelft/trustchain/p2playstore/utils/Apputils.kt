package nl.tudelft.trustchain.p2playstore.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import nl.tudelft.trustchain.p2playstore.R

object AppUtils {
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
