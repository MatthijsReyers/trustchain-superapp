package nl.tudelft.trustchain.p2playstore.ui

import android.view.LayoutInflater
import android.view.ViewGroup
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

                binding.appEntranceFee.text = "${daoBlock.hashNumber}"

                binding.appVotingThreshold.text = "Threshold: ${data.SW_VOTING_THRESHOLD}%"

                setDaoIcon("${daoBlock.transaction["iconIndex"]}")

                itemView.setOnClickListener {
                    android.util.Log.d("DaoAdapter", "DAO clicked: ${data.SW_UNIQUE_ID}")
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
                binding.appIcon.setImageResource(R.drawable.ic_bitcoin)
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                itemView.isFocusable = false
            }
        }

        private fun setDaoIcon(iconId: String) {
            val iconResource = when (iconId) {
                "0" -> R.drawable.ic_bitcoin
                "1" -> R.drawable.ic_account_balance_wallet_black_24dp // TODO: Add more icons as needed
                "2" -> R.drawable.ic_group_work_black_24dp
                else -> R.drawable.ic_device_hub_black_24dp
            }
            binding.appIcon.setImageResource(iconResource)
        }

        // Method for future custom icon implementation
//        private fun setCustomIcon(base64Icon: String?) {
//            if (!base64Icon.isNullOrEmpty()) {
//                try {
//                    val decodedBytes = Base64.decode(base64Icon, Base64.DEFAULT)
//                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
//                    binding.appIcon.setImageBitmap(bitmap)
//                } catch (e: Exception) {
//                    android.util.Log.e("DaoAdapter", "Error loading custom icon: ${e.message}")
//                    binding.appIcon.setImageResource(R.drawable.ic_bitcoin)
//                }
//            } else {
//                binding.appIcon.setImageResource(R.drawable.ic_bitcoin)
//            }
//        }
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
