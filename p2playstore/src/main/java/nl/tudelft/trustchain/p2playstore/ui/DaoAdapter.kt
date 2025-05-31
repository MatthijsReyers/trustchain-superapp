package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.ItemAppBinding
import nl.tudelft.trustchain.p2playstore.sharedWallet.SWJoinBlockTransactionData
import org.bitcoinj.core.Coin

class DaoAdapter(
    private val daoList: List<TrustChainBlock>,
    private val listener: OnItemClickListener? = null
) : RecyclerView.Adapter<DaoAdapter.DaoViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(daoBlock: TrustChainBlock)
    }

    class DaoViewHolder(
        private val binding: ItemAppBinding,
        private val listener: OnItemClickListener?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(daoBlock: TrustChainBlock) {
            val daoData = try {
                SWJoinBlockTransactionData(daoBlock.transaction).getData()
            } catch (e: Exception) {
                android.util.Log.e("DaoAdapter", "Error parsing DAO data from block: ${e.message}")
                null
            }

            daoData?.let { data ->
                binding.appName.text = "${daoBlock.transaction["name"]}"

                binding.appDeveloper.text = "${data.SW_TRUSTCHAIN_PKS.size} members"

                binding.appEntranceFee.text = "Fee: ${Coin.valueOf(data.SW_ENTRANCE_FEE).toFriendlyString()}"

                binding.appVotingThreshold.text = "Threshold: ${data.SW_VOTING_THRESHOLD}%"

                setDaoIcon(data.SW_UNIQUE_ID)

                itemView.setOnClickListener {
                    android.util.Log.d("DaoAdapter", "DAO clicked: ${data.SW_UNIQUE_ID}")

                    // Navigate to DAO details with block ID
                    val bundle = Bundle().apply {
                        putString("blockId", daoBlock.blockId)
                    }
                    itemView.findNavController().navigate(R.id.action_homeFragment_to_daoDetailsFragment, bundle)
                }

                itemView.isClickable = true
                itemView.isFocusable = true

            } ?: run {
                // Handle error case
                binding.appName.text = "Error loading DAO"
                binding.appDeveloper.text = ""
                binding.appEntranceFee.text = ""
                binding.appVotingThreshold.text = ""
                binding.appIcon.setImageResource(R.drawable.ic_bitcoin)
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                itemView.isFocusable = false
            }
        }

        private fun setDaoIcon(uniqueId: String) {
            val iconResource = when (uniqueId.hashCode() % 4) {
                0 -> R.drawable.ic_bitcoin
                1 -> R.drawable.ic_bitcoin // TODO: Add more icons as needed
                2 -> R.drawable.ic_bitcoin
                else -> R.drawable.ic_bitcoin
            }
            binding.appIcon.setImageResource(iconResource)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DaoViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DaoViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: DaoViewHolder, position: Int) {
        val daoBlock = daoList[position]
        holder.bind(daoBlock)
    }

    override fun getItemCount(): Int {
        return daoList.size
    }
}
