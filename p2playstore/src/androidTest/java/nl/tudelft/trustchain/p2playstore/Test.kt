package nl.tudelft.trustchain.p2playstore

import nl.tudelft.trustchain.p2playstore.transactionData.*
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils
import org.junit.Assert.*
import org.junit.Test



class Test {

    @Test
    fun featureRequestData_serialization_roundtrip() {
        val data =
                FeatureRequestData(
                        DAO_ID = "dao123",
                        FEATURE_REQUEST_ID = "feature456",
                        FEATURE_TITLE = "Dark Mode",
                        FEATURE_DESCRIPTION = "Add dark mode to the app",
                        FEATURE_REWARD = 1000L,
                        REQUESTER_PUBLIC_KEY = "pk_abc"
                )
        val tx =
                FeatureRequestTransactionData(
                        data.DAO_ID,
                        data.FEATURE_TITLE,
                        data.FEATURE_DESCRIPTION,
                        data.FEATURE_REWARD,
                        data.REQUESTER_PUBLIC_KEY
                )
        val parsed = tx.getData()
        assertEquals(data.DAO_ID, parsed.DAO_ID)
        assertEquals(data.FEATURE_TITLE, parsed.FEATURE_TITLE)
        assertEquals(data.FEATURE_DESCRIPTION, parsed.FEATURE_DESCRIPTION)
        assertEquals(data.FEATURE_REWARD, parsed.FEATURE_REWARD)
        assertEquals(data.REQUESTER_PUBLIC_KEY, parsed.REQUESTER_PUBLIC_KEY)
    }

    @Test
    fun proposeUpdateData_serialization_roundtrip() {
        val data =
            ProposeUpdateData(
                DAO_ID = "dao123",
                SW_UNIQUE_PROPOSAL_ID = "proposal789",
                SW_PREVIOUS_BLOCK_HASH = "prevhash",
                SW_BITCOIN_PKS = arrayListOf("btc1", "btc2"),
                SW_NONCE_PKS = arrayListOf("nonce1", "nonce2"),
                SW_SIGNATURES_REQUIRED = 2,
                SW_TRANSFER_FUNDS_AMOUNT = 500L,
                SW_TRANSFER_FUNDS_TARGET_SERIALIZED = "btc_addr",
                SW_RECEIVER_PK = "pk_receiver",
                SW_TRANSACTION_SERIALIZED = "txhex",
                APP_NAME = "TestApp",
                APP_DESCRIPTION = "Desc",
                APP_CATEGORY = "Cat",
                APP_ICON = 1,
                APP_MAGNET_LINK = "magnet:?xt=urn:btih:abcdef",
                FEATURE_REQUEST_ID = "feature456",
                SOLUTION_TITLE = "Solution",
                SOLUTION_DESCRIPTION = "Desc",
                DEVELOPER_PUBLIC_KEY = "devpk"
            )
        val tx =
            ProposeUpdateTransactionData(
                data.DAO_ID,
                data.FEATURE_REQUEST_ID,
                data.SOLUTION_TITLE,
                data.SOLUTION_DESCRIPTION,
                data.DEVELOPER_PUBLIC_KEY,
                data.APP_MAGNET_LINK,
                data.SW_PREVIOUS_BLOCK_HASH,
                data.SW_SIGNATURES_REQUIRED,
                data.SW_TRANSFER_FUNDS_AMOUNT,
                data.SW_BITCOIN_PKS,
                data.SW_NONCE_PKS,
                data.SW_TRANSFER_FUNDS_TARGET_SERIALIZED,
                data.SW_RECEIVER_PK,
                data.SW_UNIQUE_PROPOSAL_ID,
                data.SW_TRANSACTION_SERIALIZED,
                data.APP_NAME,
                data.APP_DESCRIPTION,
                data.APP_CATEGORY,
                data.APP_ICON
            )
        val parsed = tx.getData()
        assertEquals(data.DAO_ID, parsed.DAO_ID)
        assertEquals(data.FEATURE_REQUEST_ID, parsed.FEATURE_REQUEST_ID)
        assertEquals(data.SOLUTION_TITLE, parsed.SOLUTION_TITLE)
        assertEquals(data.SW_SIGNATURES_REQUIRED, parsed.SW_SIGNATURES_REQUIRED)
        assertEquals(data.SW_TRANSFER_FUNDS_AMOUNT, parsed.SW_TRANSFER_FUNDS_AMOUNT)
        assertEquals(data.APP_MAGNET_LINK, parsed.APP_MAGNET_LINK)
    }

    @Test
    fun blockUtils_percentageToIntThreshold() {
        assertEquals(2, BlockUtils.percentageToIntThreshold(3, 60))
        assertEquals(1, BlockUtils.percentageToIntThreshold(1, 60))
        assertEquals(3, BlockUtils.percentageToIntThreshold(5, 60))
        assertEquals(5, BlockUtils.percentageToIntThreshold(5, 100))
    }

