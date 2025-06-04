package nl.tudelft.trustchain.p2playstore.sharedWallet

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity

data class SWJoinRequestTD(
    var SW_UNIQUE_ID: String,
    var SW_ENTRANCE_FEE: Long,
    var SW_REQUESTER_TRUSTCHAIN_PK: String,
    var SW_REQUEST_PROPOSAL_ID: String = SWUtil.randomUUID()
)

class SWJoinRequestTransactionData(data: JsonObject) : SWBlockTransactionData(
    data,
    P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE
) {
    fun getData(): SWJoinRequestTD {
        return Gson().fromJson(getJsonString(), SWJoinRequestTD::class.java)
    }

    constructor(transaction: TrustChainTransaction) : this(SWUtil.parseTransaction(transaction))

    constructor(
        uniqueId: String,
        entranceFee: Long,
        requesterTrustChainPk: String,
        requestProposalId: String = SWUtil.randomUUID()
    ) : this(
        SWUtil.objectToJsonObject(
            SWJoinRequestTD(
                uniqueId,
                entranceFee,
                requesterTrustChainPk,
                requestProposalId
            )
        )
    )
}
