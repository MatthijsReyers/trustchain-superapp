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
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestTransactionData
import org.bitcoinj.core.Coin
import java.io.File
import java.util.Locale

object AppUtils {
    /**
     * Recursively searches for files with a specific extension inside a given directory.
     *
     * @param folder The root folder to search.
     * @param extension File extensions to match (e.g. ".apk").
     * @param recursive Whether to search subdirectories recursively. Defaults to true.
     * @return A list of [File] objects that match the given extension.
     *
     * @throws IllegalArgumentException if the folder path is not a valid directory.
     */
    fun findFilesByExtension(
        folder: File,
        extensions: Set<String>,
        recursive: Boolean = true
    ): List<File> {
        require(folder.exists() && folder.isDirectory) {
            "Directory not found or invalid: ${folder.absolutePath}"
        }

        val files: MutableList<File> = emptyList<File>().toMutableList()

        folder.listFiles()?.forEach { file ->
            if (file.isDirectory && recursive) {
                files.addAll(findFilesByExtension(file, extensions, recursive))
            } else if (extensions.any { file.name.endsWith(it, ignoreCase = true) }) {
                files += file
            }
        }

        return files
    }

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

    fun getProposalId(block: TrustChainBlock): String {
        return when (block.type) {
            PROPOSE_UPDATE_BLOCK -> ProposeUpdateTransactionData(block.transaction)
                .getData().SW_UNIQUE_PROPOSAL_ID
            JOIN_REQUEST_BLOCK -> JoinRequestTransactionData(block.transaction)
                .getData().SW_UNIQUE_PROPOSAL_ID
            else -> throw Exception("Not a P2PlayStore block")
        }
    }

    fun formatDynamicBalance(balance: Coin): String {
        return when {
            balance >= Coin.COIN -> {
                balance.toFriendlyString()
            }
            balance >= Coin.CENT -> {
                val btcValue = balance.value.toDouble() / Coin.COIN.value.toDouble()
                String.format(Locale.getDefault(), "%.2f BTC", btcValue)
            }
            balance >= Coin.MILLICOIN -> {
                val mbtc = balance.value / Coin.MILLICOIN.value
                "$mbtc mBTC"
            }
            else -> {
                "${balance.value} sats"
            }
        }
    }
}
