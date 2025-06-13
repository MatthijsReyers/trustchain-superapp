package nl.tudelft.trustchain.p2playstore.transactionData

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils

data class UpdateAcceptedData(
    var SW_UNIQUE_ID: String,
    var SW_UNIQUE_PROPOSAL_ID: String,
    var SW_TRANSACTION_SERIALIZED: String,
    var SW_TRUSTCHAIN_PKS: ArrayList<String>,
    var SW_BITCOIN_PKS: ArrayList<String>,
    var SW_NONCE_PKS: ArrayList<String>,
    var SW_TRANSFER_FUNDS_AMOUNT: Long,
    var SW_TRANSFER_FUNDS_TARGET_SERIALIZED: String
)

class UpdateAcceptedTransactionData(data: JsonObject) : BlockTransactionData(
    data,
    P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK
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
        transactionSerialized: String,
        satoshiAmount: Long,
        trustChainPks: ArrayList<String>,
        bitcoinPks: ArrayList<String>,
        noncePks: ArrayList<String>,
        transferFundsAddressSerialized: String,
        uniqueProposalId: String = BlockUtils.randomUUID()
    ) : this(
        BlockUtils.objectToJsonObject(
            UpdateAcceptedData(
                uniqueId,
                uniqueProposalId,
                transactionSerialized,
                trustChainPks,
                bitcoinPks,
                noncePks,
                satoshiAmount,
                transferFundsAddressSerialized
            )
        )
    )

    constructor(transaction: TrustChainTransaction) : this(BlockUtils.parseTransaction(transaction))
}
