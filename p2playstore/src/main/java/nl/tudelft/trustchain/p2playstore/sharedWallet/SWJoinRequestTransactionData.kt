package nl.tudelft.trustchain.p2playstore.sharedWallet

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity

/**
 * Plain‐data holder for a “join DAO” proposal.
 */
data class SWJoinRequestTD(
    var SW_UNIQUE_ID: String,
    var SW_ENTRANCE_FEE: Long,
    var SW_REQUESTER_TRUSTCHAIN_PK: String,
    var SW_REQUEST_PROPOSAL_ID: String = SWUtil.randomUUID()
)

/**
 * Wraps a JsonObject (or TrustChainTransaction) containing a SWJoinRequestTD,
 * and binds it to the JOIN_REQUEST_PROPOSAL_BLOCK type.
 *
 * Inherits getTransactionData() (to build a Map<String,Any> for createProposalBlock)
 * and getJsonString()/getJsonObject() from SWBlockTransactionData.
 */
class SWJoinRequestTransactionData(data: JsonObject) : SWBlockTransactionData(
    data,
    P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE
) {
    /**
     * Deserialize the raw JSON‐string payload into an SWJoinRequestTD object.
     */
    fun getData(): SWJoinRequestTD {
        return Gson().fromJson(getJsonString(), SWJoinRequestTD::class.java)
    }

    /**
     * Convenience constructor: if you already have a TrustChainTransaction,
     * parse it into a JsonObject via SWUtil, then hand that JsonObject to the primary ctor.
     */
    constructor(transaction: TrustChainTransaction) : this(
        SWUtil.parseTransaction(transaction)
    )

    /**
     * Build a new SWJoinRequestTransactionData from its individual fields.
     * You can then call getTransactionData() (inherited from SWBlockTransactionData)
     * to obtain a Map<String, Any> that is ready to pass into createProposalBlock(...).
     *
     * @param uniqueId             The DAO’s unique ID (bytes‐to‐hex)
     * @param entranceFee          The fee (satoshis) proposed by the user
     * @param requesterTrustChainPk The user’s TrustChain public key (hex)
     * @param requestProposalId    A fresh UUID for this join request (default = random UUID)
     */
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
