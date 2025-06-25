package nl.tudelft.trustchain.p2playstore.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.databinding.FragmentAppDetailsBinding
import nl.tudelft.trustchain.p2playstore.ExecutionActivity
import nl.tudelft.trustchain.p2playstore.P2PlayStoreMainActivity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.FEATURE_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_NO_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_YES_BLOCK
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.TorrentManager
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.utils.AppUtils
import nl.tudelft.trustchain.p2playstore.utils.AppUtils.findFilesByExtension


class AppDetails : BaseFragment() {
    private lateinit var torrentManager: TorrentManager

    private var _binding: FragmentAppDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var app: P2playApp

    private var joinPoll: PollPreviewHolder? = null
    private var updatePoll: PollPreviewHolder? = null
    private var lastestFeatureRequest: FeatureRequestPreviewHolder? = null

    /**
     * Integer between 0-100, this indicates how far along the torrent download for this apps
     * APK file is.
     */
    private var downloadProgress: Int? = null

    /**
     * Has the torrent with the APK file for this app finished downloading yet?
     */
    private fun downloadFinished(): Boolean {
        return this.downloadProgress != null && this.downloadProgress as Int >= 100
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        // The previous fragment (home) tells us which block/app/version to show
        val args = this.requireArguments()
        val publicKey = args.getByteArray("publicKey")!!
        val sequenceNumber = args.getInt("sequenceNumber").toUInt()

        try {
            // Actually retrieve the block
            val community = this.getTrustChainCommunity()
            val daoBlock = community.database.get(publicKey, sequenceNumber)!!
            this.app = P2playApp(daoBlock)
        }
        catch (e: Throwable) {
            Log.e("P2PlayStore", "Error loading DAO details: ${e.message}")
            findNavController().navigateUp()
        }

        torrentManager = (this.activity as P2PlayStoreMainActivity).torrentManager
        this.downloadProgress = torrentManager.downloadProgress(this.app);
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        this.setupClickListeners()
        this.setupTorrentDownloadStatus()
        this.setupPreviews()

        this.updateAppMetaData()
        this.updateDownloadButton()
        this.updateUIBasedOnMembership()
        this.updatePolls()
        this.updateFeatureRequests()
        this.updateScreenshots()

        this.finalizeJoinRequest()
        this.finalizeUpdate()
    }

    /**
     * Called whenever new blocks with the DAO ID for this app are detected, practically this means
     * we want to update the whole UI since votes/version updates might have changed.
     */
    override suspend fun onChainUpdated(block: TrustChainBlock) {
        if (this._binding == null) return;

        // Is the new block relevant for this app?
        if (AppUtils.getDaoId(block) != app.daoId) return;

        when (block.type) {
            // Was a new version of the app released?
            JOIN_BLOCK, UPDATE_ACCEPTED_BLOCK -> {
                this.app = P2playApp(block)
                requireActivity().runOnUiThread {
                    updateAppMetaData()
                    updateDownloadButton()
                    updateUIBasedOnMembership()
                    updatePolls()
                }
            }
            // Did someone create a new poll/proposal?
            JOIN_REQUEST_BLOCK, PROPOSE_UPDATE_BLOCK -> {
                this.updatePolls()
                this.updateFeatureRequests()
            }
            // Did someone create a new feature request?
            FEATURE_REQUEST_BLOCK -> {
                this.updateFeatureRequests()
            }
            // Did someone vote in a poll?
            VOTE_YES_BLOCK, VOTE_NO_BLOCK -> {
                this.updatePolls()
                this.updateFeatureRequests()
                // Check if this user requested to join the DAO and has collected enough votes now.
                this.finalizeJoinRequest()
                this.finalizeUpdate()
            }
        }
    }

