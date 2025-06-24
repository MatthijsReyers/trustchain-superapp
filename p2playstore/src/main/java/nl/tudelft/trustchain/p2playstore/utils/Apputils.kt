package nl.tudelft.trustchain.p2playstore.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_NO_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_YES_BLOCK
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesTransactionData

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

    fun getProposalId(block: TrustChainBlock): String {
        return when (block.type) {
            PROPOSE_UPDATE_BLOCK ->
                ProposeUpdateTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID
            JOIN_REQUEST_BLOCK ->
                JoinRequestTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID
            VOTE_NO_BLOCK ->
                VoteNoTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID
            VOTE_YES_BLOCK ->
                VoteYesTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID
            else -> throw Exception("That's not a proposal block matey")
        }
    }
}
