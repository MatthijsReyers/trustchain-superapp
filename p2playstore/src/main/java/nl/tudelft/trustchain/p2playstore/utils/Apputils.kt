package nl.tudelft.trustchain.p2playstore.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import nl.tudelft.trustchain.p2playstore.R

object AppUtils {
    /**
     * Updates the width of three progress bars within a container based on percentages.
     * This is designed for the voting progress bars in AppDetails and VotingPollsAdapter.
     */
    fun updateProgressBars(
        containerView: View,
        yesProgressBar: View,
        noProgressBar: View,
        pendingProgressBar: View,
        yesPercent: Int,
        noPercent: Int,
        pendingPercent: Int
    ) {
        containerView.post {
            val containerWidth = containerView.width
            if (containerWidth > 0) {
                // Adjust padding and margins based on the specific layout XML
                // These dimensions are taken from fragment_app_details.xml and item_voting_poll.xml
                val horizontalPaddingAndMargins =
                    containerView.resources.getDimensionPixelSize(R.dimen.padding_normal) * 2 +
                        containerView.resources.getDimensionPixelSize(R.dimen.progress_bar_margin_horizontal) * 2

                val availableWidth = containerWidth - horizontalPaddingAndMargins

                if (availableWidth > 0) {
                    // Ensure minimum width to be visible (e.g., 1 pixel)
                    val yesWidth = maxOf(1, (availableWidth * yesPercent / 100))
                    val noWidth = maxOf(1, (availableWidth * noPercent / 100))
                    val pendingWidth = maxOf(1, (availableWidth * pendingPercent / 100))

                    yesProgressBar.layoutParams.width = yesWidth
                    noProgressBar.layoutParams.width = noWidth
                    pendingProgressBar.layoutParams.width = pendingWidth

                    yesProgressBar.requestLayout()
                    noProgressBar.requestLayout()
                    pendingProgressBar.requestLayout()
                } else {
                    android.util.Log.w(
                        "AppUtils",
                        "Insufficient available width ($availableWidth) for progress bars. Container width: $containerWidth"
                    )
                }
            }
        }
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