    /**
    * Called when the user presses the install button (which is only shown when the user is not
    * yet in the app's DAO), effectively this means they will spend bitcoins to join the shared
    * wallet, so we'll ask them for confirmation of that first.
    */
    private fun onInstallApp() {
        if (this.app.isDaoMember()) {
            printToast("You are already a member of this DAO.")
            Log.d("AppDetails", "User attempted to join DAO ${app.daoId} but is already a member.")
            updateDownloadButton()
            updateUIBasedOnMembership()
            return
        }

        if (this.app.isWaitingToJoin()) {
            printToast("You have a pending request to join this DAO.")
            Log.d("AppDetails", "User attempted to join DAO ${app.daoId} but has a pending request.")
            updateDownloadButton()
            updateUIBasedOnMembership()
            return
        }

        val entranceFee = this.app.getEntranceFee()
        val msg = "In order to download this app you must join its DAO and pay an enterance fee " +
            "of $entranceFee Satoshi to the shared wallet."

        AlertDialog.Builder(requireContext())
            .setTitle("Are you sure?")
            .setMessage(msg)
            .setPositiveButton("Join DAO") { dialog, _ ->
                dialog.dismiss()
                onJoinDoa()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    /**
     * Called when the user presses the "restart download" button, which is only visible when they
     * are a member of the app DAO, but the torrent download for the app has failed.
     */
    private fun onRestartDownload() {
        lifecycleScope.launch {
            downloadProgress = 0
            updateDownloadButton()
            torrentManager.downloadApp(app)
            downloadProgress = torrentManager.downloadProgress(app)
            updateDownloadButton()
        }
    }

    /**
     * Called when the user presses the "open" button, which is only shown when the user is a member
     * of the app's DAO and has finished downloading the torrent containing the APK.
     *
     * This function:
     * - Locates the APK inside the app's P2P cache directory.
     * - Validates the presence and uniqueness of the APK file.
     * - Launches the APK via an `ExecutionActivity` intent.
     *
     * The method provides error feedback to the user using `Toast` messages
     * and logs warnings for unexpected or invalid states.
     *
     * Preconditions:
     * - The user must be a member of the DAO.
     * - The APK must have already been downloaded.
     */
    private fun onOpenApp() {
        val applicationContext = requireContext()
        val dir = File(applicationContext.cacheDir, "p2p-apps/${app.magnetLink.infoHash}")

        val apkFiles = try {
            findFilesByExtension(dir, setOf(".apk"))
        } catch (e: IllegalArgumentException) {
            e.message?.let {
                Log.w("P2P", it)
                printToast("Directory containing APK not found or invalid.")
            }
            return
        }

        val apkFile: File
        if (apkFiles.isEmpty()) {
            Log.e("P2P", "No APK files found in: ${dir.absolutePath}")
            printToast("Could not find APK in the torrent.")
            return
        } else if (apkFiles.size > 1) {
            Log.e("P2P", "Multiple APK files found in: ${dir.absolutePath}")
            printToast("Found multiple APK's in the torrent.")
            return
        } else {
            apkFile = apkFiles.first()
        }

        if (!apkFile.exists() || !apkFile.isFile) {
            Log.e("P2P", "File not found or invalid: $apkFile")
            printToast("No APK found connected to this DAO.")
            return
        }

        val intent = Intent(applicationContext, ExecutionActivity::class.java).apply {
            putExtra("fileName", apkFile.path)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            applicationContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            printToast("Unable to open APK – app component not found.")
        } catch (e: SecurityException) {
            printToast("Permission denied to launch APK.")
        } catch (e: Exception) {
            printToast("Something went wrong when opening the APK.")
        }
    }

    /**
     * Called when the user agrees to spend bitcoins needed to join the app's DAO.
     */
    private fun onJoinDoa() {
        try {
            lifecycleScope.launch {
                val mostRecentSWBlock =
                    getP2pStoreCommunity().fetchLatestSharedWalletBlock(app.block.calculateHash())
                        ?: app.block
                try {
                    getP2pStoreCommunity().proposeJoinWallet(mostRecentSWBlock).getData()
                } catch (t: Throwable) {
                    Log.e("P2P", "Join wallet proposal failed. ${t.message ?: "No further information"}.")
                }
                updateDownloadButton()
            }
            requireActivity().runOnUiThread {
                updatePolls()
                updateDownloadButton()
            }
        } catch (e: Exception) {
            Log.e("DaoDetailsFragment", "Error joining DAO: ${e.message}")
        }
    }

    /**
     * Sets up the required event listeners to detect when the download state for the torrent
     * changes so we can update the UI.
     */
    private fun setupTorrentDownloadStatus() {
        this.downloadProgress = torrentManager.downloadProgress(this.app)
        if (!this.downloadFinished()) {
            lifecycleScope.launch {
                torrentManager.onStarted.collect { link ->
                    if (link.infoHash == app.magnetLink.infoHash) {
                        downloadProgress = 0
                        updateDownloadButton()
                    }
                }
            }
            lifecycleScope.launch {
                torrentManager.onProgress.collect { data ->
                    val link = data.first
                    val progress = data.second
                    if (link.infoHash == app.magnetLink.infoHash) {
                        downloadProgress = progress
                        updateDownloadButton()
                    }
                }
            }
            lifecycleScope.launch {
                torrentManager.onFinished.collect { link ->
                    if (link.infoHash == app.magnetLink.infoHash) {
                        downloadProgress = 100
                        updateDownloadButton()
                        updateScreenshots()
                    }
                }
            }
        }
    }

    /**
     * Updates all the basic app metadata that is contained in the trustchain block
     */
    private fun updateAppMetaData() {
        binding.appName.text = this.app.name
        binding.appCategory.text = this.app.category
        binding.daoMembersCount.text = this.app.getDoaMemberCount().toString()
        binding.appLatestVersion.text = this.app.version.toString()
        binding.appDescription.text = this.app.description
        binding.daoIcon.setImageResource(this.app.icon)
        val developer = this.app.block.publicKey.toHex().take(8)
        binding.daoDeveloper.text = "Devloper: ${developer}"
    }

    /**
     * Shows/hides/disables UI elements based on whether the user can even use them or not.
     */
    private fun updateUIBasedOnMembership() {
        if (this.app.isDaoMember()) {
            binding.btnFeatureRequest.isEnabled = true
            binding.btnFeatureRequest.alpha = 1.0f
            // Voting card clickability/alpha handled in loadRecentVotingPoll
        } else {
            binding.btnFeatureRequest.isEnabled = false
            binding.btnFeatureRequest.alpha = 0.5f
            // Voting card clickability/alpha handled in loadRecentVotingPoll
        }
    }

    /**
     * Updates the open feature request preview by getting the latest one.
     */
    private fun updateFeatureRequests() {
        val requests = this.app.getFeatureRequests()
        val openRequest = requests.find { r -> !r.solutionAccepted() }
        if (openRequest != null) {
            this.lastestFeatureRequest?.bind(openRequest)
            binding.tvNoPendingFeatureRequests.visibility = View.GONE
        }
        else {
            this.lastestFeatureRequest?.hide()
            binding.tvNoPendingFeatureRequests.visibility = View.VISIBLE
        }
    }

    /**
     * Updates the list of polls/proposals
     */
    private fun updatePolls() {
        val joinPolls = this.app.getOpenDaoJoinPolls();

        if (joinPolls.isNotEmpty()) {
            this.joinPoll?.bind(joinPolls[0])
        } else {
            this.joinPoll?.hide()
        }

       val updatePolls = this.app.getOpenUpdatePolls();
       if (updatePolls.isNotEmpty()) {
           this.updatePoll?.bind(updatePolls[0])
       } else {
           this.updatePoll?.hide()
       }

        // Show/hide the "no active proposals text"
        binding.noProposalsText.visibility =
            if (joinPolls.isEmpty() && updatePolls.isEmpty()) { View.VISIBLE } else { View.GONE }
    }

    /**
     * Updates the download/open button based on the state of DOA and the app download
     */
    private fun updateDownloadButton() {
        if (this._binding == null) return
        if (this.app.isDaoMember()) {
            if (this.downloadFinished()) {
                this.binding.installOpenBtn.isEnabled = true
                this.binding.installOpenBtn.text = "Open"
            }
            else if (this.downloadProgress == null) {
                this.binding.installOpenBtn.isEnabled = true
                this.binding.installOpenBtn.text = "Download"
            }
            else {
                this.binding.installOpenBtn.isEnabled = false
                this.binding.installOpenBtn.text = "${this.downloadProgress}%"
            }
        }
        else if (this.app.isWaitingToJoin()) {
            this.binding.installOpenBtn.isEnabled = false
            this.binding.installOpenBtn.text = "Collecting votes"
        }
        else {
            this.binding.installOpenBtn.isEnabled = true
            this.binding.installOpenBtn.text = "Install"
        }
    }

    /**
     * Takes all of the image files inside the downloaded torrent and shows them as
     */
    private fun updateScreenshots() {
        if (this._binding == null) return;

        val applicationContext = requireContext()
        val dir = File(applicationContext.cacheDir, "p2p-apps/${app.magnetLink.infoHash}")

        val files = try {
            findFilesByExtension(
                dir,
                setOf(".bmp", ".gif", ".heif", ".heic", ".jpeg", ".jpg", ".png", ".webp")
            ).sorted()
        } catch (e: IllegalArgumentException) {
            e.message?.let { Log.d("P2PlayStore", it) }
            emptyList()
        }

        if (files.isEmpty()) {
            Log.d("P2P", "No image files found in: ${dir.path}")
            binding.screenshots.visibility = View.GONE
            binding.screenshotsHeader.visibility = View.GONE
            return
        } else {
            binding.screenshots.visibility = View.VISIBLE
            binding.screenshotsHeader.visibility = View.VISIBLE
        }

        val container = binding.screenshotsContainer
        container.removeAllViews()

        val imageHeightPx = container.layoutParams.height.takeIf { it > 0 } ?: 200
        val maxImageWidthPx = (150 * resources.displayMetrics.density).toInt()


        for (file in files) {
            // First decode only bounds to get original size
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.path, boundsOptions)

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) continue // Skip corrupted images

            // Compute target width preserving aspect ratio
            var targetWidth = (originalWidth.toFloat() / originalHeight.toFloat() * imageHeightPx).toInt()
            if (targetWidth > maxImageWidthPx) {
                targetWidth = maxImageWidthPx
            }

            // Decode image with sampling
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOptions, targetWidth, imageHeightPx)
            }

