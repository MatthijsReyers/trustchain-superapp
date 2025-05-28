package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.R

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [AppDetails.newInstance] factory method to
 * create an instance of this fragment.
 */
class AppDetails : BaseFragment() {

    private lateinit var block: TrustChainBlock

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        val args = this.requireArguments();
        val publicKey = args.getByteArray("publicKey")!!
        val sequenceNumber = args.getInt("sequenceNumber").toUInt()

        val community = this.getTrustChainCommunity()
        this.block = community.database.get(publicKey, sequenceNumber)!!

        println("Get block: $block")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_app_details, container, false)
    }
}
