package nl.tudelft.trustchain.p2playstore

/**
 * Used as genesis block to create a new App DAO, or to indicate that someone has successfully
 * joined the DAO.
 */
const val JOIN_BLOCK = "P2PLAYSTORE_JOIN_DAO"

/**
 * Used by someone not in the App DAO, when they want to join the app DAO, members of the DAO will
 * respond to this block by voting on it with vote blocks.
 */
const val JOIN_REQUEST_BLOCK = "P2PLAYSTORE_JOIN_REQUEST"

/**
 * Used by members to vote "yes"/agree to the proposed transaction
 */
const val VOTE_YES_BLOCK = "P2PLAYSTORE_VOTE_YES"

/**
 * Used by members to vote "no"/disagree to the proposed transaction
 */
const val VOTE_NO_BLOCK = "P2PLAYSTORE_VOTE_NO"

/**
 * Used by members of the DAO to indicate that they would like a certain feature or bug
 */
const val FEATURE_REQUEST_BLOCK = "P2PLAYSTORE_FEATURE_REQUEST"

/**
 * Used to propose an update based on a requested feature, practically this an extension of the
 * `currencyii` `TRANSFER_FUNDS_ASK_BLOCK` because along with an updated magnet link the developer
 * proposes to move the feature bounty to their own wallet.
 */
const val PROPOSE_UPDATE_BLOCK = "P2PLAYSTORE_PROPOSE_UPDATE"

/**
 * Resulting block after an update proposal has received enough votes and the transfer has been
 * completed.
 */
const val UPDATE_ACCEPTED_BLOCK = "P2PLAYSTORE_UPDATE_ACCEPTED"
