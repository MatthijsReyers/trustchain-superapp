package nl.tudelft.trustchain.p2playstore.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.databinding.PollPreviewBinding
import nl.tudelft.trustchain.p2playstore.models.Poll

class PollPreviewsAdapter()
    : RecyclerView.Adapter<PollPreviewHolder>() {

    private var polls = listOf<Poll>()

    @SuppressLint("NotifyDataSetChanged")
    fun updatePolls(newPolls: List<Poll>) {
        this.polls = newPolls
        this.notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollPreviewHolder {
        val binding = PollPreviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PollPreviewHolder(binding)
    }

    override fun onBindViewHolder(holder: PollPreviewHolder, position: Int) {
        holder.bind(this.polls[position])
    }

    override fun getItemCount(): Int = polls.size
}

