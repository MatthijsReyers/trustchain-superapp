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
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.transactionData.*
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.databinding.FragmentAppDetailsBinding
import nl.tudelft.trustchain.p2playstore.ExecutionActivity
import nl.tudelft.trustchain.p2playstore.P2PlayStoreMainActivity
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.TorrentManager
import nl.tudelft.trustchain.p2playstore.transactionData.*
import nl.tudelft.trustchain.p2playstore.utils.AppUtils
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.utils.AppUtils.printToast

import java.io.File

class AppDetails : BaseFragment() {
    private lateinit var torrentManager: TorrentManager

    private var _binding: FragmentAppDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var daoBlock: TrustChainBlock
    private lateinit var app: P2playApp

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
        this.app = P2playApp(this.daoBlock)

        torrentManager = (this.activity as P2PlayStoreMainActivity).torrentManager
        this.downloadProgress = torrentManager.downloadProgress(this.app);

        this.setupTorrentDownloadStatus()
        this.setupChainListeners()
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
            this.setupClickListeners()
            this.updateAppMetaData()
            this.updateDownloadButton()
            lifecycleScope.launch {
                loadRecentVotingPoll()
                loadLatestPendingFeatureRequest()
                updateUIBasedOnMembership()
            }
        }
        catch (e: Throwable) {
            Log.e("DaoDetailsFragment", "Error loading DAO details: ${e.message}")
            Toast.makeText(context, "Error loading DAO details.", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    /**
     * Called whenever new blocks with the DAO ID for this app are detected, practically this means
     * we want to update the whole UI since votes/version updates might have changed.
     */
    fun onChainUpdated(block: TrustChainBlock) {
        Log.d("P2pStore", "Chain update ${block.type}")

        when (block.type) {
            // Was a new version of the app released?
            P2pStoreCommunity.JOIN_BLOCK, P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> {
                this.daoBlock = block
                this.app = P2playApp(this.daoBlock)
                this.updateAppMetaData()
                this.updateDownloadButton()

                // Join block can indicate a change in membership
                this.updateUIBasedOnMembership()
            }
            // ALl the other possible blocks are essentially just updates for various polls,
            else -> {
                this.loadRecentVotingPoll()
                this.loadLatestPendingFeatureRequest()
            }
        }
    }

    /**
     * Called when the user presses the install button (which is only shown when the user is not
     * yet in the app's DAO), effectively this means they will spend bitcoins to join the shared
     * wallet, so we'll ask them for confirmation of that first.
     */
    private fun onInstallApp() {
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
     * of the app's DAO and has finished downloading the
     */
    private fun onOpenApp() {
        val applicationContext = requireContext()

        val apkPath = "${applicationContext.cacheDir}/p2p-apps/${app.magnetLink.infoHash}" +
            "/${app.magnetLink.displayName}"
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
     * Called when the user agrees to spend bitcoins needed to join the app's DAO.
     */
    private fun onJoinDoa() {
        try {
            lifecycleScope.launch {
                joinSharedWalletClicked(daoBlock)
                loadRecentVotingPoll()
                loadLatestPendingFeatureRequest()
                updateDownloadButton();
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
        this.downloadProgress = torrentManager.downloadProgress(this.app);
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

        binding.daoDeveloper.text = "Creator: ${this.daoBlock.publicKey.toHex().take(8)}..."
    }

    /**
     * Shows/hides/disables UI elements based on whether the user can even use them or not.
     */
    private fun updateUIBasedOnMembership() {
        if (this.app.isDaoMember()) {
            binding.btnFeatureRequest.isEnabled = true
            binding.btnFeatureRequest.alpha = 1.0f
        } else {
            binding.btnFeatureRequest.isEnabled = false
            binding.btnFeatureRequest.alpha = 0.5f
        }
    }
    private fun loadRecentVotingPoll() {
        lifecycleScope.launch {
            val maxRetries = 3
            val retryDelayMillis = 1000L

            for (retry in 0..maxRetries) {
                try {
                    val daoUniqueId = app.daoId

                    // Get all feature requests for this DAO
                    val featureRequests = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureRequestsForDao(daoUniqueId)
                    }
                    Log.d("DaoDetailsFragment", "loadRecentVotingPoll (Attempt ${retry + 1}): Found ${featureRequests.size} feature requests for DAO $daoUniqueId")

                    // Get all feature solutions for this DAO
                    val featureSolutions = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureSolutionsForDao(daoUniqueId)
                    }

                    // Fetch blocks and insert times for all solutions
                    val solutionsWithBlocks = featureSolutions.mapNotNull { solution ->
                        val solutionBlock = withContext(Dispatchers.IO) {
                            p2playStore.findProposalBlock(daoUniqueId, solution.SW_UNIQUE_PROPOSAL_ID)
                        }
                        if (solutionBlock != null) {
                            solution to solutionBlock // Pair the solution data with its block
                        } else {
                            Log.w("DaoDetailsFragment", "loadRecentVotingPoll: Block not found for solution ${solution.SW_UNIQUE_PROPOSAL_ID}")
                            null
                        }
                    }

                    // Find the latest feature solution block that corresponds to an OPEN feature request
                    val latestVotableSolutionBlockPair = solutionsWithBlocks
                        .filter { (solution, _) ->
                            featureRequests.any { request ->
                                // Use FEATURE_REQUEST_ID to match solutions to requests
                                request.FEATURE_REQUEST_ID == solution.FEATURE_REQUEST_ID && request.FEATURE_STATUS == "OPEN"
                            }
                        }
                        // Order by the block's insert time to get the latest using maxWithOrNull and compareBy
                        // Used chatgpt for this but it should be just filtering on timestamp? got weird error
                        .maxWithOrNull(compareBy { (_, block) -> block.insertTime?.time ?: 0L })


                    if (latestVotableSolutionBlockPair != null) {
                        val (latestVotableSolution, latestSolutionBlock) = latestVotableSolutionBlockPair
                        Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Latest votable solution found: ${latestVotableSolution.SW_UNIQUE_PROPOSAL_ID} for feature ${latestVotableSolution.FEATURE_REQUEST_ID} in DAO $daoUniqueId")

                        // Get the voting poll for this solution
                        val votingPoll = withContext(Dispatchers.IO) {
                            p2playStore.getVotingPoll(daoUniqueId, latestVotableSolution.SW_UNIQUE_PROPOSAL_ID)
                        }

                        if (votingPoll != null) {
                            // Check if binding is null before accessing it
                            if (_binding == null) {
                                Log.w("DaoDetailsFragment", "loadRecentVotingPoll: View destroyed, skipping UI update.")
                                return@launch // Exit the coroutine if the view is gone
                            }

                            binding.votingCard.visibility = View.VISIBLE
                            updateVotingCardUI(votingPoll)
                            updateVotingState(votingPoll)

                            // Set click listeners based on membership and voting status
                            if (app.isDaoMember()) {
                                binding.votingCard.setOnClickListener {
                                    // Pass DAO block ID, solution proposal ID, and DAO unique ID
                                    navigateToVotingFragment(daoBlock.blockId, latestVotableSolution.SW_UNIQUE_PROPOSAL_ID, daoUniqueId)
                                }

                                if (votingPoll.isActive && !votingPoll.hasUserVoted) {
                                    binding.btnVote.visibility = View.VISIBLE
                                    binding.btnVote.setOnClickListener {
                                        // Pass DAO block ID, solution proposal ID, and DAO unique ID
                                        navigateToVotingFragment(daoBlock.blockId, latestVotableSolution.SW_UNIQUE_PROPOSAL_ID, daoUniqueId)
                                    }
                                } else {
                                    binding.btnVote.visibility = View.GONE
                                }

                                binding.votingCard.isClickable = true
                                binding.votingCard.alpha = 1.0f
                            } else {
                                binding.votingCard.setOnClickListener(null)
                                binding.btnVote.setOnClickListener(null)
                                binding.btnVote.visibility = View.GONE
                                binding.votingCard.isClickable = false
                                binding.votingCard.alpha = 0.5f
                            }

                            binding.btnSeeAllVotes.isEnabled = true
                            binding.btnSeeAllVotes.alpha = 1.0f
                            return@launch
                        }
                    } else {
                        Log.d("DaoDetailsFragment", "loadRecentVotingPoll (Attempt ${retry + 1}): No latest votable solution found for DAO $daoUniqueId.")
                        // Check if binding is null before accessing it
                        if (_binding == null) {
                            Log.w("DaoDetailsFragment", "loadRecentVotingPoll: View destroyed, skipping UI update (no votable solution).")
                            return@launch // Exit the coroutine if the view is gone
                        }
                        binding.votingCard.visibility = View.GONE
                        binding.btnVote.visibility = View.GONE
                        binding.btnSeeAllVotes.isEnabled = false
                        binding.btnSeeAllVotes.alpha = 0.5f

                        if (retry < maxRetries) {
                            Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Retrying in ${retryDelayMillis}ms...")
                            delay(retryDelayMillis)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("DaoDetailsFragment", "loadRecentVotingPoll (Attempt ${retry + 1}): Error loading recent voting poll: ${e.message}")
                    // Check if binding is null before accessing it
                    if (_binding == null) {
                        Log.w("DaoDetailsFragment", "loadRecentVotingPoll: View destroyed during error handling, skipping UI update.")
                        return@launch // Exit the coroutine if the view is gone
                    }
                    if (retry < maxRetries) {
                        Log.e("DaoDetailsFragment", "loadRecentVotingPoll: Retrying in ${retryDelayMillis}ms due to error...")
                        delay(retryDelayMillis)
                    } else {
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
            val retryDelayMillis = 1000L

            for (retry in 0..maxRetries) {
                try {
                    val daoUniqueId = app.daoId

                    // Get all feature requests for this DAO
                    val featureRequests = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureRequestsForDao(daoUniqueId)
                    }

                    // Get all feature solutions for this DAO
                    val featureSolutions = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureSolutionsForDao(daoUniqueId)
                    }

                    // Find OPEN feature requests that have no solutions yet
                    val pendingRequests = featureRequests.filter { request ->
                        request.FEATURE_STATUS == "OPEN" &&
                            featureSolutions.none { it.FEATURE_REQUEST_ID == request.FEATURE_REQUEST_ID }
                    }

                    // TODO: filter on timestamp instead fo feature ID
                    val latestPendingRequest = pendingRequests.maxByOrNull { it.FEATURE_REQUEST_ID }


                    if (latestPendingRequest != null) {
                        Log.d("DaoDetailsFragment", "loadLatestPendingFeatureRequest: Latest pending request found: ${latestPendingRequest.FEATURE_REQUEST_ID} for DAO ${daoUniqueId}")

                        if (_binding == null) {
                            Log.w("DaoDetailsFragment", "loadLatestPendingFeatureRequest: View destroyed, skipping UI update.")
                            return@launch // Exit the coroutine if the view is gone
                        }
                        binding.latestFeatureRequestPreviewCard.visibility = View.VISIBLE
                        binding.tvNoPendingFeatureRequests.visibility = View.GONE

                        binding.tvLatestFeatureTitle.text = latestPendingRequest.FEATURE_TITLE
                        binding.tvLatestFeatureDescription.text = latestPendingRequest.FEATURE_DESCRIPTION
                        binding.tvLatestFeatureReward.text = "Reward: ${latestPendingRequest.FEATURE_REWARD} sats"

                        val solutionCount = withContext(Dispatchers.IO) {
                            p2playStore.getFeatureSolutionsForDao(daoUniqueId)
                                .count { it.FEATURE_REQUEST_ID == latestPendingRequest.FEATURE_REQUEST_ID }
                        }
                        binding.tvLatestFeatureSolutionCount.text = "$solutionCount solution(s)"


                        binding.latestFeatureRequestPreviewCard.setOnClickListener {
                            navigateToSubmitSolution(latestPendingRequest)
                        }
                        binding.latestFeatureRequestPreviewCard.isClickable = true
                        binding.latestFeatureRequestPreviewCard.alpha = 1.0f

                        return@launch // Found and updated, exit
                    } else {
                        Log.d("DaoDetailsFragment", "loadLatestPendingFeatureRequest (Attempt ${retry + 1}): No latest pending request found for DAO $daoUniqueId.")

                        if (_binding == null) {
                            Log.w("DaoDetailsFragment", "loadLatestPendingFeatureRequest: View destroyed, skipping UI update (no pending request).")
                            return@launch // Exit the coroutine if the view is gone
                        }
                        binding.latestFeatureRequestPreviewCard.visibility = View.GONE
                        binding.tvNoPendingFeatureRequests.visibility = View.VISIBLE
                        binding.latestFeatureRequestPreviewCard.setOnClickListener(null)
                        binding.latestFeatureRequestPreviewCard.isClickable = false
                        binding.latestFeatureRequestPreviewCard.alpha = 0.5f
                    }

                } catch (e: Exception) {
                    Log.e("DaoDetailsFragment", "loadLatestPendingFeatureRequest (Attempt ${retry + 1}): Error loading latest pending feature request: ${e.message}")

                    if (_binding == null) {
                        Log.w("DaoDetailsFragment", "loadLatestPendingFeatureRequest: View destroyed during error handling, skipping UI update.")
                        return@launch // Exit the coroutine if the view is gone
                    }
                    if (retry < maxRetries) {
                        Log.e("DaoDetailsFragment", "loadLatestPendingFeatureRequest: Retrying in ${retryDelayMillis}ms due to error...")
                        delay(retryDelayMillis)
                    } else {
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
     * Updates the download/open button based on the state of DAO and the app download
     */
    private fun updateDownloadButton() {
        if (_binding == null) {
            Log.w("AppDetails", "updateDownloadButton: Binding is null, skipping UI update.")
            return
        }

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

    private fun updateVotingCardUI(poll: VotingPoll) {
        if (_binding == null) {
            Log.w("AppDetails", "updateVotingCardUI: Binding is null, skipping UI update.")
            return
        }

        binding.updateTitle.text = poll.title
        binding.votesRequiredText.visibility = View.VISIBLE
        binding.totalVotes.visibility = View.VISIBLE

        binding.yesPercentage.text = "${poll.yesPercentage}%"
        binding.noPercentage.text = "${poll.noPercentage}%"
        binding.pendingPercentage.text = "${poll.pendingPercentage}%"

        binding.votesRequiredText.text = "${poll.yesVotes} of ${poll.votesNeeded} votes needed"
        binding.totalVotes.text = "${poll.totalVotesCast} of ${poll.totalMembers} members voted" // Use totalVotesCast

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

    private fun updateVotingState(poll: VotingPoll) {
        if (_binding == null) {
            Log.w("AppDetails", "updateVotingState: Binding is null, skipping UI update.")
            return
        }

        binding.btnVote.visibility = View.GONE
        binding.votingStatus.visibility = View.GONE

        // Fetch DAO data to check if user is a member and initiator
        lifecycleScope.launch {
            try {
                val daoBlock = withContext(Dispatchers.IO) {
                    p2playStore.fetchLatestSharedWalletBlockByDaoId(poll.daoId)
                }

                if (_binding == null) {
                    Log.w("AppDetails", "updateVotingState: View destroyed in coroutine, skipping UI update.")
                    return@launch // Exit the coroutine if the view is gone
                }

                val isUserMember = if (daoBlock != null) {
                    // Use the correct transaction data based on block type
                    when(daoBlock.type) {
                        P2pStoreCommunity.JOIN_BLOCK -> JoinDaoTransactionData(daoBlock.transaction).getData().SW_TRUSTCHAIN_PKS.contains(p2playStore.myPeer.publicKey.keyToBin().toHex())
                        P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(daoBlock.transaction).getData().SW_TRUSTCHAIN_PKS.contains(p2playStore.myPeer.publicKey.keyToBin().toHex())
                        else -> false
                    }
                } else {
                    false
                }

                if (_binding != null) {
                    when {
                        // Voting closed and approved (enough YES votes)
                        !poll.isActive && poll.yesVotes >= poll.votesNeeded -> {
                            binding.votingStatus.text = "Approved"
                            binding.votingStatus.setTextColor(
                                resources.getColor(android.R.color.holo_green_dark, null)
                            )
                            binding.votingStatus.visibility = View.VISIBLE
                            binding.votingCard.isClickable = false
                            binding.votingCard.alpha = 0.5f
                        }
                        // Voting closed and not approved
                        !poll.isActive -> {
                            binding.votingStatus.text = "Voting Closed"
                            binding.votingStatus.setTextColor(
                                resources.getColor(android.R.color.darker_gray, null)
                            )
                            binding.votingStatus.visibility = View.VISIBLE
                            binding.votingCard.isClickable = false
                            binding.votingCard.alpha = 0.5f
                        }
                        poll.hasUserVoted -> { // Voting active, user has voted
                            binding.btnVote.isEnabled = false
                            binding.votingCard.alpha = 1.0f
                        }
                        isUserMember -> { // Voting active, user is member, user has NOT voted
                            binding.btnVote.visibility = View.VISIBLE
                            binding.btnVote.isEnabled = true
                            binding.votingCard.alpha = 1.0f

                        }
                        else -> {
                            // User is not a member, voting is active, they cannot vote.
                            // Buttons remain hidden by default.
                            binding.votingCard.alpha = 0.5f
                        }
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "Error updating voting state: ${e.message}")
                if (_binding != null) {
                    binding.votesRequiredText.visibility = View.VISIBLE
                }
            }
        }
    }


    /**
     * Sets up all the click event handlers for buttons on the page
     */
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

//        // Navigate to DAO Wallet fragment
//        binding.daoWalletInfoLayout.setOnClickListener {
//            Log.d("AppDetails", "Navigating to DAO Wallet Fragment for DAO ${app.daoId}")
//            val bundle = Bundle().apply {
//                putString("daoUniqueId", app.daoId)
//            }
//            findNavController().navigate(R.id.action_appDetailsFragment_to_daoWalletFragment, bundle)
//        }


        binding.btnFeatureRequest.setOnClickListener {
            if (this.app.isDaoMember()) {
                val bundle = Bundle().apply {
                    putString("blockId", daoBlock.blockId)
                    putString("daoUniqueId", app.daoId)
                }
                findNavController()
                    .navigate(R.id.action_appDetailsFragment_to_featureListFragment, bundle)
            }
        }

        binding.btnSeeAllVotes.setOnClickListener {
            val bundle = Bundle().apply {
                putString("blockId", daoBlock.blockId)
                putString("daoUniqueId", app.daoId)
            }
            findNavController()
                .navigate(R.id.action_appDetailsFragment_to_allVotingPollsFragment, bundle)
        }

        binding.btnSeeAllFeatures.setOnClickListener {
            val bundle = Bundle().apply {
                putString("blockId", daoBlock.blockId)
                putString("daoUniqueId", app.daoId)
            }
            findNavController()
                .navigate(R.id.action_appDetailsFragment_to_featureListFragment, bundle)
        }
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
                return
            }

        val context = requireContext()
        var signatures: List<VoteYesData>? = null
        while (signatures == null) {
            delay(10_000)
            if (_binding == null) {
                Log.w("AppDetails", "joinSharedWalletClicked: View destroyed while waiting for signatures.")
                return
            }
            signatures = collectJoinWalletResponses(proposeBlockData)
        }

        if (_binding == null) {
            Log.w("AppDetails", "joinSharedWalletClicked: View destroyed after collecting signatures.")
            return
        }

        try {
            getP2pStoreCommunity().joinBitcoinWallet(
                mostRecentSWBlock.transaction,
                proposeBlockData,
                signatures,
                context
            )
            WalletManagerAndroid.getInstance()
                .addNewNonceKey(proposeBlockData.DAO_ID, context)
        } catch (t: Throwable) {
            Log.e("Coin", "Joining failed. ${t.message ?: "No further information"}.")
        }
    }

    /**
     * Collect the signatures of a join proposal
     */
    private suspend fun collectJoinWalletResponses(blockData: JoinRequestData): List<VoteYesData>? {
        val responses =
            getP2pStoreCommunity().fetchProposalResponses(
                blockData.DAO_ID,
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

    private fun navigateToVotingFragment(daoBlockId: String, proposalId: String, daoUniqueId: String) {
        val bundle = Bundle().apply {
            putString("blockId", daoBlockId)
            putString("solutionId", proposalId)
            putString("daoUniqueId", daoUniqueId)
        }
        findNavController()
            .navigate(R.id.action_appDetailsFragment_to_featureVotingFragment, bundle)
    }

    private fun navigateToSubmitSolution(featureRequest: FeatureRequestData) {
        val bundle = Bundle().apply {
            putString("featureId", featureRequest.FEATURE_REQUEST_ID)
            putString("daoUniqueId", featureRequest.DAO_ID) // Keep DAO_ID for context
            putString("featureTitle", featureRequest.FEATURE_TITLE)
            putString("featureDescription", featureRequest.FEATURE_DESCRIPTION)
        }

        findNavController().navigate(R.id.action_appDetailsFragment_to_featureSolutionFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
