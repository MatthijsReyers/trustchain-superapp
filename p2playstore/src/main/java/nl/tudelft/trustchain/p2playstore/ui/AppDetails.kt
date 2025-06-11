package nl.tudelft.trustchain.p2playstore.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTD
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTD
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureSolutionTransactionData
import nl.tudelft.trustchain.p2playstore.blockdata.VotingPollHelper
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.databinding.FragmentAppDetailsBinding
import nl.tudelft.trustchain.p2playstore.ExecutionActivity
import nl.tudelft.trustchain.p2playstore.P2PlayStoreMainActivity
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.TorrentManager
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestData
import nl.tudelft.trustchain.p2playstore.utils.AppUtils
import nl.tudelft.trustchain.p2playstore.utils.DebugUtils.printToast
import nl.tudelft.trustchain.p2playstore.models.P2PlayStoreApp
import nl.tudelft.trustchain.p2playstore.utils.MagnetLink
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils.parseMagnet

import java.io.File

class AppDetails : BaseFragment() {
    private lateinit var torrentManager: TorrentManager

    private var _binding: FragmentAppDetailsBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var daoBlock: TrustChainBlock
    private lateinit var daoData: SWJoinBlockTD

    private lateinit var app: P2PlayStoreApp

    /**
     * Integer between 0-100, this indicates how far along the torrent download for this apps
     * APK file is.
     */
    private var downloadProgress: Int? = null;

