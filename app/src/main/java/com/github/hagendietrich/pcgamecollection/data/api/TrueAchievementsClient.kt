package com.github.hagendietrich.pcgamecollection.data.api

import android.util.Log
import com.github.hagendietrich.pcgamecollection.data.model.Achievement
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.cookies.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class TrueAchievementsClient {
    private val client = HttpClient {
        install(HttpCookies)
    }

    /**
     * Attempts to find the TrueAchievements game URL by searching for the title.
     */
    suspend fun searchGameUrl(title: String): String? {
        // Try to establish a session first
        try {
            client.get("https://www.trueachievements.com/") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            }
            delay(500)
        } catch (e: Exception) {
            // Ignore session errors
        }

        val searchUrl = "https://www.trueachievements.com/searchresults.aspx?search=${title.replace(" ", "+")}"
        Log.d("TrueAchievementsClient", "Searching for game: $title at $searchUrl")
        return try {
            val searchResponse = client.get(searchUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                header("Accept-Language", "en-US,en;q=0.9")
                header("Referer", "https://www.trueachievements.com/")
                header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                header("Sec-Ch-Ua-Mobile", "?0")
                header("Sec-Ch-Ua-Platform", "\"Windows\"")
                header("Sec-Fetch-Dest", "document")
                header("Sec-Fetch-Mode", "navigate")
                header("Sec-Fetch-Site", "same-origin")
                header("Sec-Fetch-User", "?1")
                header("Upgrade-Insecure-Requests", "1")
            }
            
            val response = searchResponse.bodyAsText()
            val finalUrl = searchResponse.request.url.toString()
            
            Log.d("TrueAchievementsClient", "Search Response length: ${response.length}, Final URL: $finalUrl")
            
            if (finalUrl.contains("/game/") && finalUrl.endsWith("/achievements")) {
                return finalUrl
            } else if (finalUrl.contains("/game/")) {
                return "${finalUrl.trimEnd('/')}/achievements"
            }

            // Look for game slugs/IDs in links like: <a href="/game/Quake-4"> or <a href="/game/1617">
            val gameLinks = Regex("/game/([a-zA-Z0-9\\-]+|[0-9]+)").findAll(response)
                .map { it.groupValues[1] }
                .filter { slug -> 
                    !slug.equals("news", true) && 
                    !slug.equals("videos", true) && 
                    !slug.equals("reviews", true) &&
                    !slug.equals("sessions", true) &&
                    !slug.equals("forum", true) &&
                    !slug.equals("gamer", true) &&
                    !slug.contains(".") 
                }
                .distinct()
                .toList()

            Log.d("TrueAchievementsClient", "Found game slugs: $gameLinks")

            if (gameLinks.isEmpty()) return null

            val cleanTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")
            val bestSlug = gameLinks.minByOrNull { slug ->
                val cleanSlug = slug.lowercase().replace(Regex("[^a-z0-9]"), "")
                when {
                    cleanSlug == cleanTitle -> 0
                    cleanSlug.startsWith(cleanTitle) -> 1
                    cleanSlug.contains(cleanTitle) -> 2
                    else -> 10 + cleanSlug.length
                }
            }

            if (bestSlug != null) {
                "https://www.trueachievements.com/game/$bestSlug/achievements"
            } else null
        } catch (e: Exception) {
            Log.e("TrueAchievementsClient", "Error searching for game: ${e.message}")
            null
        }
    }

    /**
     * Scrapes achievement metadata from a TrueAchievements game page.
     */
    suspend fun fetchAchievements(gameUrl: String): List<Achievement> {
        delay(800)
        Log.d("TrueAchievementsClient", "Fetching achievements from $gameUrl")
        return try {
            val response: String = client.get(gameUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                header("Accept-Language", "en-US,en;q=0.9")
                header("Referer", "https://www.trueachievements.com/")
            }.bodyAsText()

            val internalIdMap = mutableMapOf<String, String>()
            
            // Try to extract mapping from var achievements array
            val jsonRegex = Regex("(?:var|const|let)\\s+(?:achievements|achData|ach_data)\\s*=\\s*(\\[.*?\\]);", RegexOption.DOT_MATCHES_ALL)
            jsonRegex.find(response)?.groupValues?.get(1)?.let { json ->
                val mapRegex = Regex("[\"{]?(?:id|achievementid)[\"}]?\\D+(\\d+)\\D+[\"{]?imageid[\"}]?\\D+(\\d+)")
                mapRegex.findAll(json).forEach { internalIdMap[it.groupValues[1]] = it.groupValues[2] }
            }
            
            // Extract mappings from loose JSON anywhere
            Regex("\\{id\\s*[:=]\\s*(\\d+).*?imageid\\s*[:=]\\s*(\\d+)\\}|\\{imageid\\s*[:=]\\s*(\\d+).*?id\\s*[:=]\\s*(\\d+)\\}").findAll(response).forEach {
                if (it.groupValues[1].isNotBlank()) internalIdMap[it.groupValues[1]] = it.groupValues[2]
                else internalIdMap[it.groupValues[4]] = it.groupValues[3]
            }

            // Broad regex for achievement containers
            val rowRegex = Regex("<li[^>]*?(?:id=\"[^\"]*?\\d+[^\"]*?\"|class=\"[^\"]*?ach[^\"]*?\")[^>]*?>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
            val nameLinkRegex = Regex("<a[^>]*?class=\"[^\"]*?title[^\"]*?\"[^>]*?href=\"([^\"]+?)\"[^>]*?>(.*?)</a>|<a[^>]*?href=\"([^\"]+?)\"[^>]*?class=\"[^\"]*?title[^\"]*?\"[^>]*?>(.*?)</a>")
            val descRegex = Regex("<p[^>]*?>(.*?)</p>")
            val iconRegex = Regex("<img[^>]*?(?:src|data-src)=\"([^\"]+?)\"[^>]*?class=\"[^\"]*?(?:ach-icon|achievement-icon|icon)[^\"]*?\"|<img[^>]*?class=\"[^\"]*?(?:ach-icon|achievement-icon|icon)[^\"]*?\"[^>]*?(?:src|data-src)=\"([^\"]+?)\"|<img[^>]*?(?:src|data-src)=\"([^\"]+?)\"")
            val secretRegex = Regex("title=\"Secret Achievement\"|class=\"[^\"]*?secret[^\"]*?\"|Secret Achievement", RegexOption.IGNORE_CASE)

            val matches = rowRegex.findAll(response).toList()
            Log.d("TrueAchievementsClient", "Found ${matches.size} rows. Internal map size: ${internalIdMap.size}")

            val fetchScope = CoroutineScope(Dispatchers.IO)
            val semaphore = Semaphore(5) // Limit to 5 parallel requests
            
            val needsDeepScan = response.contains("sms1") && internalIdMap.size < matches.size
            if (needsDeepScan) {
                Log.i("TrueAchievementsClient", "Sprite system detected and map is incomplete. Performing Deep Scan...")
            }

            val deferredResults = matches.map { rowMatch ->
                fetchScope.async {
                    val rowTag = rowMatch.value.substringBefore(">")
                    val content = rowMatch.groupValues[1]
                    val nameMatch = nameLinkRegex.find(content)
                    val url = nameMatch?.let { it.groupValues[1].takeIf { v -> v.isNotBlank() } ?: it.groupValues[3] }
                    val name = nameMatch?.let { (it.groupValues[2].takeIf { v -> v.isNotBlank() } ?: it.groupValues[4]).trim() } ?: "Unknown"
                    val description = descRegex.find(content)?.groupValues?.get(1)?.trim()
                    
                    val idFromUrl = url?.let { Regex("/a(\\d+)").find(it)?.groupValues?.get(1) }
                    var imageId = internalIdMap[idFromUrl ?: ""] ?: Regex("data-imageid=\"(\\d+)\"").find(rowTag)?.groupValues?.get(1)

                    if (imageId == null && needsDeepScan && url != null) {
                        semaphore.withPermit {
                            try {
                                delay(100)
                                val detailUrl = if (url.startsWith("http")) url else "https://www.trueachievements.com$url"
                                val detailHtml = client.get(detailUrl).bodyAsText()
                                imageId = Regex("imagestore(?:\\\\/|/)[^\\\\/\"]+(?:\\\\/|/)(\\d+)\\.jpg").find(detailHtml)?.groupValues?.get(1)
                                if (imageId != null) Log.d("TrueAchievementsClient", "Deep Scan: Found Image ID $imageId for '$name'")
                            } catch (e: Exception) {
                                Log.e("TrueAchievementsClient", "Deep Scan failed for $name: ${e.message}")
                            }
                        }
                    }

                    val iconMatch = iconRegex.find(content)
                    val icon = iconMatch?.let { it.groupValues.drop(1).find { v -> v.isNotBlank() } }
                    val isHidden = secretRegex.containsMatchIn(content)

                    val finalIcon = when {
                        icon != null && !icon.contains("spacer.gif") && !icon.contains("noachievementicon.png") -> {
                            if (icon.startsWith("//")) "https:$icon"
                            else if (icon.startsWith("/")) "https://www.trueachievements.com$icon"
                            else if (!icon.startsWith("http")) "https://www.trueachievements.com/$icon"
                            else icon
                        }
                        imageId != null -> {
                            val idLong = imageId.toLong()
                            val folderId = (idLong / 100) * 100
                            val paddedFolder = folderId.toString().padStart(10, '0')
                            "https://www.trueachievements.com/imagestore/$paddedFolder/$imageId.jpg"
                        }
                        idFromUrl != null -> "https://www.trueachievements.com/customimages/${idFromUrl.padStart(6, '0')}.jpg"
                        else -> null
                    }

                    Achievement(
                        name = cleanHtml(name),
                        description = description?.let { cleanHtml(it) },
                        iconUrl = finalIcon,
                        isUnlocked = false,
                        isHidden = isHidden
                    )
                }
            }

            val finalAchievements = deferredResults.awaitAll().filter { it.name != "Unknown" }
            Log.d("TrueAchievementsClient", "Successfully scraped ${finalAchievements.size} achievements")
            finalAchievements
        } catch (e: Exception) {
            Log.e("TrueAchievementsClient", "Error fetching achievements: ${e.message}")
            emptyList()
        }
    }
    
    private fun cleanHtml(text: String): String {
        return text.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }
}
