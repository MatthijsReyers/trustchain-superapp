package nl.tudelft.trustchain.p2playstore.transactionData

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.JOIN_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils

data class JoinRequestData (
    override var DAO_ID: String,
    var SW_UNIQUE_PROPOSAL_ID: String,
    var SW_TRANSACTION_SERIALIZED: String,
    var SW_PREVIOUS_BLOCK_HASH: String,
    var SW_SIGNATURES_REQUIRED: Int,
    var SW_RECEIVER_PK: String
) : BaseData

open class JoinRequestTransactionData(data: JsonObject) : BlockTransactionData(
    data, JOIN_REQUEST_BLOCK
) {
    fun getData(): JoinRequestData {
        return Gson().fromJson(getJsonString(), JoinRequestData::class.java)
    }

    constructor(
        uniqueId: String,
        transactionSerialized: String,
        previousBlockHash: String,
        requiredSignatures: Int,
        receiverPk: String,
        uniqueProposalId: String
    ) : this(
        BlockUtils.objectToJsonObject(
            JoinRequestData(
                uniqueId,
                uniqueProposalId,
                transactionSerialized,
                previousBlockHash,
                requiredSignatures,
                receiverPk
            )
        )
    )

    constructor(transaction: TrustChainTransaction) : this(BlockUtils.parseTransaction(transaction))
}
