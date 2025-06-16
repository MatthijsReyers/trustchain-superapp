package nl.tudelft.trustchain.p2playstore.transactionData

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils

data class FeatureRequestData(
    override var DAO_ID: String,
    var FEATURE_REQUEST_ID: String,
    var FEATURE_TITLE: String,
    var FEATURE_DESCRIPTION: String,
    var FEATURE_REWARD: Long,
    var REQUESTER_PUBLIC_KEY: String,
//    var REQUESTER_BITCOIN_ADDRESS: String,
    var FEATURE_STATUS: String = "OPEN"

) : BaseData

class FeatureRequestTransactionData(data: JsonObject) : BlockTransactionData(
    data,
    P2pStoreCommunity.FEATURE_REQUEST_BLOCK
) {
    constructor(transaction: TrustChainTransaction) : this(BlockUtils.parseTransaction(transaction))

    constructor(
        daoId: String,
        title: String,
        description: String,
        reward: Long,
        requesterPublicKey: String,
//        requesterBitcoinAddress: String,
        status: String = "OPEN"
    ) : this(
        BlockUtils.objectToJsonObject(
            FeatureRequestData(
                daoId,
                BlockUtils.randomUUID(),
                title,
                description,
                reward,
                requesterPublicKey,
//                requesterBitcoinAddress,
                status
            )
        )
    )

    fun getData(): FeatureRequestData {
        return Gson().fromJson(getJsonString(), FeatureRequestData::class.java)
    }
}
