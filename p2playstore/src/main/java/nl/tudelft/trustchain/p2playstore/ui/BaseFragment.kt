package nl.tudelft.trustchain.p2playstore.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.trustchain.common.util.TrustChainHelper
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.FEATURE_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_NO_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_YES_BLOCK

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

    override fun onDestroy() {
        super.onDestroy()
        this.cleanupChainListeners()
    }

    private val listener: BlockListener = object: BlockListener {
        override fun onBlockReceived(block: TrustChainBlock) {
            Log.d("P2PlayStore", "Chain update ${block.type}")
            lifecycleScope.launch(Dispatchers.Main) {
                delay(500)
                onChainUpdated(block)
            }
        }
    }

    fun printToast(msg: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
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
        val trustChain = getTrustChainCommunity()
        trustChain.addListener(JOIN_BLOCK, listener);
        trustChain.addListener(JOIN_REQUEST_BLOCK, listener);
        trustChain.addListener(VOTE_YES_BLOCK, listener);
        trustChain.addListener(VOTE_NO_BLOCK, listener);
        trustChain.addListener(PROPOSE_UPDATE_BLOCK, listener);
        trustChain.addListener(UPDATE_ACCEPTED_BLOCK, listener);
        trustChain.addListener(FEATURE_REQUEST_BLOCK, listener);
    }

    /**
     * This function removes the event listeners on the trust chain, because the references will
     * otherwise keep the classes "in-use", causing a memmory leak.
     */
    private fun cleanupChainListeners() {
        getTrustChainCommunity().removeListener(listener, JOIN_BLOCK)
        getTrustChainCommunity().removeListener(listener, VOTE_YES_BLOCK)
        getTrustChainCommunity().removeListener(listener, VOTE_NO_BLOCK)
        getTrustChainCommunity().removeListener(listener, UPDATE_ACCEPTED_BLOCK)
        getTrustChainCommunity().removeListener(listener, PROPOSE_UPDATE_BLOCK)
        getTrustChainCommunity().removeListener(listener, JOIN_REQUEST_BLOCK)
        getTrustChainCommunity().removeListener(listener, FEATURE_REQUEST_BLOCK)
    }
}

