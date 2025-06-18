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
import kotlinx.coroutines.launch

import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.databinding.FragmentAppDetailsBinding
import nl.tudelft.trustchain.p2playstore.ExecutionActivity
import nl.tudelft.trustchain.p2playstore.P2PlayStoreMainActivity
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.TorrentManager
import nl.tudelft.trustchain.p2playstore.databinding.PollPreviewBinding
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestData
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.models.Poll
import nl.tudelft.trustchain.p2playstore.transactionData.FeatureRequestData
import nl.tudelft.trustchain.p2playstore.utils.AppUtils
import nl.tudelft.trustchain.p2playstore.utils.AppUtils.printToast

import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

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
            this.updatePolls()
            lifecycleScope.launch {
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
    private fun onChainUpdated(block: TrustChainBlock) {
        Log.d("P2pStore", "Chain update ${block.type}")

        when (block.type) {
            // Was a new version of the app released?
            P2pStoreCommunity.JOIN_BLOCK, P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> {
                this.daoBlock = block
                this.app = P2playApp(this.daoBlock)
                requireActivity().runOnUiThread {
                    updateAppMetaData()
                    updateDownloadButton()
                    updateUIBasedOnMembership()
                }
            }
            // ALl the other possible blocks are essentially just updates for various polls,
            else -> {
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
        if (this.app.isDaoMember()) {
            AppUtils.printToast(requireContext(), "You are already a member of this DAO.")
            Log.d("AppDetails", "User attempted to join DAO ${app.daoId} but is already a member.")
            updateDownloadButton()
            updateUIBasedOnMembership()
            return
        }

        if (this.app.isWaitingToJoin()) {
            AppUtils.printToast(requireContext(), "You have a pending request to join this DAO.")
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

                val mostRecentSWBlock =
                    getP2pStoreCommunity().fetchLatestSharedWalletBlock(daoBlock.calculateHash())
                        ?: daoBlock
                try {
                    getP2pStoreCommunity().proposeJoinWallet(mostRecentSWBlock).getData()
                } catch (t: Throwable) {
                    Log.e("P2P", "Join wallet proposal failed. ${t.message ?: "No further information"}.")
                }

                loadLatestPendingFeatureRequest()
                updateDownloadButton()
            }
            requireActivity().runOnUiThread {
                updatePolls()
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
     * Updates the list of polls/proposals
     */
    private fun updatePolls() {
        val joinPolls = this.app.getOpenDaoJoinPolls();
        if (joinPolls.isNotEmpty()) {
            binding.joinProposalContainer.visibility = View.VISIBLE
            val view = binding.joinProposal
            val peer = joinPolls[0].requestingUser.substring(0, 6)
            view.pollDescription.text = "Should peer $peer be allowed to join the app DAO?"
            view.pollTitle.text = "DAO join request"
            this.updatePollView(view, joinPolls[0])
        }
        else {
            binding.joinProposalContainer.visibility = View.GONE
        }

       val updatePolls = this.app.getOpenUpdatePolls();
       Log.d("P2PlayStore", "updatePolls: $updatePolls")
       if (updatePolls.isNotEmpty()) {
           binding.updateProposalContainer.visibility = View.VISIBLE
           val view = binding.updateProposal
           view.pollDescription.text = updatePolls[0].description
           view.pollTitle.text = "Release update"
           this.updatePollView(view, updatePolls[0])
       }
       else {
           binding.updateProposalContainer.visibility = View.GONE
       }

        // Show/hide the "no active proposals text"
        binding.noProposalsText.visibility =
            if (joinPolls.isEmpty() && updatePolls.isEmpty()) { View.VISIBLE } else { View.GONE }
    }

    private fun updatePollView(view: PollPreviewBinding, poll: Poll) {
        view.progressBar.post {
            // Clamp value at 1, because 0 maps to 100% for some reason...
            view.yesProgressBar.layoutParams.width =
                max(1, (view.progressBar.width * poll.yesPercentage).roundToInt())
            view.noProgressBar.layoutParams.width =
                max(1, (view.progressBar.width * poll.noPercentage).roundToInt())
            view.yesProgressBar.requestLayout()
            view.noProgressBar.requestLayout()
        }

        val totalMembers = this.app.getDoaMemberCount()
        view.votingProgress.text = "${poll.votes} of $totalMembers members voted"
    }

    private fun loadLatestPendingFeatureRequest() {

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
            getP2pStoreCommunity().joinBitcoinWallet(
                daoBlock.transaction,
                myPoll.daoData,
                signatures,
                requireContext()
            )
            // Add new nonceKey after joining a DAO
            WalletManagerAndroid.getInstance().addNewNonceKey(
                this.app.daoId,
                requireContext()
            )
        }
        catch (t: Throwable) {
            Log.e("Coin", "Joining failed. ${t.message ?: "No further information"}.")
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

        // btnSeeAllVotes click listener (Existing) - Navigate to AllVotingPollsFragment
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
        // The click listener for latest_feature_request_preview_card is set dynamically in
        // loadLatestPendingFeatureRequest

        binding.joinProposalContainer.setOnClickListener {
            Log.d("P2PlayStore", "Navigating to join poll")
            val joinPolls = this.app.getOpenDaoJoinPolls();
            if (joinPolls.isNotEmpty()) {
                navigateToVotingFragment(
                    this.app.block.blockId,
                    joinPolls[0].proposalId,
                    joinPolls[0].daoId,
                )
            }
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
                    lifecycleScope.launch(Dispatchers.Main) {
                        onChainUpdated(block)
                    }
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
