
## Source code



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

## Example chains

To further illustrate the TrustChain side of things we have

### Creating a DAO and letting a second member join

| Step | Description |
| :--: | :----- |
| <img src="./docs/step-1.drawio.png" width="250px"> | <h3>Genesis block</h3><p>The DAO is created through the creation of a join block without previous a block, conceptually this is the creator joining a previously non-existent DAO as its only member. Practically this involves the creation of a shared Bitcoin wallet (just like for on-chain-democracy) and providing the data for the initial App release in the form of a name, description, and torrent link to an APK file.</p> |
| <img src="./docs/step-2.drawio.png" width="250px"> | <h3>New user wants to join the DAO</h3><p>When a new user wants to join the App's DAO they create a `JOIN_REQUEST_BLOCK` to inform the members of the DAO that there is something for them to vote on. Conceptually this block represents the start of a poll/vote, but practically this block actually contains the transaction data for the `JOIN_BLOCK` that the user actually wants to add to the chain.</p> |
| <img src="./docs/step-3.drawio.png" width="250px"> | <h3>DAO members vote on join request</h3><p>When the members of a DAO detect the new `JOIN_REQUEST_BLOCK`, they will respond with agreement or disagreement votes based on whether or not they think the new user should be allowed to be become a member of the DAO. Practically voting yes essentially means that the members add a signature to the transaction of moving all the DAO's funds to a new shared wallet which also has the new user's key.</p> |
| <img src="./docs/step-4.drawio.png" width="250px"> | <h3>New user joins DAO</h3><p>The user which created the `JOIN_REQUEST_BLOCK` is responsible for watching the TrustChain and determining when enough votes/signatures have been collected, which can then be used to create a new `JOIN_BLOCK`, using the original transaction data proposed in the `JOIN_REQUEST_BLOCK`. Practically this means a new shared wallet is created with all the keys of the previous members and the key of the new user.</p> |
