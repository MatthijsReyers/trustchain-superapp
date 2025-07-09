package nl.tudelft.trustchain.p2playstore.transactionData

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils

data class UpdateAcceptedData(
    override var DAO_ID: String,
    override var FEATURE_REQUEST_ID: String,
    var SW_UNIQUE_PROPOSAL_ID: String,
    override var SW_TRANSACTION_SERIALIZED: String,
    override var SW_TRUSTCHAIN_PKS: ArrayList<String>,
    override var SW_BITCOIN_PKS: ArrayList<String>,
    override var SW_NONCE_PKS: ArrayList<String>,
    var SW_TRANSFER_FUNDS_AMOUNT: Long,
    var SW_TRANSFER_FUNDS_TARGET_SERIALIZED: String,
    override var APP_NAME: String,
    override var APP_DESCRIPTION: String,
    override var APP_CATEGORY: String,
    override var APP_ICON: Int,
    override var APP_MAGNET_LINK: String
) : AppMetaData, BaseFeatureRequestData, SharedWalletData

class UpdateAcceptedTransactionData(data: JsonObject) : BlockTransactionData(
    data, UPDATE_ACCEPTED_BLOCK
) {
    fun getData(): UpdateAcceptedData {
        return Gson().fromJson(getJsonString(), UpdateAcceptedData::class.java)
    }

    fun addTrustChainPk(publicKey: String) {
        val data = getData()
        data.SW_TRUSTCHAIN_PKS.add(publicKey)
        jsonData = BlockUtils.objectToJsonObject(data)
    }

    fun addBitcoinPk(publicKey: String) {
        val data = getData()
        data.SW_BITCOIN_PKS.add(publicKey)
        jsonData = BlockUtils.objectToJsonObject(data)
    }

    fun addNoncePk(publicKey: String) {
        val data = getData()
        data.SW_NONCE_PKS.add(publicKey)
        jsonData = BlockUtils.objectToJsonObject(data)
    }

    fun setTransactionSerialized(serializedTransaction: String) {
        val data = getData()
        data.SW_TRANSACTION_SERIALIZED = serializedTransaction
        jsonData = BlockUtils.objectToJsonObject(data)
    }

    constructor(
        uniqueId: String,
        featureRequest: String,
        transactionSerialized: String,
        satoshiAmount: Long,
        trustChainPks: ArrayList<String>,
        bitcoinPks: ArrayList<String>,
        noncePks: ArrayList<String>,
        transferFundsAddressSerialized: String,
        uniqueProposalId: String = BlockUtils.randomUUID(),
        name: String,
        description: String,
        category: String,
        icon: Int,
        magnetLink: String,
    ) : this(
        BlockUtils.objectToJsonObject(
            UpdateAcceptedData(
                uniqueId,
                featureRequest,
                uniqueProposalId,
                transactionSerialized,
                trustChainPks,
                bitcoinPks,
                noncePks,
                satoshiAmount,
                transferFundsAddressSerialized,
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