    @Test
    fun voteYesData_serialization_roundtrip() {
        val data =
                VoteYesData(
                        DAO_ID = "dao123",
                        SW_UNIQUE_PROPOSAL_ID = "proposal789",
                        SW_SIGNATURE_SERIALIZED = "sig",
                        SW_BITCOIN_PK = "btc_pk",
                        SW_NONCE = "nonce"
                )
        val tx =
                VoteYesTransactionData(
                        data.DAO_ID,
                        data.SW_UNIQUE_PROPOSAL_ID,
                        data.SW_SIGNATURE_SERIALIZED,
                        data.SW_BITCOIN_PK,
                        data.SW_NONCE
                )
        val parsed = tx.getData()
        assertEquals(data.DAO_ID, parsed.DAO_ID)
        assertEquals(data.SW_UNIQUE_PROPOSAL_ID, parsed.SW_UNIQUE_PROPOSAL_ID)
        assertEquals(data.SW_SIGNATURE_SERIALIZED, parsed.SW_SIGNATURE_SERIALIZED)
        assertEquals(data.SW_BITCOIN_PK, parsed.SW_BITCOIN_PK)
        assertEquals(data.SW_NONCE, parsed.SW_NONCE)
    }
    @Test
    fun voteNoData_serialization_roundtrip() {
        val data =
                VoteNoData(
                        DAO_ID = "dao999",
                        SW_UNIQUE_PROPOSAL_ID = "proposalNO",
                        SW_SIGNATURE_SERIALIZED = "sigNO",
                        SW_BITCOIN_PK = "btc_pkNO",
                        SW_NONCE = "nonceNO"
                )
        val tx =
                VoteNoTransactionData(
                        data.DAO_ID,
                        data.SW_UNIQUE_PROPOSAL_ID,
                        data.SW_SIGNATURE_SERIALIZED,
                        data.SW_BITCOIN_PK,
                        data.SW_NONCE
                )
        val parsed = tx.getData()
        assertEquals(data.DAO_ID, parsed.DAO_ID)
        assertEquals(data.SW_UNIQUE_PROPOSAL_ID, parsed.SW_UNIQUE_PROPOSAL_ID)
        assertEquals(data.SW_SIGNATURE_SERIALIZED, parsed.SW_SIGNATURE_SERIALIZED)
        assertEquals(data.SW_BITCOIN_PK, parsed.SW_BITCOIN_PK)
        assertEquals(data.SW_NONCE, parsed.SW_NONCE)
    }

    @Test
    fun joinRequestData_serialization_roundtrip() {
        val data =
                JoinRequestData(
                        DAO_ID = "daoJoin",
                        SW_UNIQUE_PROPOSAL_ID = "joinProposal",
                        SW_TRANSACTION_SERIALIZED = "txJoin",
                        SW_PREVIOUS_BLOCK_HASH = "prevHashJoin",
                        SW_SIGNATURES_REQUIRED = 3,
                        SW_RECEIVER_PK = "receiverJoin"
                )
        val tx =
                JoinRequestTransactionData(
                        data.DAO_ID,
                        data.SW_TRANSACTION_SERIALIZED,
                        data.SW_PREVIOUS_BLOCK_HASH,
                        data.SW_SIGNATURES_REQUIRED,
                        data.SW_RECEIVER_PK,
                        data.SW_UNIQUE_PROPOSAL_ID
                )
        val parsed = tx.getData()
        assertEquals(data.DAO_ID, parsed.DAO_ID)
        assertEquals(data.SW_UNIQUE_PROPOSAL_ID, parsed.SW_UNIQUE_PROPOSAL_ID)
        assertEquals(data.SW_TRANSACTION_SERIALIZED, parsed.SW_TRANSACTION_SERIALIZED)
        assertEquals(data.SW_PREVIOUS_BLOCK_HASH, parsed.SW_PREVIOUS_BLOCK_HASH)
        assertEquals(data.SW_SIGNATURES_REQUIRED, parsed.SW_SIGNATURES_REQUIRED)
        assertEquals(data.SW_RECEIVER_PK, parsed.SW_RECEIVER_PK)
    }

