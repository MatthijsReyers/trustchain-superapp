package nl.tudelft.trustchain.p2playstore

import io.mockk.every
import io.mockk.mockk
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_BLOCK
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils
import org.junit.Test
import kotlin.test.assertEquals

interface P2playAppFactory {
    fun create(block: TrustChainBlock): P2playApp
}


class P2PlayAppTest {
    private lateinit var mockTrustChainCommunity : TrustChainCommunity

    @Test
    fun testDAOThreshold() {
        // Arrange
        val mockBlock = mockk<TrustChainBlock>()
        val mockTrustChainCommunity = mockk<TrustChainCommunity>()
        val transaction = JoinDaoTransactionData(1L, "", 3, arrayListOf(""), arrayListOf(""), arrayListOf(""), BlockUtils.randomUUID(), "", "", "", 1, "")

        every {
            mockTrustChainCommunity.createProposalBlock(any(), any(), any())
        } returns mockBlock

        every { mockBlock.type } returns JOIN_BLOCK
        every { mockBlock.transaction } returns transaction.getTransactionData()

        val mockFactory = mockk<P2playAppFactory>()
        val mockApp = mockk<P2playApp>()
        //every { mockApp.getDaoVoteThreshold() } returns 3
        every { mockFactory.create(any()) } returns mockApp

        val mockp2playApp = mockk<P2playApp>()

        // Assert
        assertEquals(3, mockApp.getDaoVoteThreshold())
    }

//    @Test
//    fun testDaoVoteThreshold_forJoinBlock() {
//        // Arrange
//        val mockBlock = mockk<TrustChainBlock>()
//        every { mockBlock.type } returns P2pStoreCommunity.JOIN_BLOCK
//
//        val mockDaoData = mockk<JoinDaoData>()
//        every { mockDaoData.SW_VOTING_THRESHOLD } returns 3
//
//        val app = P2playApp(block = mockBlock, skipInit = true)
//        app.daoData = mockDaoData
//        val threshold = app.getDaoVoteThreshold()
//
//        // Assert
//        assertEquals(3, threshold)
//    }
}

