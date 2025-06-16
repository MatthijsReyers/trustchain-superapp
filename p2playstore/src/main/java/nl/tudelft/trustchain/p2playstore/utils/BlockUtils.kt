package nl.tudelft.trustchain.p2playstore.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.ceil
import kotlin.math.min

object BlockUtils {
    /**
     * The minimal tx amount defined for creating transactions to avoid dusty transactions
     */
    const val MINIMAL_TRANSACTION_AMOUNT: Int = 5000

    /**
     * Generate a random 128 bit string
     * From: https://sakthipriyan.com/2017/04/02/creating-base64-uuid-in-java.html
     */
    @JvmStatic
    fun randomUUID(): String {
        val uuid: UUID = UUID.randomUUID()
        val src: ByteArray =
            ByteBuffer.wrap(ByteArray(16))
                .putLong(uuid.mostSignificantBits)
                .putLong(uuid.leastSignificantBits)
                .array()
        return Base64.getUrlEncoder().encodeToString(src).substring(0, 22)
    }

    @JvmStatic
    fun parseTransaction(transaction: TrustChainTransaction): JsonObject {
        return stringToJsonObject(transaction["message"].toString())
    }

    @JvmStatic
    fun percentageToIntThreshold(
        total: Int,
        percentage: Int
    ): Int {
        val totalAmount = total.toDouble()
        val oldThreshold = percentage.toDouble()
        val threshold = ceil((oldThreshold / 100.0) * totalAmount).toInt()
        return min(total, threshold)
    }

    /**
     * Converts the number of required signatures back to a percentage, given the total members.
     * Useful as a fallback if the explicit voting threshold is not available.
     */
    @JvmStatic
    fun intThresholdToPercentage(
        total: Int,
        requiredSignatures: Int
    ): Int {
        return if (total > 0) {
            ceil((requiredSignatures.toDouble() / total.toDouble()) * 100.0).toInt()
        } else 0
    }

    @JvmStatic
    fun objectToJsonObject(data: Any): JsonObject {
        val jsonString = Gson().toJson(data)
        return stringToJsonObject(jsonString)
    }

    @JvmStatic
    fun stringToJsonObject(data: String): JsonObject {
        return Gson().fromJson(data, JsonObject::class.java)
    }
}
