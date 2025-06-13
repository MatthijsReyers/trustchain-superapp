
## Block types

| Name | Description |
| :--: | :---- |
| `JOIN_BLOCK` | Used as genesis block to create a new App DAO, or to indicate that someone has successfully joined the DAO. |
| `JOIN_REQUEST_BLOCK` | Used by someone not in the App DAO, when they want to join the app DAO, members of the DAO will respond to this block by voting on it with vote blocks. |
| `VOTE_YES_BLOCK` | Used by members to vote "yes"/agree to the proposed transaction | 
| `VOTE_NO_BLOCK` | Used by members to vote "no"/disagree to the proposed transaction | 
| `FEATURE_REQUEST_BLOCK` | Used by members of the DAO to indicate that they would like a certain feature or bug fix, note that others do NOT vote on the contents of this block to indicate how much they want it or something. Other members can instead propose updates to the app in order to claim the bounty for the feature request and other users will vote on that update block. |
| `PROPOSE_UPDATE_BLOCK` | Used to propose an update based on a requested feature, practically this an extension of the `currencyii` `TRANSFER_FUNDS_ASK_BLOCK` because along with an updated magnet link the developer proposes to move the feature bounty to their own wallet. |
| `UPDATE_ACCEPTED_BLOCK` | Resulting block after an update proposal has received enough votes and the transfer has been completed.  |


Practically speaking only the `UPDATE_ACCEPTED_BLOCK` & `JOIN_BLOCK` actually contain the app meta data like name, description, magnet link.
