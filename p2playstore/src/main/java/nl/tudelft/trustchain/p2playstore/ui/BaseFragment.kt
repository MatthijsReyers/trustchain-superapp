package nl.tudelft.trustchain.p2playstore.ui

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.trustchain.common.DemoCommunity
import nl.tudelft.trustchain.common.MarketCommunity
import nl.tudelft.trustchain.common.util.TrustChainHelper
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

    protected fun getDemoCommunity(): DemoCommunity {
        return getIpv8().getOverlay()
            ?: throw IllegalStateException("DemoCommunity is not configured")
    }

    protected fun getMarketCommunity(): MarketCommunity {
        return getIpv8().getOverlay()
            ?: throw java.lang.IllegalStateException("MarketCommunity is not configured")
    }

}

