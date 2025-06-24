package nl.tudelft.trustchain.p2playstore.utils

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_NO_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_YES_BLOCK
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesTransactionData

object AppUtils {
    /**
     * Displays a temporary on-screen message (toast) to the user.
     *
     * This method shows a {@link Toast} message using the provided application context and message
     * string. It's primarily intended for debugging, error notifications, or quick user feedback.
     *
     * @param applicationContext the context used to display the toast; usually the app or activity
     * context
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

    /**
     * Extracts the DAO_ID from a P2PlayStore TrustChain block.
     */
    fun getDaoId(block: TrustChainBlock): String {
        val data = when (block.type) {
            JOIN_BLOCK -> JoinDaoTransactionData(block.transaction).getData()
            UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(block.transaction).getData()
            PROPOSE_UPDATE_BLOCK -> ProposeUpdateTransactionData(block.transaction).getData()
            JOIN_REQUEST_BLOCK -> JoinDaoTransactionData(block.transaction).getData()
            VOTE_YES_BLOCK -> VoteYesTransactionData(block.transaction).getData()
            VOTE_NO_BLOCK -> VoteNoTransactionData(block.transaction).getData()
            else -> throw Exception("Not a P2PlayStore block")
        }
        return data.DAO_ID;
    }
}
