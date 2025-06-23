package nl.tudelft.trustchain.p2playstore.transactionData

interface BaseFeatureRequestData: BaseData {
    override var DAO_ID: String;

    /**
     * Unique identifier for a feature request,
     */
    var FEATURE_REQUEST_ID: String
}
