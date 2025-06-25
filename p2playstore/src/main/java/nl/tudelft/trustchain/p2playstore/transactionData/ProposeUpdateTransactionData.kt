package nl.tudelft.trustchain.p2playstore.transactionData

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils

data class ProposeUpdateData(
    override var DAO_ID: String,
    var SW_UNIQUE_PROPOSAL_ID: String,
    var SW_PREVIOUS_BLOCK_HASH: String,
    var SW_BITCOIN_PKS: ArrayList<String>,
    var SW_NONCE_PKS: ArrayList<String>,
    var SW_SIGNATURES_REQUIRED: Int,
    var SW_TRANSFER_FUNDS_AMOUNT: Long,
    var SW_TRANSFER_FUNDS_TARGET_SERIALIZED: String,
    var SW_RECEIVER_PK: String,
    var SW_TRANSACTION_SERIALIZED: String,
    override var APP_NAME: String,
    override var APP_DESCRIPTION: String,
    override var APP_CATEGORY: String,
    override var APP_ICON: Int,
    override var APP_MAGNET_LINK: String,
    var FEATURE_REQUEST_ID: String,
    var SOLUTION_TITLE: String,
    var SOLUTION_DESCRIPTION: String,
    var DEVELOPER_PUBLIC_KEY: String,
) : AppMetaData

class ProposeUpdateTransactionData(data: JsonObject) : BlockTransactionData(
    data,
    P2pStoreCommunity.PROPOSE_UPDATE_BLOCK
) {
    fun getData(): ProposeUpdateData {
        return Gson().fromJson(getJsonString(), ProposeUpdateData::class.java)
    }

    // Constructor for feature solutions
    constructor(
        daoId: String,
        featureRequestId: String,
        solutionTitle: String,
        solutionDescription: String,
        developerPublicKey: String,
        apkMagnetLink: String,

        previousWalletBlockHash: String,
        requiredSignatures: Int,
        rewardAmount: Long,
        bitcoinPks: ArrayList<String>,
        noncePks: ArrayList<String>,
        developerBitcoinAddress: String,
        receiverPk: String,
        uniqueProposalId: String,
        transactionSerialized: String,

        appName: String,
        appDescription: String,
        appCategory: String,
        appIcon: Int
    ) : this(
        BlockUtils.objectToJsonObject(
            ProposeUpdateData(
                daoId,
                uniqueProposalId,
                previousWalletBlockHash,
                bitcoinPks,
                noncePks,
                requiredSignatures,
                rewardAmount,
                developerBitcoinAddress,
                receiverPk,
                transactionSerialized,
                appName,
                appDescription,
                appCategory,
                appIcon,
                apkMagnetLink,
                featureRequestId,
                solutionTitle,
                solutionDescription,
                developerPublicKey
            )
        )
    )

    constructor(transaction: TrustChainTransaction) : this(BlockUtils.parseTransaction(transaction))
}
