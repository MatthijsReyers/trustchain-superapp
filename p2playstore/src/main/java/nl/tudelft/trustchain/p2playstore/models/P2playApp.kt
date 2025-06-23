package nl.tudelft.trustchain.p2playstore.models

import UpdateProposalPoll
import android.util.Log
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.FEATURE_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.transactionData.FeatureRequestTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.AppMetaData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDoaData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData
import nl.tudelft.trustchain.p2playstore.utils.MagnetLink
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils
import nl.tudelft.trustchain.p2playstore.utils.iconFromIconId

class P2playApp(val block: TrustChainBlock) {
    private val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!

    private val daoData: AppMetaData = when(block.type) {
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
    val magnetLink: MagnetLink = MagnetUtils.parseMagnet(this.daoData.APP_MAGNET_LINK)
    val icon: Int get() = iconFromIconId(
        try {
            this.block.transaction["iconIndex"]
        } catch (e: Exception) {
            Log.w("P2playApp", "Could not get iconIndex from block transaction: ${e.message}")
            3 // Default icon index
        }
    )

    /**
     * Unique number to identify a specific update/version of the app, note that these numbers are
     * not incremental or anything so bigger does not mean newer.
     */
    val version: Int get() = this.block.hashNumber

    private fun getSharedWalletPublicKeys(): ArrayList<String> {
        return when (daoData) {
            is JoinDoaData -> daoData.SW_TRUSTCHAIN_PKS
            is UpdateAcceptedData -> daoData.SW_TRUSTCHAIN_PKS
            else -> arrayListOf()
        }
    }

    fun getDoaVoteThreshold(): Int {
        try {
            if (block.type == JOIN_BLOCK) {
                return (daoData as JoinDoaData).SW_VOTING_THRESHOLD
            } else if (block.type == PROPOSE_UPDATE_BLOCK
                && (daoData as ProposeUpdateData).FEATURE_REQUEST_ID != null) {
                // Feature Solution proposals also contain voting threshold
    //            TODO:fix this to precentage instead of int signatures
                return daoData.SW_SIGNATURES_REQUIRED
            }
            val data = JoinDaoTransactionData(this.getLatestJoin().transaction).getData()
            return data.SW_VOTING_THRESHOLD
        }
        catch (err: Throwable) {
            Log.d("P2PlayStore", "Failed to get dao vote threshold")
            return 0
        }
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
        try {
            if (block.type == JOIN_BLOCK) {
                return (daoData as JoinDoaData).SW_ENTRANCE_FEE
            }
            val data = JoinDaoTransactionData(this.getLatestJoin().transaction).getData()
            return data.SW_ENTRANCE_FEE
        }
        catch (err: Throwable) {
            Log.d("P2PlayStore", "Failed to load entrance fee")
            return 0
        }
    }

    /**
     * Gets the latest join block, this block should contain the latest update when it comes to the
     * state of the shared bitcoin wallet.
     */
    fun getLatestJoin(): TrustChainBlock {
        return trustChain.database.getBlocksWithType(JOIN_BLOCK)
            .filter { b ->
                val data = JoinRequestTransactionData(b.transaction).getData()
                data.DAO_ID == this.daoId
            }
            .maxByOrNull { b -> b.insertTime!! }!!
    }

    /**
     * Look on the trust chain if there exists a newer version/accepted update for this app.
     *
     * Note that this means that if you have created an instance of this model with a
     * `PROPOSE_UPDATE` block which has not been accepted yet this method might actually return an
     * older version.
     */
    fun getLatestVersion(): P2playApp {
        val joinBlocks = trustChain.database.getBlocksWithType(JOIN_BLOCK)
        val updateBlocks = trustChain.database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)
        val latest = (joinBlocks + updateBlocks)
            .filter { b ->
                val data = JoinRequestTransactionData(b.transaction).getData()
                data.DAO_ID == this.daoId
            }
            .maxByOrNull { b -> b.insertTime!! }
        return P2playApp(latest!!)
    }

