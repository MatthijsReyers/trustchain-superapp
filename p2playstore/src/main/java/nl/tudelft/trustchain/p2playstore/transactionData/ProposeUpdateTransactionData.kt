package nl.tudelft.trustchain.p2playstore.transactionData

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils
import nl.tudelft.trustchain.p2playstore.utils.MagnetLink

data class ProposeUpdateData(
    override var DAO_ID: String,
    var SW_UNIQUE_PROPOSAL_ID: String,
    var SW_PREVIOUS_BLOCK_HASH: String,
    var SW_BITCOIN_PKS: List<String>,
    var SW_SIGNATURES_REQUIRED: Int,
    var SW_TRANSFER_FUNDS_AMOUNT: Long,
    var SW_TRANSFER_FUNDS_TARGET_SERIALIZED: String,
    var SW_RECEIVER_PK: String,
    var SW_TRANSACTION_SERIALIZED: String,
    override var APP_NAME: String,
    override var APP_DESCRIPTION: String,
    override var APP_CATEGORY: String,
    override var APP_ICON: Int,
    override var APP_MAGNET_LINK: String
) : AppMetaData

class ProposeUpdateTransactionData(data: JsonObject) : BlockTransactionData(
    data,
    P2pStoreCommunity.PROPOSE_UPDATE_BLOCK
) {
    fun getData(): ProposeUpdateData {
        return Gson().fromJson(getJsonString(), ProposeUpdateData::class.java)
    }

    constructor(
        uniqueId: String,
        previousWalletBlockHash: String,
        requiredSignatures: Int,
        satoshiAmount: Long,
        bitcoinPks: List<String>,
        transferFundsAddressSerialized: String,
        receiverPk: String,
        uniqueProposalId: String,
        transactionSerialized: String,
        name: String,
        description: String,
        category: String,
        icon: Int,
        magnetLink: String,
    ) : this(
        BlockUtils.objectToJsonObject(
            ProposeUpdateData(
                uniqueId,
                uniqueProposalId,
                previousWalletBlockHash,
                bitcoinPks,
                requiredSignatures,
                satoshiAmount,
                transferFundsAddressSerialized,
                receiverPk,
                transactionSerialized,
                name,
                description,
                category,
                icon,
                magnetLink,
            )
        )
    )

    constructor(transaction: TrustChainTransaction) : this(BlockUtils.parseTransaction(transaction))
}
