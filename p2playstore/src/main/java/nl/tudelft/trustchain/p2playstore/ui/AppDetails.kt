package nl.tudelft.trustchain.p2playstore.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.ExecutionActivity
import nl.tudelft.trustchain.p2playstore.databinding.FragmentAppDetailsBinding

/**
 * This is the app details fragment that displays information about an app after a user has clicked
 * on it, here the user can actually open the app or join the app's DAO.
 */
class AppDetails : BaseFragment() {
    private var _binding: FragmentAppDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var block: TrustChainBlock

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        val args = this.requireArguments();
        val publicKey = args.getByteArray("publicKey")!!
        val sequenceNumber = args.getInt("sequenceNumber").toUInt()

        val community = this.getTrustChainCommunity()
        this.block = community.database.get(publicKey, sequenceNumber)!!

        println("Get block: ${block.transaction}")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        this.updateView()
    }

    private fun updateView() {
        val name = this.block.transaction["name"] as String
        binding.appName.text = name

        var category = this.block.transaction["category"] as? String
        if (category.isNullOrEmpty()) { category = "[none]" }
        binding.appCategory.text = category

        val description = this.block.transaction["description"] as? String
        binding.appDescription.text = description

        val magnetLink = this.block.transaction["magnetLink"] as String
        val fileName = magnetLink.split("&dn=").last().split("&tr=").first()
        binding.btnInstallUpdate.setOnClickListener {
            loadDynamicCode(fileName)
        }
    }

    private fun loadDynamicCode(fileName: String) {
        try {
            val intent = Intent(requireContext(), ExecutionActivity::class.java)
            intent.putExtra(
                "fileName",
                "${requireContext().cacheDir}/apps/${fileName.split("/").last()}"
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