    /**
     * This helper method helps with finding polls and does all of the work necessary to return
     * for each poll only the blocks for which the current user is the receiver.
     * Unless no such block exists (for example, if the user is not a member of the DAO) in which
     * case any random proposal block will be used for the poll model since the user cannot vote
     * in that poll then anyway.
     */
    private fun <P : Poll> getPolls(
        blockType: String,
        pollFactory: (TrustChainBlock) -> P?
    ): List<P> {
        // Note that for a single proposal multiple proposal blocks are created for every DAO
        // member which need to be filtered in order not to return duplicate proposals
        val allPolls = trustChain.database.getBlocksWithType(blockType)
            .mapNotNull { b -> try { pollFactory(b) } catch (err: Throwable) { null } }
            .filter { poll -> poll.daoId == this.daoId }
        val polls = allPolls.distinctBy { poll -> poll.proposalId }

        // Get all proposal blocks addressed to me
        val myKey = trustChain.myPeer.publicKey.keyToBin().toHex()
        val myPolls = allPolls.filter { poll -> poll.receivingUser == myKey }

        // Get all proposals created before I was a member of the DAO
        val otherPolls = polls.filter {
                poll -> myPolls.none { p -> p.proposalId == poll.proposalId }
        }

        return (myPolls + otherPolls)
            .sortedBy { poll -> poll.block.insertTime!! }
    }

    /**
     * Returns a list of all join DAO requests for this app, including the ones for which voting has
     * already concluded.
     */
    fun getAllDaoJoinPolls(): List<DaoJoinPoll> {
        return this.getPolls(JOIN_REQUEST_BLOCK, ::DaoJoinPoll)
    }

    /**
     * Returns a list of all open join DAO requests for this app (i.e. all the requests which have
     * not been finished yet.
     */
    fun getOpenDaoJoinPolls(): List<DaoJoinPoll> {
        return this.getAllDaoJoinPolls().filter { poll -> poll.isPending }
    }

    /**
     * Gets the latest DAO join poll/proposal created by this user.
     */
    fun getMyDaoJoinPoll(): DaoJoinPoll? {
        val myKey = trustChain.myPeer.publicKey.keyToBin()
        val blocks = trustChain.database.getBlocksWithType(JOIN_REQUEST_BLOCK)
            .filter { b ->
                val data = ProposeUpdateTransactionData(b.transaction).getData()
                data.DAO_ID == daoId && b.linkPublicKey.contentEquals(myKey)
            }
        val block = blocks.maxByOrNull { b -> b.insertTime!! }
        if (block == null) return null
        return DaoJoinPoll(block)
    }

    /**
     * Gets all the update proposal polls created by this user.
     */
    fun getMyUpdateProposals(): List<UpdateProposalPoll> {
        val myKey = trustChain.myPeer.publicKey.keyToBin()
        return trustChain.database.getBlocksWithType(PROPOSE_UPDATE_BLOCK)
            .filter { b -> b.publicKey.contentEquals(myKey) }
            .mapNotNull { b -> try { UpdateProposalPoll(b) } catch (err: Throwable) { null } }
            .filter { poll -> poll.daoId == this.daoId }
            .distinctBy { poll -> poll.proposalId }
    }

    /**
     * Gets all the update proposal polls that have ever been proposed for this app.
     */
    fun getAllUpdatePolls(): List<UpdateProposalPoll> {
        return this.getPolls(PROPOSE_UPDATE_BLOCK, ::UpdateProposalPoll)
    }

    /**
     * Gets all the update proposal polls that have not been finished yet.
     */
    fun getOpenUpdatePolls(): List<UpdateProposalPoll> {
        return this.getAllUpdatePolls().filter { poll -> poll.isPending }
    }

    /**
     * Returns a list of all feature requests for this app, including the ones for which an
     * update/implementation has already been proposed and accepted.
     *
     * Note that the returned list is ordered by creation date with the newest fearure request
     * always being the first item in the list.
     */
    fun getFeatureRequests(): List<FeatureRequest> {
        val blocks = trustChain.database.getBlocksWithType(FEATURE_REQUEST_BLOCK)
        return blocks
            .mapNotNull { b -> try { FeatureRequest(b) } catch (e: Throwable) { null } }
            .filter { b -> b.doaId == this.daoId }
            .sortedBy { b -> b.block.insertTime }
            .reversed()
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
        val joinRequests = this.getAllDaoJoinPolls()
        val myPublicKey = trustChain.myPeer.publicKey.keyToBin().toHex()
        for (request in joinRequests) {
            if (request.requestingUser == myPublicKey) {
                if (request.isPending) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        /**
         * Tries to find an app with the given DAO id.
         */
        fun findByDoaId(daoId: String): P2playApp? {
            val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!

            val joinBlocks = trustChain.database.getBlocksWithType(JOIN_BLOCK)
            val updateBlocks = trustChain.database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)

            val latest = (joinBlocks + updateBlocks)
                .filter { b ->
                    val data = JoinRequestTransactionData(b.transaction).getData()
                    data.DAO_ID == daoId
                }
                .maxByOrNull { b -> b.insertTime!! }

            if (latest == null) return null
            return P2playApp(latest)
        }
    }
}