    @Test
    fun joinDaoTransactionData_serialization_roundtrip() {
        val data =
            JoinDaoData(
                DAO_ID = "daoJoin",
                SW_ENTRANCE_FEE = 1L,
                SW_TRANSACTION_SERIALIZED = "txJoin",
                SW_VOTING_THRESHOLD = 1,
                SW_TRUSTCHAIN_PKS = arrayListOf("123", "456"),
                SW_BITCOIN_PKS = arrayListOf("123", "456"),
                SW_NONCE_PKS = arrayListOf("345", "567"),
                APP_NAME = "Name",
                APP_DESCRIPTION = "Description",
                APP_CATEGORY = "Category",
                APP_ICON = 1,
                APP_MAGNET_LINK = "magnetlink"
            )
        val tx =
            JoinDaoTransactionData(
                data.SW_ENTRANCE_FEE,
                data.SW_TRANSACTION_SERIALIZED,
                data.SW_VOTING_THRESHOLD,
                data.SW_TRUSTCHAIN_PKS,
                data.SW_BITCOIN_PKS,
                data.SW_NONCE_PKS,
                data.DAO_ID,
                data.APP_NAME,
                data.APP_DESCRIPTION,
                data.APP_CATEGORY,
                data.APP_ICON,
                data.APP_MAGNET_LINK
            )
        val parsed = tx.getData()
        assertEquals(data.DAO_ID, parsed.DAO_ID)
        assertEquals(data.SW_ENTRANCE_FEE, parsed.SW_ENTRANCE_FEE)
        assertEquals(data.SW_TRANSACTION_SERIALIZED, parsed.SW_TRANSACTION_SERIALIZED)
        assertEquals(data.SW_VOTING_THRESHOLD, parsed.SW_VOTING_THRESHOLD)
        assertEquals(data.SW_TRUSTCHAIN_PKS, parsed.SW_TRUSTCHAIN_PKS)
        assertEquals(data.SW_BITCOIN_PKS, parsed.SW_BITCOIN_PKS)
        assertEquals(data.SW_NONCE_PKS, parsed.SW_NONCE_PKS)
        assertEquals(data.APP_NAME, parsed.APP_NAME)
        assertEquals(data.APP_DESCRIPTION, parsed.APP_DESCRIPTION)
        assertEquals(data.APP_CATEGORY, parsed.APP_CATEGORY)
        assertEquals(data.APP_ICON, parsed.APP_ICON)
        assertEquals(data.APP_MAGNET_LINK, parsed.APP_MAGNET_LINK)
    }

    @Test
    fun updatedAcceptedTransactionData_serialization_roundtrip() {
        val data =
            UpdateAcceptedData(
                DAO_ID = "daoJoin",
                FEATURE_REQUEST_ID = "123",
                SW_UNIQUE_PROPOSAL_ID = "456",
                SW_TRANSACTION_SERIALIZED = "txJoin",
                SW_TRUSTCHAIN_PKS = arrayListOf("123", "456"),
                SW_BITCOIN_PKS = arrayListOf("123", "456"),
                SW_NONCE_PKS = arrayListOf("345", "567"),
                SW_TRANSFER_FUNDS_AMOUNT = 1L,
                SW_TRANSFER_FUNDS_TARGET_SERIALIZED = "1h24y",
                APP_NAME = "Name",
                APP_DESCRIPTION = "Description",
                APP_CATEGORY = "Category",
                APP_ICON = 1,
                APP_MAGNET_LINK = "magnetlink"
            )
        val tx =
            UpdateAcceptedTransactionData(
                data.DAO_ID,
                data.FEATURE_REQUEST_ID,
                data.SW_TRANSACTION_SERIALIZED,
                data.SW_TRANSFER_FUNDS_AMOUNT,
                data.SW_TRUSTCHAIN_PKS,
                data.SW_BITCOIN_PKS,
                data.SW_NONCE_PKS,
                data.SW_TRANSFER_FUNDS_TARGET_SERIALIZED,
                data.SW_UNIQUE_PROPOSAL_ID,
                data.APP_NAME,
                data.APP_DESCRIPTION,
                data.APP_CATEGORY,
                data.APP_ICON,
                data.APP_MAGNET_LINK
            )
        val parsed = tx.getData()
        assertEquals(data.DAO_ID, parsed.DAO_ID)
        assertEquals(data.FEATURE_REQUEST_ID, parsed.FEATURE_REQUEST_ID)
        assertEquals(data.SW_UNIQUE_PROPOSAL_ID, parsed.SW_UNIQUE_PROPOSAL_ID)
        assertEquals(data.SW_TRANSFER_FUNDS_AMOUNT, parsed.SW_TRANSFER_FUNDS_AMOUNT)
        assertEquals(data.SW_TRUSTCHAIN_PKS, parsed.SW_TRUSTCHAIN_PKS)
        assertEquals(data.SW_BITCOIN_PKS, parsed.SW_BITCOIN_PKS)
        assertEquals(data.SW_NONCE_PKS, parsed.SW_NONCE_PKS)
        assertEquals(data.SW_TRANSACTION_SERIALIZED, parsed.SW_TRANSACTION_SERIALIZED)
        assertEquals(data.SW_TRANSFER_FUNDS_TARGET_SERIALIZED, parsed.SW_TRANSFER_FUNDS_TARGET_SERIALIZED)
        assertEquals(data.APP_NAME, parsed.APP_NAME)
        assertEquals(data.APP_DESCRIPTION, parsed.APP_DESCRIPTION)
        assertEquals(data.APP_CATEGORY, parsed.APP_CATEGORY)
        assertEquals(data.APP_ICON, parsed.APP_ICON)
        assertEquals(data.APP_MAGNET_LINK, parsed.APP_MAGNET_LINK)
    }

    @Test
    fun blockUtils_randomUUID_isUnique() {
        val uuid1 = BlockUtils.randomUUID()
        val uuid2 = BlockUtils.randomUUID()
        assertNotEquals(uuid1, uuid2)
        assertEquals(22, uuid1.length)
        assertEquals(22, uuid2.length)
    }
}
