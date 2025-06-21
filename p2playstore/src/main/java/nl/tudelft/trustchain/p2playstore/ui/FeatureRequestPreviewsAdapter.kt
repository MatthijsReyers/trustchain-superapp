package nl.tudelft.trustchain.p2playstore.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.databinding.FeatureRequestPreviewBinding
import nl.tudelft.trustchain.p2playstore.models.FeatureRequest

class FeatureRequestPreviewsAdapter
    : RecyclerView.Adapter<FeatureRequestPreviewHolder>() {

    private var requests = listOf<FeatureRequest>()

    @SuppressLint("NotifyDataSetChanged")
    fun updateRequests(requests: List<FeatureRequest>) {
        this.requests = requests
        this.notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureRequestPreviewHolder {
        val binding = FeatureRequestPreviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeatureRequestPreviewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeatureRequestPreviewHolder, position: Int) {
        holder.bind(this.requests[position])
    }

    override fun getItemCount(): Int = this.requests.size
}
