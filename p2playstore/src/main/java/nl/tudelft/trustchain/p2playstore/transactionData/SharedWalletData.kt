package nl.tudelft.trustchain.p2playstore.transactionData

interface SharedWalletData {
    var SW_TRANSACTION_SERIALIZED: String
    var SW_TRUSTCHAIN_PKS: ArrayList<String>
    var SW_BITCOIN_PKS: ArrayList<String>
    var SW_NONCE_PKS: ArrayList<String>
}
