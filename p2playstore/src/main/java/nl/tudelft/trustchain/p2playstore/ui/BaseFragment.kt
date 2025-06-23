package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.util.Log
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.trustchain.common.DemoCommunity
import nl.tudelft.trustchain.common.MarketCommunity
import nl.tudelft.trustchain.common.util.TrustChainHelper
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity

abstract class BaseFragment(
    @LayoutRes contentLayoutId: Int = 0
) : Fragment(contentLayoutId) {
    protected fun getP2pStoreCommunity(): P2pStoreCommunity {
        return getIpv8().getOverlay()
            ?: throw IllegalStateException("P2pStoreCommunity is not configured")
    }

    protected val p2playStore: P2pStoreCommunity by lazy {
        getP2pStoreCommunity()
    }

    protected fun getIpv8(): IPv8 {
        return IPv8Android.getInstance()
    }

    protected fun getTrustChainCommunity(): TrustChainCommunity {
        return getIpv8().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    protected val trustchain: TrustChainHelper by lazy {
        TrustChainHelper(getTrustChainCommunity())
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        this.setupChainListeners()
    }

    /**
     * Called whenever new blocks show up on the trustchain, child classes override this function
     * to update their UI's
     */
    open suspend fun onChainUpdated(block: TrustChainBlock) {}

    /**
     * This function attaches a bunch of event listeners to the trustchain so we can detect new
     * blocks when they are created and update the UI accordingly
     */
    private fun setupChainListeners() {
        val listener: BlockListener = object: BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                Log.d("P2pStore", "Chain update ${block.type}")
                lifecycleScope.launch(Dispatchers.Main) {
                    delay(500)
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
}

