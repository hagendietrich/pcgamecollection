package com.github.hagendietrich.pcgamecollection.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Price information for a wishlist game on a specific store/platform.
 * Prices are always stored in EUR (default currency).
 * A price of 0.0 means the price could not be fetched and is considered unavailable.
 */
@Serializable
data class PlatformPrice(
    val platform: String,               // "Steam", "GOG", "Epic", ...
    val storeUrl: String,               // Direct link to the store page
    val price: Double,                  // Current price in EUR (0.0 = unavailable)
    val currency: String = "EUR",
    val isOnSale: Boolean = false,
    val originalPrice: Double? = null,  // Base price if currently on sale
    val lastUpdated: Long = System.currentTimeMillis(),
    val externalId: String? = null      // Store-specific ID (e.g., Steam AppID) for refreshing prices
)

/**
 * A game the user does not own yet but wants to purchase.
 * Unlike [Game], it has no playtime or completion tracking, but carries a list of
 * platform-specific prices ([PlatformPrice]) – one entry per store where it is available.
 */
@Serializable
@Entity(tableName = "wishlist_games")
data class WishlistGame(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val coverImageUrl: String? = null,
    val igdbId: Long? = null,
    val parentIgdbId: Long? = null,
    val category: Int? = null,
    val hltbMain: Int? = null,
    val hltbMainExtra: Int? = null,
    val hltbCompletionist: Int? = null,
    val playtimeSource: String? = null,
    val platformPrices: List<PlatformPrice> = emptyList(),
    val dateAdded: Long = System.currentTimeMillis(),
    val notes: String? = null
) {
    /** The lowest currently known price across all platforms (null if none available). */
    fun lowestPrice(): PlatformPrice? =
        platformPrices.filter { it.price > 0.0 }.minByOrNull { it.price }
}
