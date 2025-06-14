package nl.tudelft.trustchain.p2playstore.transactionData

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils

data class VoteYesData(
    override var DAO_ID: String,
    var SW_UNIQUE_PROPOSAL_ID: String,
    var SW_SIGNATURE_SERIALIZED: String,
    var SW_BITCOIN_PK: String,
    var SW_NONCE: String
) : BaseData

class VoteYesTransactionData(data: JsonObject) : BlockTransactionData(
    data,
    P2pStoreCommunity.VOTE_YES_BLOCK
) {
    fun getData(): VoteYesData {
        return Gson().fromJson(getJsonString(), VoteYesData::class.java)
    }

    fun matchesProposal(
        walletId: String,
        proposalId: String
    ): Boolean {
        val data = getData()
        return data.DAO_ID == walletId && data.SW_UNIQUE_PROPOSAL_ID == proposalId
    }

    constructor(
        uniqueId: String,
        uniqueProposalId: String,
        signatureSerialized: String,
        bitcoinPk: String,
        nonce: String
    ) : this(
        BlockUtils.objectToJsonObject(
            VoteYesData(
                uniqueId,
                uniqueProposalId,
                signatureSerialized,
                bitcoinPk,
                nonce
            )
        )
    )

    constructor(transaction: TrustChainTransaction) : this(BlockUtils.parseTransaction(transaction))
}
