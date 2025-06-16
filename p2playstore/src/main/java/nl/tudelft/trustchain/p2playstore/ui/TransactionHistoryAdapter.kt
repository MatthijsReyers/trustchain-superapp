package nl.tudelft.trustchain.p2playstore.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.util.taproot.CTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.databinding.ItemTransactionHistoryBinding
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData
import org.bitcoinj.core.Coin
import java.text.SimpleDateFormat
import java.util.*

class TransactionHistoryAdapter(
    private var blocks: List<TrustChainBlock>
) : RecyclerView.Adapter<TransactionHistoryAdapter.TransactionViewHolder>() {

    fun updateBlocks(newBlocks: List<TrustChainBlock>) {
        blocks = newBlocks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(blocks[position])
    }

    override fun getItemCount(): Int = blocks.size

    class TransactionViewHolder(private val binding: ItemTransactionHistoryBinding) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(block: TrustChainBlock) {
            // Common fields
            binding.tvDaoId.text = block.blockId.take(8) + "..."
            binding.tvTimestamp.text = dateFormat.format(block.timestamp)

            // Specific details based on block type
            when (block.type) {
                P2pStoreCommunity.JOIN_BLOCK -> {
                    try {
                        val data = JoinDaoTransactionData(block.transaction).getData()
                        // Use DAO ID if available, otherwise block ID
                        binding.tvDaoId.text = data.DAO_ID.take(8) + "..."
                        binding.tvTransactionType.text = "DAO Joined"

                        // Get the value of the shared wallet output from the serialized transaction
                        val serializedTx = data.SW_TRANSACTION_SERIALIZED
                        val cTx = CTransaction().deserialize(serializedTx.hexToBytes())
                        val sharedWalletOutputValue = cTx.vout.find { it.scriptPubKey.size == 35 }?.nValue ?: 0L

                        binding.tvAmountLabel.text = "New Total Balance:"
                        binding.tvAmount.text = Coin.valueOf(sharedWalletOutputValue).toFriendlyString()
                        binding.tvDetails.text = "New member joined DAO ${data.DAO_ID.take(8)}..."
                        binding.tvDetails.visibility = View.VISIBLE

                    } catch (e: Exception) {
                        android.util.Log.e("TransactionAdapter", "Error parsing JoinDaoTransactionData: ${e.message}")
                        setParsingErrorState(block)
                    }
                }
                P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> {
                    try {
                        val data = UpdateAcceptedTransactionData(block.transaction).getData()
                        // Use DAO ID if available, otherwise block ID
                        binding.tvDaoId.text = data.DAO_ID.take(8) + "..."
                        binding.tvTransactionType.text = "Update Accepted"

                        // Check if it was a feature solution with a reward transfer
                        if (data.SW_TRANSFER_FUNDS_AMOUNT > 0 && !data.SW_TRANSFER_FUNDS_TARGET_SERIALIZED.isNullOrEmpty()) {
                            binding.tvAmountLabel.text = "Reward Transferred:"
                            binding.tvAmount.text = Coin.valueOf(data.SW_TRANSFER_FUNDS_AMOUNT).toFriendlyString()
                            binding.tvAmount.setTextColor(binding.root.context.getColor(android.R.color.holo_green_dark)) // Indicate funds received

                            // Display details about the feature request and developer if available in the original proposal block
                            binding.tvDetails.text = "Update for ${data.APP_NAME} approved. ${Coin.valueOf(data.SW_TRANSFER_FUNDS_AMOUNT).toFriendlyString()} transferred to ${data.SW_TRANSFER_FUNDS_TARGET_SERIALIZED.take(8)}..."
                            binding.tvDetails.visibility = View.VISIBLE

                        } else {
                            // It was an update accepted without a direct reward transfer (e.g., just updating magnet link)
                            binding.tvAmountLabel.text = "DAO Wallet Balance:"
                            // Need to parse serialized transaction to get the *new* total DAO balance
                            val serializedTx = data.SW_TRANSACTION_SERIALIZED
                            val cTx = CTransaction().deserialize(serializedTx.hexToBytes())
                            val sharedWalletOutputValue = cTx.vout.find { it.scriptPubKey.size == 35 }?.nValue ?: 0L
                            binding.tvAmount.text = Coin.valueOf(sharedWalletOutputValue).toFriendlyString()
                            binding.tvAmount.setTextColor(binding.root.context.getColor(android.R.color.black)) // Default color

                            binding.tvDetails.text = "Update for ${data.APP_NAME} accepted."
                            binding.tvDetails.visibility = View.VISIBLE
                        }

                    } catch (e: Exception) {
                        android.util.Log.e("TransactionAdapter", "Error parsing UpdateAcceptedTransactionData: ${e.message}")
                        setParsingErrorState(block)
                    }
                }

                else -> {
                    // Handle other block types if needed, or mark as unknown
                    binding.tvTransactionType.text = "Unknown Transaction Type (${block.type})"
                    binding.tvAmountLabel.text = "N/A"
                    binding.tvAmount.text = ""
                    binding.tvDetails.visibility = View.GONE
                }
            }
        }

        private fun setParsingErrorState(block: TrustChainBlock) {
            binding.tvTransactionType.text = "Error Parsing Block (${block.type})"
            binding.tvAmountLabel.text = "N/A"
            binding.tvAmount.text = ""
            binding.tvDetails.text = "Could not parse transaction data for block ${block.blockId.take(8)}..."
            binding.tvDetails.visibility = View.VISIBLE
        }
    }
}
