package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.AppPreviewBinding
import nl.tudelft.trustchain.p2playstore.models.P2playApp

class AppPreviewHolder(private val binding: AppPreviewBinding)
    : RecyclerView.ViewHolder(binding.root)
{
    fun bind(app: P2playApp) {
        binding.appName.text = app.name
        binding.appDeveloper.text = "${app.getDoaMemberCount()} members"
        binding.appEntranceFee.text = "Fee: ${app.getEntranceFee()}"
        binding.appVotingThreshold.text = "Threshold: ${app.getDoaVoteThreshold()}%"
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
