package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.ItemAppBinding
import nl.tudelft.trustchain.p2playstore.models.P2playApp

class DaoAdapter(private val daoList: List<P2playApp>)
    : RecyclerView.Adapter<DaoAdapter.DaoViewHolder>()
{
    interface OnItemClickListener {
        fun onItemClick(app: P2playApp)
    }

    class DaoViewHolder(private val binding: ItemAppBinding)
        : RecyclerView.ViewHolder(binding.root)
    {
        fun bind(app: P2playApp) {
            binding.appName.text = app.getName()
            binding.appDeveloper.text = "${app.getDoaMemberCount()} members"
            binding.appEntranceFee.text = "Fee: ${app.getEntranceFee()}"
            binding.appVotingThreshold.text = "Threshold: ${app.daoData.SW_VOTING_THRESHOLD}%"
            binding.appIcon.setImageResource(app.getIcon())
            itemView.setOnClickListener {
                val bundle = Bundle().apply {
                    putByteArray("publicKey", app.block.publicKey)
                    putInt("sequenceNumber", app.block.sequenceNumber.toInt())
                }
                itemView.findNavController().navigate(R.id.action_homeFragment_to_appDetails, bundle)
            }
            itemView.isClickable = true
            itemView.isFocusable = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DaoViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DaoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DaoViewHolder, position: Int) {
        val daoBlock = daoList[position]
        holder.bind(daoBlock)
    }

    override fun getItemCount(): Int {
        return daoList.size
    }
}
