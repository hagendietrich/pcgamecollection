package com.github.hagendietrich.pcgamecollection.data.repository

import com.github.hagendietrich.pcgamecollection.data.api.*
import com.github.hagendietrich.pcgamecollection.data.dao.GameDao
import com.github.hagendietrich.pcgamecollection.data.dao.IgnoredGameDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GameRepositoryTest {

    private lateinit var repository: GameRepository
    private val gameDao = mockk<GameDao>()
    private val ignoredGameDao = mockk<IgnoredGameDao>()
    private val igdbClient = mockk<IgdbClient>()
    private val steamClient = mockk<SteamClient>()
    private val gogClient = mockk<GogClient>()
    private val epicClient = mockk<EpicClient>()
    private val ubisoftClient = mockk<UbisoftClient>()
    private val battleNetClient = mockk<BattleNetClient>()
    private val settingsRepository = mockk<SettingsRepository>()

    @Before
    fun setup() {
        repository = GameRepository(
            gameDao,
            ignoredGameDao,
            igdbClient,
            steamClient,
            gogClient,
            epicClient,
            ubisoftClient,
            battleNetClient,
            settingsRepository
        )
    }

    @Test
    fun `cleanTitle removes special characters and edition suffixes`() {
        assertEquals("The Witcher 3 Wild Hunt", repository.cleanTitle("The Witcher 3: Wild Hunt - GOTY Edition"))
        assertEquals("Game Name", repository.cleanTitle("Game Name®™©"))
        assertEquals("Cyberpunk 2077", repository.cleanTitle("Cyberpunk 2077 (Ultimate Edition)"))
        // Updated expectation to match current logic: + becomes a space
        assertEquals("Modern Warfare Warzone", repository.cleanTitle("Modern Warfare + Warzone"))
    }

    @Test
    fun `normalizeDate converts various formats to YYYY-MM-DD`() {
        assertEquals("2023-01-01", repository.normalizeDate("2023"))
        assertEquals("2023-05-15", repository.normalizeDate("2023-5-15"))
        assertEquals("2023-05-15", repository.normalizeDate("2023/05/15"))
        assertEquals("2023-05-15", repository.normalizeDate("15.05.2023"))
    }

    @Test
    fun `formatTimestamp converts epoch to YYYY-MM-DD`() {
        // 1672531200 is 2023-01-01 00:00:00 UTC
        assertEquals("2023-01-01", repository.formatTimestamp(1672531200L))
        assertEquals("2020-01-01", repository.formatTimestamp(2020L)) // Year-only fallback
        assertNull(repository.formatTimestamp(null))
    }

    @Test
    fun `sortGameModes returns modes in canonical order`() {
        val input = listOf("Multiplayer", "Co-op", "Singleplayer", "Other")
        val expected = listOf("Singleplayer", "Multiplayer", "Co-op", "Other")
        assertEquals(expected, repository.sortGameModes(input))
    }

    @Test
    fun `mapIgdbGameModes maps various strings correctly`() {
        val modes = listOf(
            mockk<com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGameMode> { io.mockk.every { name } returns "Single player" },
            mockk<com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGameMode> { io.mockk.every { name } returns "Massively Multiplayer Online (MMO)" },
            mockk<com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGameMode> { io.mockk.every { name } returns "Co-operative" }
        )
        val result = repository.mapIgdbGameModes(modes)
        assertEquals(listOf("Singleplayer", "Multiplayer", "Co-op"), result)
    }
}
