package nl.tudelft.trustchain.p2playstore.transactionData

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils

data class JoinDoaData(
    override var DAO_ID: String,
    var SW_ENTRANCE_FEE: Long,
    override var SW_TRANSACTION_SERIALIZED: String,
    var SW_VOTING_THRESHOLD: Int,
    override var SW_TRUSTCHAIN_PKS: ArrayList<String>,
    override var SW_BITCOIN_PKS: ArrayList<String>,
    override var SW_NONCE_PKS: ArrayList<String>,
    override var APP_NAME: String,
    override var APP_DESCRIPTION: String,
    override var APP_CATEGORY: String,
    override var APP_ICON: Int,
    override var APP_MAGNET_LINK: String
) : AppMetaData, SharedWalletData

class JoinDaoTransactionData(
    data: JsonObject,
) : BlockTransactionData(data, P2pStoreCommunity.JOIN_BLOCK) {

    fun getData(): JoinDoaData {
        return Gson().fromJson(getJsonString(), JoinDoaData::class.java)
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
        entranceFee: Long,
        transactionSerialized: String,
        votingThreshold: Int,
        trustChainPks: ArrayList<String>,
        bitcoinPks: ArrayList<String>,
        noncePks: ArrayList<String>,
        uniqueId: String = BlockUtils.randomUUID(),
        name: String,
        description: String,
        category: String,
        icon: Int,
        magnetLink: String,
    ) : this(
        BlockUtils.objectToJsonObject(
            JoinDoaData(
                uniqueId,
                entranceFee,
                transactionSerialized,
                votingThreshold,
                trustChainPks,
                bitcoinPks,
                noncePks,
                name,
                description,
                category,
                icon,
                magnetLink,
            )
        ),
    )

    constructor(
        transaction: TrustChainTransaction,
    ) : this(
        BlockUtils.parseTransaction(transaction),
    )

    constructor(block: TrustChainBlock) : this(
        BlockUtils.parseTransaction(block.transaction),
    )
}
