package nl.tudelft.trustchain.p2playstore.blockdata

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.currencyii.sharedWallet.SWBlockTransactionData
import nl.tudelft.trustchain.currencyii.sharedWallet.SWUtil
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity

// Data class for Feature Request
data class FeatureRequestTD(
    val featureId: String,
    val title: String,
    val description: String,
    val reward: Long,
    val daoId: String,
    val requesterPublicKey: String,
    val status: String // e.g., "OPEN", "COMPLETED"
)

class FeatureRequestTransactionData(data: JsonObject) : SWBlockTransactionData(
    data,
    P2pStoreCommunity.FEATURE_REQUEST_BLOCK
) {
    constructor(transaction: TrustChainTransaction) : this(SWUtil.parseTransaction(transaction))

    constructor(
        featureId: String,
        title: String,
        description: String,
        reward: Long,
        daoId: String,
        requesterPublicKey: String,
        status: String = "OPEN"
    ) : this(
        SWUtil.objectToJsonObject(
            FeatureRequestTD(
                featureId,
                title,
                description,
                reward,
                daoId,
                requesterPublicKey,
                status
            )
        )
    )

    fun getData(): FeatureRequestTD {
        return Gson().fromJson(getJsonString(), FeatureRequestTD::class.java)
    }
}

// Data class for Feature Solution
data class FeatureSolutionTD(
    val solutionId: String,
    val featureId: String,
    val daoId: String,
    val title: String,
    val description: String,
    val apkMagnetLink: String,
    val developerPublicKey: String
)

class FeatureSolutionTransactionData(data: JsonObject) : SWBlockTransactionData(
    data,
    P2pStoreCommunity.FEATURE_SOLUTION_BLOCK
) {
    constructor(transaction: TrustChainTransaction) : this(SWUtil.parseTransaction(transaction))

    constructor(
        solutionId: String,
        featureId: String,
        daoId: String,
        title: String,
        description: String,
        apkMagnetLink: String,
        developerPublicKey: String
    ) : this(
        SWUtil.objectToJsonObject(
            FeatureSolutionTD(
                solutionId,
                featureId,
                daoId,
                title,
                description,
                apkMagnetLink,
                developerPublicKey
            )
        )
    )

    fun getData(): FeatureSolutionTD {
        return Gson().fromJson(getJsonString(), FeatureSolutionTD::class.java)
    }
}

// Data class for Feature Vote
data class FeatureVoteTD(
    val voteId: String,
    val solutionId: String,
    val featureId: String,
    val daoId: String,
    val isYes: Boolean,
    val voterPublicKey: String
)

class FeatureVoteTransactionData(data: JsonObject) : SWBlockTransactionData(
    data,
    P2pStoreCommunity.FEATURE_VOTE_BLOCK
) {
    constructor(transaction: TrustChainTransaction) : this(SWUtil.parseTransaction(transaction))

    constructor(
        voteId: String,
        solutionId: String,
        featureId: String,
        daoId: String,
        isYes: Boolean,
        voterPublicKey: String
    ) : this(
        SWUtil.objectToJsonObject(
            FeatureVoteTD(
                voteId,
                solutionId,
                featureId,
                daoId,
                isYes,
                voterPublicKey
            )
        )
    )

    fun getData(): FeatureVoteTD {
        return Gson().fromJson(getJsonString(), FeatureVoteTD::class.java)
    }
}
