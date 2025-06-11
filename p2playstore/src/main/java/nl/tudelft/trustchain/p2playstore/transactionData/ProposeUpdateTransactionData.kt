package nl.tudelft.trustchain.p2playstore.transactionData

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils

data class ProposeUpdateData(
    var SW_UNIQUE_ID: String,
    var SW_UNIQUE_PROPOSAL_ID: String,
    var SW_PREVIOUS_BLOCK_HASH: String,
    var SW_BITCOIN_PKS: List<String>,
    var SW_SIGNATURES_REQUIRED: Int,
    var SW_TRANSFER_FUNDS_AMOUNT: Long,
    var SW_TRANSFER_FUNDS_TARGET_SERIALIZED: String,
    var SW_RECEIVER_PK: String,
    var SW_TRANSACTION_SERIALIZED: String
)

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
        transactionSerialized: String
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
                transactionSerialized
            )
        )
    )

    constructor(transaction: TrustChainTransaction) : this(BlockUtils.parseTransaction(transaction))
}
