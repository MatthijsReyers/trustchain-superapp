package nl.tudelft.trustchain.p2playstore.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.databinding.AppPreviewBinding
import nl.tudelft.trustchain.p2playstore.models.P2playApp

class AppPreviewsAdapter(private val daoList: List<P2playApp>)
    : RecyclerView.Adapter<AppPreviewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppPreviewHolder {
        val binding = AppPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppPreviewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppPreviewHolder, position: Int) {
        val daoBlock = daoList[position]
        holder.bind(daoBlock)
    }

    override fun getItemCount(): Int {
        return daoList.size
    }
}
