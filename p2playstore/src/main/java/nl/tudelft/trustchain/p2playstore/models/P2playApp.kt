package nl.tudelft.trustchain.p2playstore.models

import android.util.Log
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.FEATURE_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.AppMetaData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDoaData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData
import nl.tudelft.trustchain.p2playstore.utils.MagnetLink
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils
import nl.tudelft.trustchain.p2playstore.utils.iconFromIconId

class P2playApp(val block: TrustChainBlock) {
    private val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!

    val daoData: AppMetaData = when(block.type) {
        JOIN_BLOCK -> JoinDaoTransactionData(block.transaction).getData()
        UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(block.transaction).getData()
        PROPOSE_UPDATE_BLOCK -> ProposeUpdateTransactionData(block.transaction).getData()
        else -> throw Exception("P2playApp received wrong block type: $block.type")
    }

    /**
     * Unique identifier for the DAO that belongs to this app, this ID remains the same across all
     * different versions/updates of the app.
     */
    val daoId = daoData.DAO_ID

    val name: String get() = this.daoData.APP_NAME
    val description: String get() = this.daoData.APP_DESCRIPTION
    val category: String get() = this.daoData.APP_CATEGORY
    val icon: Int get() = iconFromIconId(this.daoData.APP_ICON)
    val magnetLink: MagnetLink = MagnetUtils.parseMagnet(this.daoData.APP_MAGNET_LINK)

    /**
     * Unique number to identify a specific update/version of the app, note that these numbers are
     * not incremental or anything so bigger does not mean newer.
     */
    val version: Int get() = this.block.hashNumber

    private fun getSharedWalletPublicKeys(): ArrayList<String> {
        if (block.type == JOIN_BLOCK) {
            return (daoData as JoinDoaData).SW_TRUSTCHAIN_PKS
        }
        if (block.type == UPDATE_ACCEPTED_BLOCK) {
            return (daoData as JoinDoaData).SW_TRUSTCHAIN_PKS
        }
        val data = JoinDaoTransactionData(this.getLatestJoin().transaction).getData()
        return data.SW_TRUSTCHAIN_PKS
    }

    fun getDoaVoteThreshold(): Int {
        if (block.type == JOIN_BLOCK) {
            return (daoData as JoinDoaData).SW_VOTING_THRESHOLD
        }
        val data = JoinDaoTransactionData(this.getLatestJoin().transaction).getData()
        return data.SW_VOTING_THRESHOLD
    }

    /**
     * Returns the amount of members the DAO for this app has.
     */
    fun getDoaMemberCount(): Int {
        return this.getSharedWalletPublicKeys().size
    }

    /**
     * Returns the amount of members the DAO for this app has.
     */
    fun getEntranceFee(): Long {
        if (block.type == JOIN_BLOCK) {
            return (daoData as JoinDoaData).SW_ENTRANCE_FEE
        }
        val data = JoinDaoTransactionData(this.getLatestJoin().transaction).getData()
        return data.SW_ENTRANCE_FEE
    }

    /**
     * Gets the latest join block, this block should contain the latest update when it comes to the
     * state of the shared bitcoin wallet.
     */
    private fun getLatestJoin(): TrustChainBlock {
        return trustChain.database.getBlocksWithType(JOIN_BLOCK)
            .filter { b ->
                val data = JoinRequestTransactionData(b.transaction).getData()
                data.DAO_ID == this.daoId
            }
            .maxByOrNull { b -> b.insertTime!! }!!
    }

    /**
     * Look on the trust chain if there exists a newer version/update for this app.
     */
    fun getLatestVersion(): P2playApp {
        val joinBlocks = trustChain.database.getBlocksWithType(JOIN_BLOCK)
        val updateBlocks = trustChain.database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)
        val latest = (joinBlocks + updateBlocks)
            .filter { b ->
                val data = JoinRequestTransactionData(b.transaction).getData()
                data.DAO_ID == this.daoId
            }
            .maxByOrNull { b -> b.insertTime!! }!!
        return P2playApp(latest!!)
    }

    /**
     * Returns a list of all requests to join this app's DOA, including ones for which voting has
     * already finished.
     */
    fun getDaoJoinRequests(): List<DaoJoinRequest> {
        val blocks = trustChain.database.getBlocksWithType(JOIN_REQUEST_BLOCK)
        return blocks
            .filter { b ->
                val data = JoinRequestTransactionData(b.transaction).getData()
                data.DAO_ID == this.daoId
            }
            .map { b -> DaoJoinRequest(b) }
    }

    /**
     * Returns a list of all feature requests for this app, including the ones for which an
     * update/implementation has already been proposed and accepted.
     */
    fun getFeatureRequests(): List<TrustChainBlock> {
        val blocks = trustChain.database.getBlocksWithType(FEATURE_REQUEST_BLOCK)
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
        return this.getSharedWalletPublicKeys().contains(myPublicKey)
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
