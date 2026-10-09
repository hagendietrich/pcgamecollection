package com.github.hagendietrich.pcgamecollection.data.sync

import com.github.hagendietrich.pcgamecollection.data.model.DeletedGame
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.model.SyncSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeEngineTest {

    @Test
    fun testMergeNewRemoteGame() {
        val localGame = Game(
            id = 1,
            title = "Local Game",
            coverImageUrl = null,
            igdbId = 100L,
            lastModified = 1000L
        )

        val remoteGame = Game(
            id = 0,
            title = "Remote Game",
            coverImageUrl = null,
            igdbId = 200L,
            lastModified = 2000L
        )

        val snapshot = SyncSnapshot(
            deviceId = "device-b",
            deviceName = "Bazzite",
            games = listOf(remoteGame)
        )

        val result = SyncMergeEngine.mergeSnapshot(
            localGames = listOf(localGame),
            localIgnoredGames = emptyList(),
            localWishlistGames = emptyList(),
            remoteSnapshot = snapshot
        )

        assertEquals(2, result.gamesToInsertOrUpdate.size)
        assertEquals(1, result.newGamesCount)
        assertTrue(result.gamesToInsertOrUpdate.any { it.title == "Remote Game" })
    }

    @Test
    fun testMergeRemoteNewerGameUpdatesLocal() {
        val localGame = Game(
            id = 1,
            title = "Cyberpunk 2077",
            coverImageUrl = null,
            igdbId = 123L,
            playtimeMinutes = 100,
            lastModified = 1000L
        )

        val remoteGame = Game(
            id = 0,
            title = "Cyberpunk 2077",
            coverImageUrl = null,
            igdbId = 123L,
            playtimeMinutes = 250,
            lastModified = 2000L // Newer
        )

        val snapshot = SyncSnapshot(
            deviceId = "device-b",
            deviceName = "Bazzite",
            games = listOf(remoteGame)
        )

        val result = SyncMergeEngine.mergeSnapshot(
            localGames = listOf(localGame),
            localIgnoredGames = emptyList(),
            localWishlistGames = emptyList(),
            remoteSnapshot = snapshot
        )

        assertEquals(1, result.gamesToInsertOrUpdate.size)
        assertEquals(1, result.updatedGamesCount)
        assertEquals(250, result.gamesToInsertOrUpdate.first().playtimeMinutes)
    }

    @Test
    fun testLocalNewerGamePreservedWithCumulativePlaytime() {
        val localGame = Game(
            id = 1,
            title = "Hades",
            coverImageUrl = null,
            igdbId = 456L,
            playtimeMinutes = 300,
            platforms = listOf("Steam"),
            lastModified = 3000L // Local is newer
        )

        val remoteGame = Game(
            id = 0,
            title = "Hades",
            coverImageUrl = null,
            igdbId = 456L,
            playtimeMinutes = 150,
            platforms = listOf("Epic"),
            lastModified = 1000L // Remote is older
        )

        val snapshot = SyncSnapshot(
            deviceId = "device-b",
            deviceName = "Bazzite",
            games = listOf(remoteGame)
        )

        val result = SyncMergeEngine.mergeSnapshot(
            localGames = listOf(localGame),
            localIgnoredGames = emptyList(),
            localWishlistGames = emptyList(),
            remoteSnapshot = snapshot
        )

        assertEquals(1, result.gamesToInsertOrUpdate.size)
        val merged = result.gamesToInsertOrUpdate.first()
        // Playtime should take max (300)
        assertEquals(300, merged.playtimeMinutes)
        // Platforms should be unioned ("Steam", "Epic")
        assertTrue(merged.platforms.contains("Steam"))
        assertTrue(merged.platforms.contains("Epic"))
    }

    @Test
    fun testRemoteDeletionTombstoneRemovesLocalGame() {
        val localGame = Game(
            id = 1,
            title = "Deleted Game",
            coverImageUrl = null,
            igdbId = 999L,
            lastModified = 1000L
        )

        val snapshot = SyncSnapshot(
            deviceId = "device-b",
            deviceName = "Bazzite",
            games = emptyList(),
            deletedRecords = listOf(DeletedGame(igdbId = 999L, title = "Deleted Game"))
        )

        val result = SyncMergeEngine.mergeSnapshot(
            localGames = listOf(localGame),
            localIgnoredGames = emptyList(),
            localWishlistGames = emptyList(),
            remoteSnapshot = snapshot
        )

        assertEquals(0, result.gamesToInsertOrUpdate.size)
        assertEquals(1, result.gamesToDelete.size)
        assertEquals(1, result.deletedGamesCount)
        assertEquals("Deleted Game", result.gamesToDelete.first().title)
    }
}
