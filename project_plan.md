# Project Plan – Minimal Working App (Step 1)

## Overview
Build a functional app that lets users manually add PC games via title input, which triggers an IGDB search and downloads cover art + metadata locally. Database schema is designed to accommodate future Steam/GOG integrations without migration headaches.

The plan follows: **data layer first** → **API service** → **Repository → UI**.

---

## 1. Gradle Dependencies (`app/build.gradle.kts`)
Add the following dependencies:
- **Room** + `room-ktx`: database wrapper with Kotlin extensions (already configured in libs.versions.toml, added to app module's implementation list)
- **Coroutines**: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` for suspend functions and lifecycle-aware collections
- **Coil 2** (`io.coil-kt:coil-compose:5.0.0`): declarative image loading from URLs (used in GameListScreen for IGDB cover art)

**Status**: ✅ Complete — dependencies added to `app/build.gradle.kts`, version refs updated in `libs.versions.toml`.

---

## 2. Data Layer – Schema & Database

### Current state
- `Game.kt` has fields: `id`, `title`, `platform`, `coverImageUrl`, `releaseDate`, `isOwned`.
- This schema is already designed for multi-platform (Steam, GOG, Ubisoft, etc.).

### To be created
1. **`data/database/AppDatabase.kt`** – Room database class:
   ```kotlin
   @Database(entities = [Game::class], version = 1, exportSchema = true)
   abstract class AppDatabase : RoomDatabase() {
       abstract fun gameDao(): GameDao
   }
   ```

2. **Single `ViewModelProvider.Factory`** in a `MainApp.kt` module so the same DAO is reused from multiple ViewModels (avoid one DAO per ViewModel).

3. **Migration path forward**: When Steam/GOG fields are added later, bump Room version to 2 and add migrations in `DatabaseHelper.kt`. No refactor of existing queries needed if schema evolution stays backward-compatible (Room handles this automatically when the new DB is on disk for the first time with a higher exported-version — though a proper Migration object is still recommended).

---

## 3. IGDB API Service

IGDB is public, no auth required for search/lookup. Use **Ktor client** (no extra runtime needed beyond coroutine-support, which we already depend on).

### To be created
1. **`data/api/models/IgdbGameModel.kt`**:
   - `name: String`, `coverUrl: String` (use the full-size cover from IGDB), `releaseDate: Date?`. Use kotlinx.serialization for deserialization (library is small and already in many Android projects via `org.jetbrains.kotlinx:kotlinx-serialization-json`).

2. **`data/api/IgdbClient.kt`**:
   ```kotlin
   // Endpoints under https://igdb.com/api/v4/games
   suspend fun search(query: String, limit: Int = 10): List<IgdbGameModel>
   ```
   - Use Ktor `HttpURLBuilder` to construct query params.
   - Handle `200 OK`, log non-2xx with tags/logger and rethrow.

3. **Retry / rate-limit handling**: Wrap calls in a small suspend function that retries at least once on transient failures (network blips). IGDB doesn't enforce strict limits for search, but this is free defensive code.

---

## 4. Repository (`data/repository/GameRepository.kt`)
One place that wires data source → cache together:

- `searchGame(title: String): Flow<IgdbGameModel?>` — observes a single game (single-entry Flow via `StateFlow` + coroutine `runBlocking` or collect-as-flow pattern). Cache the last result after successful fetch.
- `addGame(game: Game)` / `getAllGames(): Flow<List<Game>>` delegate to DAO.

### Future-proofing
Define an enum sealed class for data sources so a new backend is just adding a case, not rewriting logic:
```kotlin
sealed interface GameSource { IGDB ; // GOG/Steam/etc added later }
```
The repository method `getGamesFrom(source: GameSource)` branches to the right implementation.

---

## 5. UI – Manual Add Screen (`ui/screens/AddGameScreen.kt`)
Compose screen with three fields, one big "Add" button:
- **Title** (EditText/text field) — required. Triggers async IGDB search as soon as user finishes typing or hits Enter. Debounce on text change (or use a small loader after Enter).
- **Cover URL preview** below the form — shows download progress while fetching from IGDB.
- **Release date** — populated automatically from API result, not editable manually (for now).

### UX details
- Button disabled until search returns a result.
- Show a toast/snackbar with "Game added" after success.
- Use `rememberCoroutineScope` to launch the fetch without exposing coroutine leaks on screen disposal.

---

## 6. UI – Game List Screen (`ui/screens/GameListScreen.kt`)
Cover-flow / carousel using **HorizontalPager**:
- Pager page = single game card with cover image (loaded via Coil, placeholder while loading).
- Swipe between games; show title below the cover.
- Long-press a game to remove it (or use a floating action button "Remove" on each item).
- Click/tap a game could open a detail screen or just expand into info for now.

---

## 7. Main Activity + Navigation (`ui/navigation/MainNavigation.kt`)
- `MainActivity` → host a `NavHost` (Jetpack Compose Navigation).
- Bottom navigation with two tabs: "My Games" and "Add Game".

---

## 8. Build & Run Checklist
- [ ] Update gradle libs in `.gradle/versionsCatalog.xml` or `build.gradle.kts` (check if using version catalog)
- [ ] Add Room + Coil + Ktor dependencies to app module
- [ ] Add network permission (`android.permission.INTERNET`) in manifest — check already present, add if missing
- [ ] Create `MainApp.kt` with InMemoryAppDatabase for testing during dev (no need for files dir)
- [ ] Get IGDB API actually returning data from `MainActivity` by running the app, typing a known game like "Stardew Valley", and verify cover + date appear in Room.

---

## 9. Files to Create / Modify Summary

| File | Action | Purpose |
|-------|--------|---------|
| `app/build.gradle.kts` | **Modify** | Add Room, Coil, Ktor deps |
| `gradle/libs.versions.toml` (or versionsCatalog.xml) | **Check** | Pin exact library versions |
| `src/main/AndroidManifest.xml` | **Verify** | INTERNET permission present |
| `data/database/AppDatabase.kt` | **Create** | Room DB instantiation |
| `data/api/models/*.kt` | **Create** | IGDB response models |
| `data/api/IgdbClient.kt` | **Create** | HTTP client for IGDB search |
| `data/repository/GameRepository.kt` | **Create** | Business logic + caching |
| `MainApp.kt` (module) | **Create** | Application class + DI setup |
| `ui/screens/AddGameScreen.kt` | **Create** | Manual add UI with IGDB search |
| `ui/screens/GameListScreen.kt` | **Create** | Carousel / cover flow of stored games |
| `ui/navigation/MainNavigation.kt` | **Create** | NavHost + bottom nav |

---

## 10. What Is Explicitly *Not* in Scope (Step 1)
- Steam API auth (`steamcommunity.com/openid`) — reserved for Step 2.
- GOG user profile parsing — same, Step 2+.
- Multi-platform imports — add a "Platforms" tab later with toggles per source.
- Game categories / Genres / Tags from IGDB — optional detail screen in step 2.
- Export/import via CSV/JSON — deferred until after basic CRUD is solid.
