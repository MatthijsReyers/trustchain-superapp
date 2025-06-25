package nl.tudelft.trustchain.p2playstore

import android.app.Activity
import android.content.Context
import android.util.Log
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.transactionData.*
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils
import nl.tudelft.trustchain.p2playstore.utils.DAOCreateHelper
import nl.tudelft.trustchain.p2playstore.utils.DAOJoinHelper
import nl.tudelft.trustchain.p2playstore.utils.DAOTransferFundsHelper

class P2pStoreCommunity : Community() {
    override val serviceId: String = "3344FF11BEEF3883287FAC632fc8db5899c5df5b"

    private fun getTrustChainCommunity(): TrustChainCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    val daoJoinHelper = DAOJoinHelper()

    /**
     * 2.1 Send a proposal on the trust chain to join a shared wallet and to collect signatures. The
     * proposal is a serialized bitcoin join transaction. **NOTE** the latest walletBlockData should
     * be given, otherwise the serialized transaction is invalid.
     * @param walletBlock
     * - the latest (that you know of) shared wallet block.
     */
    fun proposeJoinWallet(walletBlock: TrustChainBlock): JoinRequestTransactionData {
        return daoJoinHelper.proposeJoinWallet(myPeer, walletBlock)
    }

    /**
     * 2.2 Commit the join wallet transaction on the bitcoin blockchain and broadcast the result on
     * trust chain.
     *
     * Note: There should be enough sufficient signatures, based on the multisig wallet data.
     * @throws
     * - exceptions if something goes wrong with creating or broadcasting bitcoin transaction.
     * @param walletBlockData
     * - TrustChainTransaction, describes the wallet that is joined
     * @param blockData
     * - SWSignatureAskBlockTD, the block where the other users are voting on
     * @param responses
     * - the positive responses for your request to join the wallet
     */
    fun joinBitcoinWallet(
        walletBlockData: TrustChainTransaction,
        blockData: JoinRequestData,
        responses: List<VoteYesData>,
        context: Context
    ) {
        daoJoinHelper.joinBitcoinWallet(myPeer, walletBlockData, blockData, responses, context)
    }

    /**
     * Searches all known parts of the trust chain for P2PlayStore apps, always returning the
     * latest version of each app inside this user's database.
     */
    fun discoverAllApps(): List<P2playApp> {
        val joinBlocks = getTrustChainCommunity().database.getBlocksWithType(JOIN_BLOCK)
        val updateBlocks = getTrustChainCommunity().database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)
        val blocks = joinBlocks + updateBlocks

        // Get a unique list of the shared wallet IDs of the app DAO's that we know about.
        val appIds = blocks
            .map { b -> JoinDaoTransactionData(b.transaction).getData().DAO_ID }
            .distinctBy { id -> id }

        val latestBlocks = appIds
            .map { id ->
                blocks
                    // Get all blocks with this app ID
                    .filter { b ->
                        JoinDaoTransactionData(b.transaction).getData().DAO_ID == id
                    }
                    // Find the newest block
                    .maxByOrNull { b -> b.timestamp }
            }

        return latestBlocks
            .map { b ->
                try { P2playApp(b!!) }
                catch (err: Throwable) {
                    Log.e("P2PlayStore", "Found invalid app block: $err")
                    null
                }
            }
            .filterNotNull()
    }

    fun discoverMyApps(): List<P2playApp> {
        return this.discoverAllApps().filter { app -> app.isDaoMember() }
    }

    internal fun fetchLatestSharedWalletBlockByDaoId(daoId: String): TrustChainBlock? {
        val joinBlocks = getTrustChainCommunity().database.getBlocksWithType(JOIN_BLOCK)
        val updateBlocks = getTrustChainCommunity().database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)

        return (joinBlocks + updateBlocks)
            .filter { block ->
                try {
                    when(block.type) {
                        JOIN_BLOCK -> JoinDaoTransactionData(block.transaction).getData().DAO_ID == daoId
                        UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(block.transaction).getData().DAO_ID == daoId
                        else -> false
                    }
                } catch (e: Exception) {
                    false
                }
            }
            .maxByOrNull { it.timestamp }
    }

    /**
     * Discover shared wallets that you can join, return the latest (known) blocks Fetch the latest
     * block associated with a shared wallet. swBlockHash - the hash of one of the blocks associated
     * with a shared wallet.
     */
    fun fetchLatestSharedWalletBlock(swBlockHash: ByteArray): TrustChainBlock? {
        val swBlock = getTrustChainCommunity().database.getBlockWithHash(swBlockHash) ?: return null
        val swBlocks = getTrustChainCommunity().database.getBlocksWithType(JOIN_BLOCK)
        return fetchLatestSharedWalletBlock(swBlock, swBlocks)
    }

    /**
     * Fetch the latest shared wallet block, based on a given block 'block'. The unique shared
     * wallet id is used to find the most recent block in the 'sharedWalletBlocks' list.
     */
    private fun fetchLatestSharedWalletBlock(
        block: TrustChainBlock,
        fromBlocks: List<TrustChainBlock>
    ): TrustChainBlock? {
        if (block.type != JOIN_BLOCK) {
            return null
        }
        val walletId = JoinDaoTransactionData(block.transaction).getData().DAO_ID

        return fromBlocks
            .filter { it.type == JOIN_BLOCK } // make sure the blocks have the correct type!
            .filter {
                JoinDaoTransactionData(it.transaction).getData().DAO_ID == walletId
            }
            .maxByOrNull { it.timestamp.time }
    }

    /**
     * Fetch all DAO blocks that contain a signature. These blocks are the response of a signature
     * request. Signatures are fetched from [VOTE_YES_BLOCK] type blocks.
     */
    fun fetchProposalResponses(
        walletId: String,
        proposalId: String
    ): List<VoteYesData> {
        return getTrustChainCommunity()
            .database
            .getBlocksWithType(VOTE_YES_BLOCK)
            .filter {
                val blockData = VoteYesTransactionData(it.transaction)
                blockData.matchesProposal(walletId, proposalId)
            }
            .map { VoteYesTransactionData(it.transaction).getData() }
    }

    companion object {
        // Used as genesis block to create a new App DAO, or to indicate that someone has
        // successfully joined the DAO.
        const val JOIN_BLOCK = "P2PLAYSTORE_JOIN_DAO"

        // Used by someone not in the App DAO, when they want to join the app DAO, members of the
        // DAO will respond to this block by voting on it with vote blocks.
        const val JOIN_REQUEST_BLOCK = "P2PLAYSTORE_JOIN_REQUEST"

        // Used by members to vote "yes"/agree to the proposed transaction
        const val VOTE_YES_BLOCK = "P2PLAYSTORE_VOTE_YES"

        // Used by members to vote "no"/disagree to the proposed transaction
        const val VOTE_NO_BLOCK = "P2PLAYSTORE_VOTE_NO"

        // Used by members of the DAO to indicate that they would like a certain feature or bug
        const val FEATURE_REQUEST_BLOCK = "P2PLAYSTORE_FEATURE_REQUEST"

        // Used to propose an update based on a requested feature, practically this an extension of
        // the `currencyii` `TRANSFER_FUNDS_ASK_BLOCK` because along with an updated magnet link
        // the developer proposes to move the feature bounty to their own wallet.
        const val PROPOSE_UPDATE_BLOCK = "P2PLAYSTORE_PROPOSE_UPDATE"

        // Resulting block after an update proposal has received enough votes and the transfer has
        // been completed.
        const val UPDATE_ACCEPTED_BLOCK = "P2PLAYSTORE_UPDATE_ACCEPTED"
    }
}
