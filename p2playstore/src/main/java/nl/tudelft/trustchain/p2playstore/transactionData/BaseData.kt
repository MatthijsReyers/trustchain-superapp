package nl.tudelft.trustchain.p2playstore.transactionData

interface BaseData {
    /**
     * Unique identifier for the DAO, all blocks that belong to the same DAO have the same value in
     * this field.
     */
    var DAO_ID: String;
}
