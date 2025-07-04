package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.currencyii.util.taproot.CTransaction
import nl.tudelft.trustchain.p2playstore.JOIN_BLOCK
import nl.tudelft.trustchain.p2playstore.utils.AppUtils.formatDynamicBalance
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.databinding.FragmentWalletBalanceBinding
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData
import org.bitcoinj.core.Coin
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params

class DaoWalletFragment : BaseFragment() {

    private var _binding: FragmentWalletBalanceBinding? = null
    private val binding get() = _binding!!

    private lateinit var transactionHistoryAdapter: TransactionHistoryAdapter
    private lateinit var daoUniqueId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletBalanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the DAO unique ID from arguments
        daoUniqueId = arguments?.getString("daoId")!!

        if (daoUniqueId.isEmpty()) {
            Log.e("DaoWalletFragment", "DAO Unique ID not provided.")
            // Display an error or navigate back
            binding.balanceRefreshLayout.isRefreshing = false
            updateUIWithError("Error: Missing DAO ID.")
            return
        }

        // Update UI titles to be DAO-specific
        binding.totalBalance.text = getString(R.string.loading)
        binding.daoWalletCount.text = getString(R.string.loading)

        setupRecyclerView()
        loadWalletData()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        transactionHistoryAdapter = TransactionHistoryAdapter(emptyList())
        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionHistoryAdapter
        }
    }

    private fun setupClickListeners() {
        binding.balanceRefreshLayout.setOnRefreshListener {
            loadWalletData()
        }
    }

    private fun loadWalletData() {
        if (!WalletManagerAndroid.isInitialized()) {
            Log.w("DaoWalletFragment", "WalletManager is not initialized.")
            binding.balanceRefreshLayout.isRefreshing = false

            updateUIWithError("Wallet not initialized.")
            return
        }

        binding.balanceRefreshLayout.isRefreshing = true
        Log.d("DaoWalletFragment", "Loading wallet data for DAO: $daoUniqueId")

        lifecycleScope.launch {
            try {
                val walletManager = WalletManagerAndroid.getInstance()

                // Get Personal Wallet Balance
                val personalBalance = withContext(Dispatchers.IO) {
                    walletManager.kit.wallet().balance
                }
                Log.d("DaoWalletFragment", "Personal balance: $personalBalance")

                // Get THIS DAO's Shared Wallet Balance
                var daoBalance = Coin.ZERO
                var daoMemberCount = 0

                val latestDaoBlock = withContext(Dispatchers.IO) {
                    val latestApp = P2playApp.findByDoaId(daoUniqueId)
                    latestApp?.block
                }

                if (latestDaoBlock != null) {
                    val (serializedTx, trustChainPks) = when(latestDaoBlock.type) {
                        JOIN_BLOCK -> {
                            val data = JoinDaoTransactionData(latestDaoBlock.transaction).getData()
                            Pair(data.SW_TRANSACTION_SERIALIZED, data.SW_TRUSTCHAIN_PKS)
                        }
                        UPDATE_ACCEPTED_BLOCK -> {
                            val data = UpdateAcceptedTransactionData(latestDaoBlock.transaction).getData()
                            Pair(data.SW_TRANSACTION_SERIALIZED, data.SW_TRUSTCHAIN_PKS)
                        }
                        else -> Pair(null, null)
                    }

                    if (serializedTx != null) {
                        val cTx = CTransaction().deserialize(serializedTx.hexToBytes())
                        // Find the output corresponding to the shared wallet's multisig script (size 35)
                        val sharedWalletOutput = cTx.vout.find { it.scriptPubKey.size == 35 }
                        if (sharedWalletOutput != null) {
                            daoBalance = Coin.valueOf(sharedWalletOutput.nValue)
                            Log.d("DaoWalletFragment", "DAO $daoUniqueId balance: $daoBalance")
                        } else {
                            Log.w("DaoWalletFragment", "No shared wallet output found in latest block for DAO $daoUniqueId")
                        }
                    } else {
                        Log.w("DaoWalletFragment", "SW_TRANSACTION_SERIALIZED is null in latest block for DAO $daoUniqueId")
                    }

                    daoMemberCount = trustChainPks?.size ?: 0
                    Log.d("DaoWalletFragment", "DAO $daoUniqueId member count: $daoMemberCount")

                } else {
                    Log.w("DaoWalletFragment", "Could not find latest block for DAO $daoUniqueId. Assuming balance is 0.")
                    // If no block found, the DAO might not exist or user is not a member.
                    updateUIWithError("Could not load DAO wallet state.")
                    binding.balanceRefreshLayout.isRefreshing = false
                }

                // Load Transaction History relevant to DAO
                val transactionBlocks = withContext(Dispatchers.IO) {
                    val trustchain = getTrustChainCommunity()

                    // Fetch blocks relevant to this specific DAO's fund transfers/joins
                    val joinBlocks = trustchain.database.getBlocksWithType(JOIN_BLOCK)
                    val updateBlocks = trustchain.database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)

                    // Filter blocks by this specific DAO_ID
                    (joinBlocks + updateBlocks).filter { block ->
                        try {
                            val daoIdFromBlock = when(block.type) {
                                JOIN_BLOCK -> JoinDaoTransactionData(block.transaction).getData().DAO_ID
                                UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(block.transaction).getData().DAO_ID
                                else -> null // Should not happen
                            }
                            daoIdFromBlock == daoUniqueId
                        } catch (e: Exception) {
                            Log.e(
                                "DaoWalletFragment",
                                "Error filtering transaction blocks for DAO $daoUniqueId: ${e.message}"
                            )
                            false // Exclude blocks that fail parsing
                        }
                    }.sortedByDescending { it.timestamp.time } // Sort by time descending
                }
                Log.d("DaoWalletFragment", "Found ${transactionBlocks.size} relevant transaction blocks for DAO $daoUniqueId")

                // 4. Update UI
                updateUI(personalBalance, daoBalance, daoMemberCount, transactionBlocks)

            } catch (e: Exception) {
                Log.e("DaoWalletFragment", "Error loading wallet data for DAO $daoUniqueId: ${e.message}")
                updateUIWithError("Error loading wallet data: ${e.message}")
            } finally {
                binding.balanceRefreshLayout.isRefreshing = false
            }
        }
    }


    private fun updateUI(
        personalBalance: Coin,
        daoBalance: Coin,
        daoMemberCount: Int,
        transactionBlocks: List<TrustChainBlock>
    ) {
        // Update DAO Wallet specific UI
        binding.totalBalance.text = formatDynamicBalance(daoBalance) // Show THIS DAO's balance as the main balance
        binding.daoWalletBalance.text = formatDynamicBalance(daoBalance)
        binding.daoWalletCount.text = "$daoMemberCount ${if (daoMemberCount == 1) "member" else "members"} in DAO" // Use member count here

        // Update Personal Wallet UI
        binding.personalWalletBalance.text = formatDynamicBalance(personalBalance)

        val networkType = when (WalletManagerAndroid.getInstance().params) {
            RegTestParams.get() -> "RegTest Network"
            TestNet3Params.get() -> "TestNet Network"
            MainNetParams.get() -> "Production Network"
            else -> "Unknown Network"
        }
        binding.bitcoinNetworkType.text = networkType

        val walletManager = WalletManagerAndroid.getInstance()
        val peerGroup = walletManager.kit.peerGroup()
        val connectedPeers = peerGroup.numConnectedPeers()
        val isConnected = connectedPeers > 0
        if (isConnected){
            binding.bitcoinNetworkStatus.text = "Connected"
        } else {
            binding.bitcoinNetworkStatus.text = "Disconnected"
        }

        // Update Transaction History
        if (transactionBlocks.isNotEmpty()) {
            binding.emptyTransactionsView.visibility = View.GONE
            binding.transactionsRecyclerView.visibility = View.VISIBLE
            transactionHistoryAdapter.updateBlocks(transactionBlocks)
        } else {
            binding.emptyTransactionsView.visibility = View.VISIBLE
            binding.transactionsRecyclerView.visibility = View.GONE
        }

    }

    private fun updateUIWithError(message: String) {
        binding.totalBalance.text = "Error"
        binding.personalWalletBalance.text = "Error"
        binding.daoWalletBalance.text = "Error"
        binding.daoWalletCount.text = "Error"
        binding.bitcoinNetworkType.text = "Error"
        binding.bitcoinNetworkStatus.text = "Error"


        binding.emptyTransactionsView.visibility = View.VISIBLE
        binding.transactionsRecyclerView.visibility = View.GONE
        // Find the TextView within the empty view layout
        val emptyTextView = binding.emptyTransactionsView.findViewById<android.widget.TextView>(R.id.empty_transactions_view)
        emptyTextView?.text = message // Display the specific error message
        // Hide icon and other views in the empty state if they exist in the layout
        val emptyIcon = binding.emptyTransactionsView.findViewById<android.widget.ImageView>(R.id.empty_transactions_view)
        emptyIcon?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
