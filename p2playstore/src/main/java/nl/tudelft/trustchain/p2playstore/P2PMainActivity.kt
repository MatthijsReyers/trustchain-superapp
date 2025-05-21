package nl.tudelft.trustchain.p2playstore

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import nl.tudelft.trustchain.common.BaseActivity
import nl.tudelft.trustchain.p2playstore.databinding.ActivityMainBinding
import nl.tudelft.trustchain.p2playstore.databinding.FragmentHomeBinding

const val DEFAULT_APK = "search.apk"

class P2PMainActivity : BaseActivity() {
    private lateinit var binding: FragmentHomeBinding

    override val navigationGraph = R.navigation.nav_graph_p2pstore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentHomeBinding.inflate(layoutInflater)

        start()
    }

    private fun start() {
        val continueButton = binding.continueButton
        val fileName = DEFAULT_APK
        continueButton.text = fileName

        continueButton.setOnClickListener {
            loadDynamicCode(fileName)
        }
    }

    private fun loadDynamicCode(fileName: String) {
        try {
            val intent = Intent(this, ExecutionActivity::class.java)
            intent.putExtra(
                "fileName",
                "${this.applicationContext.cacheDir}/${fileName.split("/").last()}"
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