    /**
     * Has the torrent with the APK file for this app finished downloading yet?
     */
    private fun downloadFinished(): Boolean {
        return this.downloadProgress != null && this.downloadProgress as Int >= 100
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        // The previous fragment (home) tells us which block/app/version to show
        val args = this.requireArguments();
        val publicKey = args.getByteArray("publicKey")!!
        val sequenceNumber = args.getInt("sequenceNumber").toUInt()

        // Actually retrieve the block
        val community = this.getTrustChainCommunity()
        this.daoBlock = community.database.get(publicKey, sequenceNumber)!!
        this.daoData = SWJoinBlockTransactionData(daoBlock.transaction).getData()

        this.app = P2PlayStoreApp(this.daoBlock)

        torrentManager = (this.activity as P2PlayStoreMainActivity).torrentManager
        this.downloadProgress = torrentManager.downloadProgress(this.app);

        this.setupTorrentDownloadStatus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            lifecycleScope.launch {
                loadDaoDetails()
            }
            setupClickListeners()
        }
        catch (e: Throwable) {
            Log.e("DaoDetailsFragment", "Error loading DAO details: ${e.message}")
            Toast.makeText(context, "Error loading DAO details.", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun loadDaoDetails() {
        updateAppMetaData()
        updateUIBasedOnMembership()
        updateDownloadButton()
        loadRecentVotingPoll()
        loadLatestPendingFeatureRequest()
        loadLatestApprovedUpdate()
    }

    /**
     * Called whenever new blocks with the DAO ID for this app are detected, practically this means
     * we want to update the whole UI since votes/version updates might have changed.
     */
    fun onChainUpdated(block: TrustChainBlock) {
        Log.d("P2pStore", "Chain update ${block.type}")

        this.updateAppMetaData()
        this.updateDownloadButton();
    }

    private fun setupTorrentDownloadStatus() {
        val magnetLink = MagnetUtils.parseMagnet(this.daoBlock.transaction["magnetLink"] as String)
        this.downloadProgress = torrentManager.downloadProgress(this.app);
        if (!this.downloadFinished()) {
            lifecycleScope.launch {
                torrentManager.onStarted.collect { link ->
                    if (link.infoHash == magnetLink.infoHash) {
                        downloadProgress = 0
                        updateDownloadButton()
                    }
                }
            }
            lifecycleScope.launch {
                torrentManager.onProgress.collect { data ->
                    val link = data.first
                    val progress = data.second
                    if (link.infoHash == magnetLink.infoHash) {
                        downloadProgress = progress
                        updateDownloadButton()
                    }
                }
            }
            lifecycleScope.launch {
                torrentManager.onFinished.collect { link ->
                    if (link.infoHash == magnetLink.infoHash) {
                        downloadProgress = 100
                        updateDownloadButton()
                    }
                }
            }
        }
    }

    private fun updateAppMetaData() {
        binding.appName.text = this.app.getName() ?: "[Unknown]"
        binding.appCategory.text = this.app.getCategory() ?: "General"
        binding.daoMembersCount.text = this.app.getDoaMemberCount().toString()
        binding.appLatestVersion.text = this.app.getVersion().toString()
        binding.appDescription.text = this.app.getDescription()
        binding.daoIcon.setImageResource(this.app.getIcon())
    }

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

    // This function finds the latest solution block that has met the voting threshold for its feature request.
    private fun loadLatestApprovedUpdate() {
        lifecycleScope.launch {
            val maxRetries = 3
            val retryDelayMillis = 1000L // 1 second delay

            for (retry in 0..maxRetries) {
                try {
                    val daoUniqueId = daoData.SW_UNIQUE_ID

                    // Get all feature requests for this DAO
                    val featureRequests = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureRequestsForDao(daoUniqueId)
                    }
                    Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): Found ${featureRequests.size} feature requests for DAO $daoUniqueId")
                    // Get all solution blocks for this DAO
                    val solutionBlocks = withContext(Dispatchers.IO) {
                        p2playStore.getSolutionBlocksForDaoAndFeature(daoUniqueId)
                    }
                    Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): Found ${solutionBlocks.size} solution blocks for DAO $daoUniqueId.")

                    // Get all votes for this DAO
                    val votes = withContext(Dispatchers.IO) {
                        p2playStore.getVotesForSolution(daoUniqueId)
                    }
                    Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): Found ${votes.size} votes for DAO $daoUniqueId.")


                    val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
                    val votingThreshold = daoData.SW_VOTING_THRESHOLD

                    // Filter for approved solutions
                    val approvedSolutionDataWithBlocks = solutionBlocks.mapNotNull { block ->
                        try {
                            val solutionData = FeatureSolutionTransactionData(block.transaction).getData()
                            block to solutionData
                        } catch (e: Exception) {
                            Log.e("DaoDetailsFragment", "Failed to parse solution block in approved filter: ${e.message}")
                            null
                        }
                    }.filter { (block, solution) ->
                        // Find the corresponding feature request
                        val correspondingRequest = featureRequests.find { it.featureId == solution.featureId }

                        // Only consider solutions linked to a feature request
                        if (correspondingRequest != null) {
                            // Count votes for this specific solution
                            val votesForSolution = votes.filter { it.solutionId == solution.solutionId }
                            val yesVotes = votesForSolution.count { it.isYes }
                            val votesNeeded = votingThreshold
                            // An update is approved if YES votes meet the threshold
                            yesVotes >= votesNeeded
                        } else {
                            false
                        }
                    }
                    Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate: Found ${approvedSolutionDataWithBlocks.size} approved solutions with blocks for DAO $daoUniqueId.")
                    // Sort by block timestamp descending and take the first one
                    val latestApprovedSolutionWithBlock = approvedSolutionDataWithBlocks
                        .sortedByDescending { it.first.timestamp.time }
                        .firstOrNull()


                    if (latestApprovedSolutionWithBlock != null) {
                        val (block, solution) = latestApprovedSolutionWithBlock
                        Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate: Latest approved solution found: ${solution.solutionId} (Feature ${solution.featureId}), Block Timestamp: ${block.timestamp.time}")
                        // Update UI with latest approved version info
                        // Set click listener for update button
                        // Success, break out of retry loop
                        return@launch

                    } else {
                        Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): No approved solutions found for DAO $daoUniqueId.")
                    }
                } catch (e: Exception) {
                    Log.e("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): Error loading latest approved update: ${e.message}")
                }
                if (retry < maxRetries) {
                    Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate: Retrying in ${retryDelayMillis}ms...")
                }
            }
        }
    }

    private fun loadRecentVotingPoll() {
        lifecycleScope.launch {
            val maxRetries = 3
            val retryDelayMillis = 1000L

            for (retry in 0..maxRetries) {
                try {
                    val daoUniqueId = daoData.SW_UNIQUE_ID

                    // Get all feature requests for this DAO
                    val featureRequests = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureRequestsForDao(daoUniqueId)
                    }
                    Log.d("DaoDetailsFragment", "loadRecentVotingPoll (Attempt ${retry + 1}): Found ${featureRequests.size} feature requests for DAO $daoUniqueId")


                    val latestVotableSolutionWithBlock = withContext(Dispatchers.IO) {
                        p2playStore.fetchLatestVotableSolutionBlock(daoUniqueId, featureRequests)
                    }

                    if (latestVotableSolutionWithBlock != null) {
                        val (latestVotableSolution, solutionBlock) = latestVotableSolutionWithBlock
                        Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Latest votable solution found: ${latestVotableSolution.solutionId} for feature ${latestVotableSolution.featureId} in DAO $daoUniqueId")
//                      TODO: might not be necessary but looks cool
                        binding.votingCard.visibility = View.VISIBLE
                        // Hide the "See All" button initially if we only show the latest,

                        val votes = withContext(Dispatchers.IO) {
                            // Fetch votes specifically for this solution within this DAO
                            p2playStore.getVotesForSolution(daoUniqueId, latestVotableSolution.solutionId)
                        }
                        Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Found ${votes.size} votes for solution ${latestVotableSolution.solutionId} in DAO ${daoUniqueId}")


                        // Find the corresponding feature request again to get its details
                        val correspondingRequest = featureRequests.find { it.featureId == latestVotableSolution.featureId }

                        if (correspondingRequest != null) {
                            val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
                            val votingThreshold = daoData.SW_VOTING_THRESHOLD
                            val myPublicKey = p2playStore.myPeer.publicKey.keyToBin().toHex()
                            val hasUserVoted = votes.any { it.voterPublicKey == myPublicKey }
                            val userVote = votes.find { it.voterPublicKey == myPublicKey }?.isYes

                            // Create a VotingPoll object and update the UI
                            val votingPoll = VotingPollHelper.createVotingPoll(
                                correspondingRequest, // Pass the actual request
                                latestVotableSolution,
                                votes,
                                totalMembers,
                                votingThreshold,
                                hasUserVoted = hasUserVoted,
                                userVote = userVote
                            )
                            Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Created voting poll for UI. Active: ${votingPoll.isActive}, Voted: ${votingPoll.hasUserVoted}, Approved: ${votingPoll.isApproved}")


                            updateVotingCardUI(votingPoll)

                            // Set click listeners based on membership and voting status
                            if (app.isDaoMember()) { // Only members can interact with the voting card
                                binding.votingCard.setOnClickListener {
                                    navigateToVotingFragment(daoBlock.blockId, latestVotableSolution.solutionId)
                                }
                                // Show vote button only if active, member, and hasn't voted
                                if (votingPoll.isActive && !hasUserVoted) {
                                    binding.btnVote.visibility = View.VISIBLE
                                    binding.btnVote.setOnClickListener {
                                        navigateToVotingFragment(daoBlock.blockId, latestVotableSolution.solutionId)
                                    }
                                } else {
                                    binding.btnVote.visibility = View.GONE
                                }

                                binding.votingCard.isClickable = true
                                binding.votingCard.alpha = 1.0f

                            } else {
                                // If not a member, make card non-interactive and hide vote button
                                binding.votingCard.setOnClickListener(null)
                                binding.btnVote.setOnClickListener(null)
                                binding.btnVote.visibility = View.GONE
                                binding.votingCard.isClickable = false
                                binding.votingCard.alpha = 0.5f // Gray out
                            }
                            // Always enable "See All" votes if the card is visible
                            binding.btnSeeAllVotes.isEnabled = true
                            binding.btnSeeAllVotes.alpha = 1.0f

                            // Success, break out of retry loop
                            return@launch

                        } else {
                            Log.e("DaoDetailsFragment", "loadRecentVotingPoll: Found votable solution but corresponding open feature request not found. Solution ID: ${latestVotableSolution.solutionId} in DAO ${daoUniqueId}")
                            binding.votingCard.visibility = View.GONE
                            binding.btnVote.visibility = View.GONE
                            binding.btnSeeAllVotes.isEnabled = false
                            binding.btnSeeAllVotes.alpha = 0.5f
                        }

                    } else {
                        // No open feature request has a submitted solution to vote on
                        Log.d("DaoDetailsFragment", "loadRecentVotingPoll (Attempt ${retry + 1}): No latest votable solution found for DAO $daoUniqueId.")
                        binding.votingCard.visibility = View.GONE
                        binding.btnVote.visibility = View.GONE
                        binding.btnSeeAllVotes.isEnabled = false // Disable "See All" if no polls
                        binding.btnSeeAllVotes.alpha = 0.5f

                        // If no votable solution is found, we might need to wait for sync.
                        if (retry < maxRetries) {
                            Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Retrying in ${retryDelayMillis}ms...")
                        }
                    }

                } catch (e: Exception) {
                    Log.e("DaoDetailsFragment", "loadRecentVotingPoll (Attempt ${retry + 1}): Error loading recent voting poll: ${e.message}")
                    if (retry < maxRetries) {
                        Log.e("DaoDetailsFragment", "loadRecentVotingPoll: Retrying in ${retryDelayMillis}ms due to error...")
                    } else {
                        // exhausted retries, show error UI
                        binding.votingCard.visibility = View.GONE
                        binding.btnVote.visibility = View.GONE
                        binding.btnSeeAllVotes.isEnabled = false
                        binding.btnSeeAllVotes.alpha = 0.5f
                    }
                }
            }
        }
    }

    private fun loadLatestPendingFeatureRequest() {
        lifecycleScope.launch {
            val maxRetries = 3
            val retryDelayMillis = 1000L // 1 second delay

            for (retry in 0..maxRetries) {
                try {
                    val daoUniqueId = daoData.SW_UNIQUE_ID

                    val latestPendingRequest = withContext(Dispatchers.IO) {
                        p2playStore.fetchLatestPendingRequestBlock(daoUniqueId)
                    }


                    if (latestPendingRequest != null) {
                        Log.d("DaoDetailsFragment", "loadLatestPendingFeatureRequest: Latest pending request found: ${latestPendingRequest.featureId} for DAO $daoUniqueId")
                        binding.latestFeatureRequestPreviewCard.visibility = View.VISIBLE
                        binding.tvNoPendingFeatureRequests.visibility = View.GONE

                        binding.tvLatestFeatureTitle.text = latestPendingRequest.title
                        binding.tvLatestFeatureDescription.text = latestPendingRequest.description
                        binding.tvLatestFeatureReward.text = "Reward: ${latestPendingRequest.reward} sats"

                        // Still show 0 solutions count for clarity
                        binding.tvLatestFeatureSolutionCount.text = "0 solution(s)"


                        binding.latestFeatureRequestPreviewCard.setOnClickListener {
                            // Navigate to the submit solution fragment for this request
                            navigateToSubmitSolution(latestPendingRequest)
                        }
                        binding.latestFeatureRequestPreviewCard.isClickable = true
                        binding.latestFeatureRequestPreviewCard.alpha = 1.0f

                        // Success, break out of retry loop
                        return@launch

                    } else {
                        // No pending feature requests without solutions
                        Log.d("DaoDetailsFragment", "loadLatestPendingFeatureRequest (Attempt ${retry + 1}): No latest pending request found for DAO $daoUniqueId.")
                        binding.latestFeatureRequestPreviewCard.visibility = View.GONE
                        binding.tvNoPendingFeatureRequests.visibility = View.VISIBLE
                        binding.latestFeatureRequestPreviewCard.setOnClickListener(null)
                        binding.latestFeatureRequestPreviewCard.isClickable = false
                        binding.latestFeatureRequestPreviewCard.alpha = 0.5f

                    }

                } catch (e: Exception) {
                    Log.e("DaoDetailsFragment", "loadLatestPendingFeatureRequest (Attempt ${retry + 1}): Error loading latest pending feature request: ${e.message}")
                    if (retry < maxRetries) {
                        Log.e("DaoDetailsFragment", "loadLatestPendingFeatureRequest: Retrying in ${retryDelayMillis}ms due to error...")
                    } else {
                        // exhausted retries, show error UI
                        binding.latestFeatureRequestPreviewCard.visibility = View.GONE
                        binding.tvNoPendingFeatureRequests.visibility = View.VISIBLE
                        binding.latestFeatureRequestPreviewCard.setOnClickListener(null)
                        binding.latestFeatureRequestPreviewCard.isClickable = false
                        binding.latestFeatureRequestPreviewCard.alpha = 0.5f
                    }
                }
            }
        }
    }

    /**
     * Called when the user presses the install button (which is only shown when the user is not
     * yet in the app's DAO), effectively this means they will spend bitcoins to join the shared
     * wallet, so we'll ask them for confirmation of that first.
     */
    private fun onInstallApp() {
        val entranceFee = daoData.SW_ENTRANCE_FEE
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
     * of the app's DAO and has finished downloading the
     */
    private fun onOpenApp() {
        val applicationContext = requireContext()

        val rawMagnetLink = this.daoBlock.transaction["magnetLink"] as? String
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

    /**
     * Called when the user agrees to spend bitcoins to join the app's DAO.
     */
    private fun onJoinDoa() {
        try {
            lifecycleScope.launch {
                joinSharedWalletClicked(daoBlock)
                loadRecentVotingPoll()
                loadLatestPendingFeatureRequest()
                loadLatestApprovedUpdate()
                updateDownloadButton();
            }
        } catch (e: Exception) {
            Log.e("DaoDetailsFragment", "Error joining DAO: ${e.message}")
        }
    }

    /**
     * Updates the download/open button based on the state of DOA and the app download
     */
    private fun updateDownloadButton() {
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

    private fun updateVotingCardUI(poll: nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll) {
        binding.updateTitle.text = poll.title
        binding.votesRequiredText.visibility = View.VISIBLE
        binding.totalVotes.visibility = View.VISIBLE

        binding.yesPercentage.text = "${poll.yesPercentage}%"
        binding.noPercentage.text = "${poll.noPercentage}%"
        binding.pendingPercentage.text = "${poll.pendingPercentage}%"

        binding.votesRequiredText.text = "${poll.yesVotes} of ${poll.votesNeeded} votes needed"
        binding.totalVotes.text =
            "${poll.yesVotes + poll.noVotes} of ${poll.totalMembers} members voted"

        AppUtils.updateProgressBars(
            binding.root,
            binding.yesProgressBar,
            binding.noProgressBar,
            binding.pendingProgressBar,
            poll.yesPercentage,
            poll.noPercentage,
            poll.pendingPercentage
        )
        updateVotingState(poll)
    }

    private fun updateVotingState(poll: nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll) {
        binding.btnVote.visibility = View.GONE
        binding.votingStatus.visibility = View.GONE

        when {
            !poll.isActive && poll.isApproved -> { // Voting closed and approved
                binding.votingStatus.text = "Approved"
                binding.votingStatus.setTextColor(
                    resources.getColor(android.R.color.holo_green_dark, null)
                )
                binding.votingStatus.visibility = View.VISIBLE
                binding.votingCard.isClickable =
                    false // Make card non-clickable after status is final
                binding.votingCard.alpha = 0.5f // Gray out after status is final
            }
            !poll.isActive -> { // Voting closed and not approved
                binding.votingStatus.text = "Voting Closed"
                binding.votingStatus.setTextColor(
                    resources.getColor(android.R.color.darker_gray, null)
                )
                binding.votingStatus.visibility = View.VISIBLE
                binding.votingCard.isClickable =
                    false // Make card non-clickable after status is final
                binding.votingCard.alpha = 0.5f // Gray out after status is final
            }
            poll.hasUserVoted -> { // Voting active, user has voted
                binding.btnVote.isEnabled = false // Disable vote button if already voted
                binding.votingCard.alpha = 1.0f // Full alpha if active/voted
            }
            app.isDaoMember() -> { // Voting active, user is member, user has NOT voted
                binding.btnVote.visibility = View.VISIBLE
                binding.btnVote.isEnabled = true // Enable vote button
                binding.votingCard.alpha = 1.0f // Full alpha if active/can vote
            }
            else -> {
                binding.votingCard.alpha = 0.5f // Gray out if not member
            }
        }
    }

    private fun setupClickListeners() {
        binding.installOpenBtn.setOnClickListener {
            if (this.app.isDaoMember()) {
                if (this.downloadProgress == null) {
                    this.onRestartDownload();
                }
                else if (this.downloadFinished()) {
                    this.onOpenApp()
                }
            } else {
                this.onInstallApp()
            }
        }

        // btnFeatureRequest click listener is correct for navigating to FeatureListFragment
        binding.btnFeatureRequest.setOnClickListener {
            if (this.app.isDaoMember()) {
                val bundle =
                    Bundle().apply {
                        putString("blockId", daoBlock.blockId) // Pass DAO block ID
                        putString("daoUniqueId", daoData.SW_UNIQUE_ID) // Pass DAO unique ID")
                    }
                findNavController()
                    .navigate(
                        // TODO: verify that these actions work
                        R.id.action_appDetailsFragment_to_featureListFragment,
                        bundle
                    )
            }
        }

        // btnSeeAllVotes click listener (Existing) - Navigate to AllVotingPollsFragment
        binding.btnSeeAllVotes.setOnClickListener {
            if (app.isDaoMember()) {
                // Navigate to all voting polls
                val bundle =
                    Bundle().apply {
                        putString("blockId", daoBlock.blockId)
                        putString("daoUniqueId", daoData.SW_UNIQUE_ID)
                    }
                findNavController()
                    .navigate(R.id.action_appDetailsFragment_to_allVotingPollsFragment, bundle)
            }
        }

        binding.btnSeeAllFeatures.setOnClickListener { // Use the original ID from XML
            // Navigate to the FeatureListFragment
            val bundle =
                Bundle().apply {
                    putString("blockId", daoBlock.blockId)
                    putString("daoUniqueId", daoData.SW_UNIQUE_ID)
                    // putString("daoId", daoData.SW_UNIQUE_ID)
                }
            findNavController()
                .navigate(R.id.action_appDetailsFragment_to_featureListFragment, bundle)
        }
        // The click listener for latest_feature_request_preview_card is set dynamically in
        // loadLatestPendingFeatureRequest
    }

    /**
     * This function attaches a bunch of event listeners to the trustchain so we can detect new
     * blocks when they are created and update the UI accordingly
     */
    private fun setupChainListeners() {
        val listener: BlockListener = object: BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                // TODO: Replace with BaseTransactionData class for better type safety, since there
                // is really no guarantee that it will be this kind of block.
                val data = SWJoinBlockTransactionData(block.transaction).getData()
                // Is the new block relevant for this app?
                if (data.SW_UNIQUE_ID == app.daoId) {
                    onChainUpdated(block)
                }

            }
        }
        val trustChain = getTrustChainCommunity()
        trustChain.addListener(P2pStoreCommunity.JOIN_BLOCK, listener);
        trustChain.addListener(P2pStoreCommunity.JOIN_REQUEST_BLOCK, listener);
        trustChain.addListener(P2pStoreCommunity.VOTE_YES_BLOCK, listener);
        trustChain.addListener(P2pStoreCommunity.VOTE_NO_BLOCK, listener);
        trustChain.addListener(P2pStoreCommunity.PROPOSE_UPDATE_BLOCK, listener);
        trustChain.addListener(P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK, listener);
        trustChain.addListener(P2pStoreCommunity.FEATURE_REQUEST_BLOCK, listener);
    }

    /**
     * Join a shared bitcoin wallet.
     */
    private suspend fun joinSharedWalletClicked(block: TrustChainBlock) {
        val mostRecentSWBlock =
            getP2pStoreCommunity().fetchLatestSharedWalletBlock(block.calculateHash())
                ?: block
        // Add a proposal to trust chain to join a shared wallet
        val proposeBlockData =
            try {
                getP2pStoreCommunity().proposeJoinWallet(mostRecentSWBlock).getData()
            } catch (t: Throwable) {
                Log.e("P2P", "Join wallet proposal failed. ${t.message ?: "No further information"}.")
//                setAlertText(t.message ?: "Unexpected error occurred. Try again")
                return
            }

        val context = requireContext()
        // Wait and collect signatures
        var signatures: List<VoteYesData>? = null
        while (signatures == null) {
            delay(10_000)
            signatures = collectJoinWalletResponses(proposeBlockData)
        }

        // Create a new shared wallet using the signatures of the others.
        // Broadcast the new shared bitcoin wallet on trust chain.
        try {
            getP2pStoreCommunity().joinBitcoinWallet(
                mostRecentSWBlock.transaction,
                proposeBlockData,
                signatures,
                context
            )
            // Add new nonceKey after joining a DAO
            WalletManagerAndroid.getInstance()
                .addNewNonceKey(proposeBlockData.SW_UNIQUE_ID, context)
        } catch (t: Throwable) {
            Log.e("Coin", "Joining failed. ${t.message ?: "No further information"}.")
//            setAlertText(t.message ?: "Unexpected error occurred. Try again")
        }

    }

    /**
     * Collect the signatures of a join proposal
     */
    private suspend fun collectJoinWalletResponses(blockData: JoinRequestData): List<VoteYesData>? {
        val responses =
            getP2pStoreCommunity().fetchProposalResponses(
                blockData.SW_UNIQUE_ID,
                blockData.SW_UNIQUE_PROPOSAL_ID
            )
        Log.i(
            "P2P",
            "Waiting for signatures. ${responses.size}/${blockData.SW_SIGNATURES_REQUIRED} received!"
        )

        if (responses.size >= blockData.SW_SIGNATURES_REQUIRED) {
            return responses
        }
        return null
    }

    // Helper function to navigate to FeatureVotingFragment
    private fun navigateToVotingFragment(daoBlockId: String, solutionId: String) {
        val bundle =
            Bundle().apply {
                putString("blockId", daoBlockId)
                putString("solutionId", solutionId)
                putString("daoUniqueId", daoData.SW_UNIQUE_ID)
            }
        findNavController()
            .navigate(R.id.action_appDetailsFragment_to_featureVotingFragment, bundle)
    }

    // Helper function to navigate to FeatureSolutionFragment
    private fun navigateToSubmitSolution(featureRequest: FeatureRequestTD) {
        val bundle =
            Bundle().apply {
                putString("featureId", featureRequest.featureId)
//                    putString("daoId", daoBlock.blockId) // Pass DAO unique ID
                putString("daoUniqueId", featureRequest.daoId) // Pass DAO block ID
                putString("featureTitle", featureRequest.title)
                putString("featureDescription", featureRequest.description)
            }

        findNavController().navigate(R.id.action_appDetailsFragment_to_featureSolutionFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
