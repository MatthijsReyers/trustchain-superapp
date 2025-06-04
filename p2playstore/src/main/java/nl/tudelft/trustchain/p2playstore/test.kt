//private fun loadFeatureRequests() {
//    lifecycleScope.launch {
//        try {
//            if (daoUniqueId.isEmpty()) {
//                android.util.Log.e("FeatureListFragment", "daoUniqueId is empty. Cannot load feature requests.")
//                binding.tvNoFeatures.text = "Error loading DAO features."
//                binding.tvNoFeatures.visibility = View.VISIBLE
//                binding.recyclerViewFeatures.visibility = View.GONE
//                return@launch
//            }
//
//            // 1. Fetch all feature request blocks for this DAO (includes join requests now)
//            val featureRequestBlocks = withContext(Dispatchers.IO) {
//                p2playStore.getFeatureRequestBlocksForDao(daoUniqueId)
//            }
//            android.util.Log.d("FeatureListFragment", "loadFeatureRequests: Found ${featureRequestBlocks.size} feature request blocks for DAO $daoUniqueId")
//
//            // Parse feature request blocks, pairing with their blocks for timestamp
//            val featureRequestsWithBlocks = featureRequestBlocks.mapNotNull { reqBlock ->
//                try {
//                    FeatureRequestTransactionData(reqBlock.transaction).getData() to reqBlock // Pair data with its block
//                } catch (e: Exception) {
//                    android.util.Log.e("FeatureListFragment", "Failed to parse FeatureRequest block: ${e.message}")
//                    null
//                }
//            }
//
//            // 2. Get all solution blocks for this DAO (only for standard features)
//            val allSolutionBlocks = withContext(Dispatchers.IO) {
//                p2playStore.getSolutionBlocksForDaoAndFeature(daoUniqueId)
//            }
//            android.util.Log.d("FeatureListFragment", "loadFeatureRequests: Found ${allSolutionBlocks.size} total solution blocks for DAO $daoUniqueId (standard features).")
//
//            // Map solution blocks to pairs of (SolutionData, Block) and group by featureId
//            val solutionsGroupedByFeatureId = allSolutionBlocks.mapNotNull { block ->
//                try {
//                    val solutionData = FeatureSolutionTransactionData(block.transaction).getData()
//                    solutionData to block // Pair of SolutionData and its Block
//                } catch (e: Exception) {
//                    android.util.Log.e("FeatureListFragment", "Failed to parse FeatureSolution block: ${e.message}")
//                    null
//                }
//            }.groupBy { it.first.featureId }
//
//            // 3. Get all vote blocks for this DAO (for both standard feature solutions and join requests)
//            // We need the blocks to get the timestamps. Let's fetch all vote blocks and parse.
//            val allFeatureVoteBlocks = withContext(Dispatchers.IO) {
//                getTrustChainCommunity().database.getBlocksWithType(P2pStoreCommunity.FEATURE_VOTE_BLOCK)
//            }
//            android.util.Log.d("FeatureListFragment", "loadFeatureRequests: Found ${allFeatureVoteBlocks.size} total FeatureVote blocks.")
//
//            // Map vote blocks to pairs of (VoteData, Block) and group by featureId and daoId
//            val votesGroupedByFeatureIdAndDao = allFeatureVoteBlocks.mapNotNull { block ->
//                try {
//                    val voteData = FeatureVoteTransactionData(block.transaction).getData()
//                    if (voteData.daoId == daoUniqueId) { // Filter for votes in this DAO
//                        voteData to block // Pair of VoteData and its Block
//                    } else {
//                        null
//                    }
//                } catch (e: Exception) {
//                    android.util.Log.e("FeatureListFragment", "Failed to parse FeatureVote block: ${e.message}")
//                    null
//                }
//            }.groupBy { it.first.featureId } // Group by featureId
//
//
//            // Combine feature requests with their associated data and calculate latest timestamp
//            val featuresWithAssociatedDataAndTimestamp = featureRequestsWithBlocks.mapNotNull { (featureRequest, reqBlock) ->
//                val latestAssociatedBlockTimestamp: Long? = when (featureRequest.requestType) {
//                    P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE -> {
//                        // Find the latest timestamp among vote blocks for this feature request in this DAO
//                        // The vote blocks for join requests use the featureId as the solutionId field.
//                        votesGroupedByFeatureIdAndDao[featureRequest.featureId]?.maxByOrNull { it.second.timestamp.time }?.second?.timestamp?.time
//                    }
//                    else -> {
//                        // Find the latest timestamp among solution blocks for this feature request in this DAO
//                        solutionsGroupedByFeatureId[featureRequest.featureId]?.maxByOrNull { it.second.timestamp.time }?.second?.timestamp?.time
//                    }
//                }
//
//                // Use the latest timestamp found, or the request block's timestamp if no associated blocks
//                val sortTimestamp = latestAssociatedBlockTimestamp ?: reqBlock.timestamp.time
//
//                // Get the lists of solutions and votes
//                val solutionsForRequest = solutionsGroupedByFeatureId[featureRequest.featureId]?.map { it.first } ?: emptyList()
//                val votesForRequest = votesGroupedByFeatureIdAndDao[featureRequest.featureId]?.map { it.first } ?: emptyList() // Use the grouped votes
//
//                FeatureRequestWithSolutionsAndTimestamp(featureRequest, solutionsForRequest, sortTimestamp, votesForRequest)
//            }
//
//            val sortedFeatures = featuresWithAssociatedDataAndTimestamp.sortedByDescending { it.latestTimestamp }
//
//            // Map back to the original adapter data format, adjusting 'solutions' to contain votes for join requests
//            val featuresForAdapter = sortedFeatures.map {
//                if (it.featureRequest.requestType == P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE) {
//                    // For join requests, we want to show the count of votes.
//                    // Pass the list of votes, although the adapter expects List<FeatureSolutionTD>.
//                    // The adapter's ViewHolder will just use the size for the count.
//                    // This is a slight misuse of the data class, but it avoids creating a new adapter type for now.
//                    FeatureRequestWithSolutions(it.featureRequest, it.votes.filterIsInstance<FeatureSolutionTD>()) // Still need to pass a List<FeatureSolutionTD> to match the adapter, but it will be empty.
//                } else {
//                    FeatureRequestWithSolutions(it.featureRequest, it.solutions)
//                }
//            }
//
//
//            adapter.updateFeatures(featuresForAdapter)
//
//            if (featuresForAdapter.isEmpty()) {
//                binding.tvNoFeatures.visibility = View.VISIBLE
//                binding.recyclerViewFeatures.visibility = View.GONE
//            } else {
//                binding.tvNoFeatures.visibility = View.GONE
//                binding.recyclerViewFeatures.visibility = View.VISIBLE
//            }
//
//        } catch (e: Exception) {
//            android.util.Log.e("FeatureListFragment", "Error loading features: ${e.message}")
//            binding.tvNoFeatures.text = "Error loading DAO features: ${e.message}"
//            binding.tvNoFeatures.visibility = View.VISIBLE
//            binding.recyclerViewFeatures.visibility = View.GONE
//        }
//    }
