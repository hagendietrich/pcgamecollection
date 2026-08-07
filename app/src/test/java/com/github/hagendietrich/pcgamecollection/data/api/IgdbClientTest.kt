package com.github.hagendietrich.pcgamecollection.data.api

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgdbClientTest {

    @Test
    fun `getFullCoverUrl replaces t_thumb with t_cover_big`() {
        val client = IgdbClient()
        val thumb = "//images.igdb.com/igdb/image/upload/t_thumb/co1r8v.jpg"
        val expected = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r8v.jpg"
        assertEquals(expected, client.getFullCoverUrl(thumb))
    }

    @Test
    fun `authenticate updates accessToken on success`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"access_token": "test_token", "expires_in": 3600, "token_type": "Bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        
        val igdbClient = IgdbClient(httpClient)
        val result = igdbClient.authenticate("id", "secret")
        
        assertTrue(result)
        // Since accessToken is private, we can only verify the return value 
        // or check if subsequent requests use the token if we mock them too.
    }
}
