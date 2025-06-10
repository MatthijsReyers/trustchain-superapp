package nl.tudelft.trustchain.p2playstore.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.databinding.ItemAppBinding
import nl.tudelft.trustchain.p2playstore.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.utils.iconFromIconId
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

                binding.appEntranceFee.text = "${daoBlock.hashNumber}"

                binding.appVotingThreshold.text = "Threshold: ${data.SW_VOTING_THRESHOLD}%"

                binding.appIcon.setImageResource(iconFromIconId(daoBlock.transaction["iconIndex"]))

                itemView.setOnClickListener {
                    Log.d("DaoAdapter", "ItemView clicked: Triggering listener.onItemClick")
                    listener?.onItemClick(daoBlock)
                }

                itemView.isClickable = true
                itemView.isFocusable = true

            } ?: run {
                // Handle error case
                binding.appName.text = "Error loading DAO"
                binding.appDeveloper.text = ""
                binding.appEntranceFee.text = ""
                binding.appVotingThreshold.text = ""
                binding.appIcon.setImageResource(iconFromIconId(null))
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                itemView.isFocusable = false
            }
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
