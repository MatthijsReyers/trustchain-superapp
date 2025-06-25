package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.ItemAppPreviewBinding
import nl.tudelft.trustchain.p2playstore.models.P2playApp

class AppPreviewHolder(private val binding: ItemAppPreviewBinding)
    : RecyclerView.ViewHolder(binding.root)
{
    fun bind(app: P2playApp) {
        binding.appName.text = app.name
        binding.appDeveloper.text = "${app.getDaoMemberCount()} members"
        binding.appEntranceFee.text = "Fee: ${app.getEntranceFee()}"
        binding.appVotingThreshold.text = "Threshold: ${app.getDaoVoteThreshold()}%"
        binding.appIcon.setImageResource(app.icon)
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