            try {
                val bitmap = BitmapFactory.decodeFile(file.path, decodeOptions)
                if (bitmap == null) {
                    Log.w("P2P", "Failed to decode bitmap from: ${file.path}")
                    continue
                }

                val imageView = ImageView(applicationContext).apply {
                    layoutParams = LinearLayout.LayoutParams(targetWidth, imageHeightPx).also {
                        it.setMargins(8, 0, 8, 0)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(bitmap)
                }

                container.addView(imageView)
            } catch (e: OutOfMemoryError) {
                Log.e("P2P", "OutOfMemoryError decoding image: ${file.path}", e)
                continue
            } catch (e: Exception) {
                Log.e("P2P", "Unexpected error decoding image: ${file.path}", e)
                continue
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Checks if this user has previously created a DAO join request/poll and if enough signatures
     * have been collected it will create a JOIN_DAO block using the collected signatures.
     */
    private fun finalizeJoinRequest() {
        // User is already a DAO member; do nothing.
        if (this.app.isDaoMember()) return;

        // Has the user even created a join request/poll?
        val myPoll = this.app.getMyDaoJoinPoll() ?: return

        // Do we have enough signatures?
        if (!myPoll.isApproved) return

        val signatures = getP2pStoreCommunity().fetchProposalResponses(
            this.app.daoId,
            myPoll.proposalId
        )

        try {
            this.binding.installOpenBtn.text = "Joining.."

            getP2pStoreCommunity().joinBitcoinWallet(
                app.block.transaction,
                myPoll.blockData,
                signatures,
                requireContext()
            )
            // Add new nonceKey after joining a DAO
            WalletManagerAndroid.getInstance().addNewNonceKey(
                this.app.daoId,
                requireContext()
            )

            // Now update the UI to inform the user they have joined successfully
            val latestApp = this.app.getLatestVersion()
            lifecycleScope.launch(Dispatchers.Main) {
                onChainUpdated(latestApp.block)
            }
        }
        catch (t: Throwable) {
            Log.e("Coin", "Joining failed. ${t.message ?: "No further information"}.")
        }
    }

    /**
     * Checks if the user has submitted an update, which has now been accepted (in which case the
     * submitting user should now create an UPDATE_ACCEPTED block with the collected signatures).
     */
    private fun finalizeUpdate() {
        // Have I proposed any updates that have since been accepted but for which no update block
        // has been released yet?
        val acceptedUpdate = this.app.getMyUpdateProposals()
            .filter { p -> p.isApproved && !p.hasBeenReleased() }
            .maxByOrNull { p -> p.block.timestamp }

        // Looks like there is nothing to do.
        if (acceptedUpdate == null) return

        Log.d("P2PlayStore", "Finalizing update because enough votes have been cast")
        acceptedUpdate.publishUpdate(
            requireContext(),
            requireActivity()
        )

        val latestApp = this.app.getLatestVersion()
        lifecycleScope.launch(Dispatchers.Main) {
            onChainUpdated(latestApp.block)
        }
    }

    /**
     * Sets up the Holder classes for the poll and feature request previews.
     */
    private fun setupPreviews() {
        this.joinPoll = PollPreviewHolder(
            binding.joinProposal,
            R.id.action_appDetailsFragment_to_featureVotingFragment
        );
        this.updatePoll = PollPreviewHolder(
            binding.updateProposal,
            R.id.action_appDetailsFragment_to_featureVotingFragment
        );
        this.lastestFeatureRequest = FeatureRequestPreviewHolder(
            binding.latestFeatureRequest,
            R.id.action_appDetailsFragment_to_featureSolutionFragment
        )
    }

    /**
     * Sets up all the click event handlers for buttons on the page
     */
    private fun setupClickListeners() {
        binding.installOpenBtn.setOnClickListener {
            if (this.app.isDaoMember()) {
                if (this.downloadProgress == null) {
                    this.onRestartDownload()
                }
                else if (this.downloadFinished()) {
                    this.onOpenApp()
                }
            } else {
                this.onInstallApp()
            }
        }

        binding.btnFeatureRequest.setOnClickListener {
            if (this.app.isDaoMember()) {
                findNavController()
                    .navigate(
                        R.id.action_appDetailsFragment_to_featureListFragment,
                        Bundle().apply {
                            putString("daoId", app.daoId)
                        }
                    )
            }
        }


        binding.btnOpenWallet.setOnClickListener {
            findNavController()
                .navigate(
                    R.id.action_appDetails_to_daoWalletFragment,
                    Bundle().apply {
                        putString("daoId", app.daoId)
                    }
                )
        }

        binding.btnSeeAllVotes.setOnClickListener {
            findNavController()
                .navigate(
                    R.id.action_appDetailsFragment_to_allVotingPollsFragment,
                    Bundle().apply {
                        putString("daoId", app.daoId)
                    }
                )
        }

        binding.btnSeeAllFeatures.setOnClickListener {
            val bundle = Bundle().apply {
                putString("daoId", app.daoId)
            }
            findNavController()
                .navigate(R.id.action_appDetailsFragment_to_featureListFragment, bundle)
        }
    }
}
