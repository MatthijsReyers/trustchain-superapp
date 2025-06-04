package nl.tudelft.trustchain.p2playstore.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.ExecutionActivity
import nl.tudelft.trustchain.p2playstore.databinding.FragmentAppDetailsBinding
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils.parseMagnet
import java.io.File

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

        binding.btnInstallUpdate.setOnClickListener {
            loadDynamicCode()
        }
    }

    private fun loadDynamicCode() {
        val applicationContext = requireContext()
        val magnet = parseMagnet(this.block.transaction["magnetLink"] as String)

        try {
            val intent = Intent(applicationContext, ExecutionActivity::class.java)
            intent.putExtra(
                "fileName",
                "${applicationContext}/p2p-apps/${magnet.infoHash}/${magnet.displayName}"
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Logs all files in the given subfolder within the app's cache directory.
     *
     * This function retrieves and logs the absolute paths of all files located in the specified
     * subfolder inside the application's cache directory. Useful for debugging file presence
     * and structure when working with dynamically loaded code or cached assets.
     *
     * @param subfolderInCache The relative subfolder path inside the cache directory to inspect.
     *                         Defaults to the root of the cache directory ("/").
     *
     * Example usage:
     * ```
     * printFiles("/dynamic_apks/")
     * ```
     */
    private fun printFiles(subfolderInCache: String = "/") {
        val applicationContext = requireContext()

        val files = File("${applicationContext.cacheDir}$subfolderInCache").listFiles()
        for (file in files!!) {
            Log.d("P2P", "File: $file")
        }
    }
}
