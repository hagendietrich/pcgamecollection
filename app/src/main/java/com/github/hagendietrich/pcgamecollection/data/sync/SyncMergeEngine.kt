package com.github.hagendietrich.pcgamecollection.data.sync

import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.model.IgnoredGame
import com.github.hagendietrich.pcgamecollection.data.model.SyncSnapshot
import com.github.hagendietrich.pcgamecollection.data.model.WishlistGame

data class MergeResult(
    val gamesToInsertOrUpdate: List<Game>,
    val gamesToDelete: List<Game> = emptyList(),
    val ignoredGamesToInsertOrUpdate: List<IgnoredGame>,
    val wishlistGamesToInsertOrUpdate: List<WishlistGame>,
    val newGamesCount: Int,
    val updatedGamesCount: Int,
    val deletedGamesCount: Int = 0
)

object SyncMergeEngine {

    fun mergeSnapshot(
        localGames: List<Game>,
        localIgnoredGames: List<IgnoredGame>,
        localWishlistGames: List<WishlistGame>,
        remoteSnapshot: SyncSnapshot
    ): MergeResult {
        val gamesToSave = mutableListOf<Game>()
        val gamesToDeleteList = mutableListOf<Game>()
        var newGamesCount = 0
        var updatedGamesCount = 0
        var deletedGamesCount = 0

        val remoteDeletedIgdbIds = remoteSnapshot.deletedRecords.mapNotNull { it.igdbId }.toSet()
        val remoteDeletedTitleKeys = remoteSnapshot.deletedRecords.map { createTitleKey(it.title) }.toSet()

        val localGameMapByIgdbId = localGames.filter { it.igdbId != null }.associateBy { it.igdbId!! }
        
        // Map sourceId (e.g. "STEAM_12345") to local Game for cross-device store matching
        val localGameMapBySourceId = mutableMapOf<String, Game>()
        for (local in localGames) {
            for ((srcKey, srcVal) in local.sourceIds) {
                if (srcVal.isNotBlank()) {
                    localGameMapBySourceId["${srcKey}_$srcVal"] = local
                }
            }
        }
        val localGameMapByTitleKey = localGames.associateBy { createTitleKey(it.title) }

        val handledLocalIds = mutableSetOf<Int>()

        // 1. Process Remote Games with Multi-Tier Matching
        for (remoteGame in remoteSnapshot.games) {
            val matchedLocalGame = 
                (if (remoteGame.igdbId != null) localGameMapByIgdbId[remoteGame.igdbId] else null)
                ?: remoteGame.sourceIds.entries.firstNotNullOfOrNull { (k, v) -> localGameMapBySourceId["${k}_$v"] }
                ?: localGameMapByTitleKey[createTitleKey(remoteGame.title)]
                ?: localGames.find { local ->
                    val lKey = createTitleKey(local.title)
                    val rKey = createTitleKey(remoteGame.title)
                    lKey.contains(rKey) || rKey.contains(lKey)
                }

            // Check if this remote game was deleted locally or via remote tombstone
            val isDeletedByRemote = (remoteGame.igdbId != null && remoteDeletedIgdbIds.contains(remoteGame.igdbId)) ||
                    remoteDeletedTitleKeys.contains(createTitleKey(remoteGame.title)) ||
                    (matchedLocalGame != null && remoteDeletedTitleKeys.contains(createTitleKey(matchedLocalGame.title)))

            if (isDeletedByRemote) {
                if (matchedLocalGame != null) {
                    handledLocalIds.add(matchedLocalGame.id)
                    gamesToDeleteList.add(matchedLocalGame)
                    deletedGamesCount++
                }
                continue
            }

            if (matchedLocalGame == null) {
                // New game from remote
                val newGame = remoteGame.copy(id = 0)
                gamesToSave.add(newGame)
                newGamesCount++
            } else {
                handledLocalIds.add(matchedLocalGame.id)
                val mergedGame = mergeSingleGame(localGame = matchedLocalGame, remoteGame = remoteGame)
                if (mergedGame != matchedLocalGame) {
                    updatedGamesCount++
                }
                gamesToSave.add(mergedGame)
            }
        }

        // 2. Process Local Games not in remote snapshot
        for (localGame in localGames) {
            if (!handledLocalIds.contains(localGame.id)) {
                // Check if deleted by remote tombstone
                val isDeletedByRemote = (localGame.igdbId != null && remoteDeletedIgdbIds.contains(localGame.igdbId)) ||
                        remoteDeletedTitleKeys.contains(createTitleKey(localGame.title))

                if (isDeletedByRemote) {
                    gamesToDeleteList.add(localGame)
                    deletedGamesCount++
                } else {
                    gamesToSave.add(localGame)
                }
            }
        }

        // Merge Ignored Games
        val mergedIgnored = mergeIgnoredGames(localIgnoredGames, remoteSnapshot.ignoredGames)

        // Merge Wishlist Games
        val mergedWishlist = mergeWishlistGames(localWishlistGames, remoteSnapshot.wishlistGames)

        return MergeResult(
            gamesToInsertOrUpdate = gamesToSave,
            gamesToDelete = gamesToDeleteList,
            ignoredGamesToInsertOrUpdate = mergedIgnored,
            wishlistGamesToInsertOrUpdate = mergedWishlist,
            newGamesCount = newGamesCount,
            updatedGamesCount = updatedGamesCount,
            deletedGamesCount = deletedGamesCount
        )
    }

