package nl.tudelft.trustchain.p2playstore.utils
import nl.tudelft.trustchain.p2playstore.R

/**
 * Maps a an icon ID to an image resource that represents the given icon, in the future app icons
 * should probably work differently with compressed images or something, but this demonstrates the
 * basic functionality.
 */
fun iconFromIconId(id: Int): Int {
    return when (id) {
        0 -> R.drawable.ic_bitcoin
        1 -> R.drawable.ic_account_balance_wallet_black_24dp
        2 -> R.drawable.ic_group_work_black_24dp
        else -> R.drawable.ic_device_hub_black_24dp
    }
}
