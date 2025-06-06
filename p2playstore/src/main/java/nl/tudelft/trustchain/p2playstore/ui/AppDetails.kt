package nl.tudelft.trustchain.p2playstore.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.ExecutionActivity
import nl.tudelft.trustchain.p2playstore.databinding.FragmentAppDetailsBinding
import nl.tudelft.trustchain.p2playstore.utils.DebugUtils.printToast
import nl.tudelft.trustchain.p2playstore.utils.MagnetLink
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
            loadDynamicAPK()
        }
    }

    private fun loadDynamicAPK() {
        val applicationContext = requireContext()

        val rawMagnetLink = this.block.transaction["magnetLink"] as? String
        if (rawMagnetLink.isNullOrBlank()) {
            Log.e("P2P", "No magnet link found in transaction.")
            printToast(applicationContext, "No magnet link connected to this DAO.")
            return
        }

        val magnet: MagnetLink = try {
            parseMagnet(rawMagnetLink)
        } catch (e: IllegalArgumentException) {
            Log.e("P2P", "Malformed magnet link: ${e.message}")
            printToast(applicationContext, "Malformed magnet link connected to this DAO.")
            return
        }

        val apkPath = "${applicationContext.cacheDir}/p2p-apps/${magnet.infoHash}/${magnet.displayName}"
        val apkFile = File(apkPath)

        if (!apkFile.exists() || !apkFile.isFile) {
            Log.e("P2P", "File not found or invalid: $apkFile")
            printToast(applicationContext, "No APK found connected to this DAO.")
            return
        }

        val intent = Intent(applicationContext, ExecutionActivity::class.java).apply {
            putExtra("fileName", apkPath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            applicationContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("P2P", "No activity found to handle intent for APK: $apkPath", e)
            printToast(applicationContext, "Unable to open APK – app component not found.")
        } catch (e: SecurityException) {
            Log.e("P2P", "Security exception when launching APK: $apkPath", e)
            printToast(applicationContext, "Permission denied to launch APK.")
        } catch (e: Exception) {
            Log.e("P2P", "Unexpected error launching APK: $apkPath", e)
            printToast(applicationContext, "Something went wrong when opening the APK.")
        }
    }
}