    private fun mergeSingleGame(localGame: Game, remoteGame: Game): Game {
        val remoteIsNewer = remoteGame.lastModified > localGame.lastModified
        val base = if (remoteIsNewer) remoteGame else localGame

        // Cumulative playtimes
        val mergedPlaytimes = (localGame.playtimes.keys + remoteGame.playtimes.keys).associateWith { key ->
            maxOf(localGame.playtimes[key] ?: 0, remoteGame.playtimes[key] ?: 0)
        }
        val mergedPlaytimeMinutes = maxOf(localGame.playtimeMinutes, remoteGame.playtimeMinutes)

        // Cumulative sets
        val mergedPlatforms = (localGame.platforms + remoteGame.platforms).distinct().filter { it.isNotBlank() }
        val mergedGenres = (localGame.genres + remoteGame.genres).distinct().filter { it.isNotBlank() }
        val mergedLabels = (localGame.labels + remoteGame.labels).distinct().filter { it.isNotBlank() }
        val mergedSourceIds = localGame.sourceIds + remoteGame.sourceIds
        val mergedStoreUrls = localGame.storeUrls + remoteGame.storeUrls

        // Union achievements if available
        val mergedAchievements = if (remoteGame.achievements.isNotEmpty() || localGame.achievements.isNotEmpty()) {
            (localGame.achievements + remoteGame.achievements).distinctBy { it.name }
        } else {
            base.achievements
        }

        return base.copy(
            id = localGame.id, // Retain local Room DB Primary Key
            platforms = mergedPlatforms,
            playtimes = mergedPlaytimes,
            playtimeMinutes = mergedPlaytimeMinutes,
            genres = mergedGenres,
            labels = mergedLabels,
            sourceIds = mergedSourceIds,
            storeUrls = mergedStoreUrls,
            achievements = mergedAchievements,
            lastModified = maxOf(localGame.lastModified, remoteGame.lastModified)
        )
    }

    private fun mergeIgnoredGames(
        localList: List<IgnoredGame>,
        remoteList: List<IgnoredGame>
    ): List<IgnoredGame> {
        val existingKeys = localList.map { "${it.platform}_${it.title}_${it.catalogItemId}" }.toSet()
        val result = localList.toMutableList()

        for (remote in remoteList) {
            val key = "${remote.platform}_${remote.title}_${remote.catalogItemId}"
            if (!existingKeys.contains(key)) {
                result.add(remote.copy(id = 0))
            }
        }
        return result
    }

    private fun mergeWishlistGames(
        localList: List<WishlistGame>,
        remoteList: List<WishlistGame>
    ): List<WishlistGame> {
        val localMapByIgdbId = localList.filter { it.igdbId != null }.associateBy { it.igdbId!! }
        val localMapByTitleKey = localList.associateBy { createTitleKey(it.title) }

        val result = localList.toMutableList()
        val handledLocalIds = localList.map { it.id }.toMutableSet()

        for (remote in remoteList) {
            val matchedLocal = (if (remote.igdbId != null) localMapByIgdbId[remote.igdbId] else null)
                ?: localMapByTitleKey[createTitleKey(remote.title)]

            if (matchedLocal == null) {
                result.add(remote.copy(id = 0))
            } else {
                handledLocalIds.add(matchedLocal.id)
            }
        }
        return result
    }

    private fun createTitleKey(title: String): String {
        return title.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
    }
}
