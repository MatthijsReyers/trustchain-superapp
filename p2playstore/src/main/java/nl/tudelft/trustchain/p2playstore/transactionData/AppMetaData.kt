package nl.tudelft.trustchain.p2playstore.transactionData

/**
 * All blocks that contain a (new) version of the app implement this.
 */
interface AppMetaData : BaseData {
    override var DAO_ID: String

    /**
     * App name, technically this can change between updates.
     */
    var APP_NAME: String

    var APP_DESCRIPTION: String

    var APP_CATEGORY: String

    /**
     * This integer maps to an image resource which will be used as the icon, see the
     * iconFromIconId function in utils..
     */
    var APP_ICON: Int

    /**
     * Magnet link that points to a torrent which contains the actual APK file that we need to
     * download and run to open the app.
     */
    var APP_MAGNET_LINK: String
}

