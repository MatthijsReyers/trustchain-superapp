package nl.tudelft.trustchain.p2playstore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.p2playstore.databinding.FragmentHomeBinding
import nl.tudelft.trustchain.p2playstore.databinding.FragmentJoinDaoBinding

class JoinDao : Fragment() {

//    private var _binding: FragmentHomeBinding? = null
    private var _binding: FragmentJoinDaoBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJoinDaoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.clickToJoin.setOnClickListener { // <-- Use the ID from fragment_join_dao.xml
            findNavController().navigate(R.id.proposalsFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
