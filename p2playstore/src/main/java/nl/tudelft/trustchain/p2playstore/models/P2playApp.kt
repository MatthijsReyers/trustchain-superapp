package nl.tudelft.trustchain.p2playstore.models

import android.util.Log
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTransactionData
import nl.tudelft.trustchain.p2playstore.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.sharedWallet.SWSignatureAskTransactionData
import nl.tudelft.trustchain.p2playstore.utils.iconFromIconId

class P2playApp(private val block: TrustChainBlock) {
    private val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!

    private val daoData = SWJoinBlockTransactionData(block.transaction).getData()

    /**
     * Unique identifier for the DAO that belongs to this app, this ID should remain consistent
     * across different versions/updates of the app.
     */
    val daoId = daoData.SW_UNIQUE_ID

    fun getName(): String? {
        return block.transaction["name"] as? String
    }

    fun getDescription(): String {
        return (block.transaction["description"] as? String) ?: ""
    }

    fun getCategory(): String? {
        return block.transaction["category"] as? String
    }

    fun getIcon(): Int {
        return iconFromIconId(block.transaction["iconIndex"])
    }

    fun getVersion(): Int {
        return block.hashNumber
    }


    /**
     * Returns the amount of members the DAO for this app has.
     */
    fun getDoaMemberCount(): Int {
        return daoData.SW_TRUSTCHAIN_PKS.size
    }



    /**
     * Returns a list of all requests to join this app's DOA, including ones for which voting has
     * already finished.
     */
    fun getDaoJoinRequests(): List<DaoJoinRequest> {
        val blocks = trustChain.database.getBlocksWithType(P2pStoreCommunity.SIGNATURE_ASK_BLOCK)
        return blocks
            .filter { b ->
                val data = SWSignatureAskTransactionData(b.transaction).getData()
                data.SW_UNIQUE_ID == this.daoId
            }
            .map { b -> DaoJoinRequest(b) }
    }

    /**
     * Returns a list of all feature requests for this app, including the ones for which an
     * update/implementation has already been proposed and accepted.
     */
    fun getFeatureRequests(): List<TrustChainBlock> {
        val blocks = trustChain.database.getBlocksWithType(P2pStoreCommunity.FEATURE_REQUEST_BLOCK)
        return blocks.filter { block ->
            try {
                val data = FeatureRequestTransactionData(block.transaction).getData()
                data.daoId == this.daoId
            } catch (e: Exception) {
                Log.e(
                    "P2PlayStore.AppHelper",
                    "Failed to parse FeatureRequest block: ${e.message}"
                )
                false
            }
        }
    }

    /**
     * Checks if this user/ipv8 peer is a member of this app's DOA. Note that this is computed based
     * on the block that was given in the constructor, if a newer version of the app is available in
     * which the user became a member than this result will be outdated.
     */
    fun isDaoMember(): Boolean {
        val myPublicKey = trustChain.myPeer.publicKey.keyToBin().toHex()
        return daoData.SW_TRUSTCHAIN_PKS.contains(myPublicKey)
    }

    /**
     * Checks if this user/ipv8 peer is waiting for other users to be voted into the DOA
     */
    fun isWaitingToJoin(): Boolean {
        if (this.isDaoMember()) {
            return false
        }
        val joinRequests = this.getDaoJoinRequests()
        val myPublicKey = trustChain.myPeer.publicKey.keyToBin().toHex()
        for (request in joinRequests) {
            if (request.requestingUser == myPublicKey) {
                if (request.isPending()) {
                    return true
                }
            }
        }
        return false
    }
}
