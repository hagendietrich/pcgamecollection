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

### Status: ✅ Complete
- `Game.kt` data model created (includes `externalId` and `source` for duplicate prevention).
- `GameDao.kt` with CRUD operations, Flow support, and external ID lookups created.
- `AppDatabase.kt` Room database class implemented.
- `MainApp.kt` (App class) created with database initialization and singleton access.
- `AndroidManifest.xml` updated to use the custom `App` class.

---

## 3. IGDB API Service

### Status: ✅ Complete
- **Authentication**: Implemented Twitch OAuth2 client credentials flow (Client ID + Secret required).
- **Setup Flow**: Created a first-run setup screen to securely collect and validate credentials.
- **Storage**: Used `DataStore` for secure local storage of API keys.
- **Client**: Implemented `IgdbClient` using Ktor for token management and game searching.

---

## 4. Repository (`data/repository/GameRepository.kt`)
One place that wires data source → cache together:

- `searchGames(query: String): List<IgdbGame>` — Fetches credentials from `SettingsRepository`, authenticates with `IgdbClient`, and returns results.
- `addGame(game: Game)` / `getAllGames(): Flow<List<Game>>` delegate to DAO.

### Status: ✅ Complete
- `GameSource.kt` enum created with support for manual, IGDB, Steam, GOG, etc.
- `GameRepository.kt` implemented with dependencies on `GameDao`, `IgdbClient`, and `SettingsRepository`.

---

## 5. UI – Manual Add Screen (`ui/screens/AddGameScreen.kt`)
Compose screen with three fields, one big "Add" button:
- **Title** (EditText/text field) — required. Triggers async IGDB search as soon as user finishes typing or hits Enter. Debounce on text change (or use a small loader after Enter).
- **Cover URL preview** below the form — shows download progress while fetching from IGDB.
- **Release date** — populated automatically from API result, not editable manually (for now).

### Status: ✅ Complete
- `AddGameViewModel.kt` implemented with search and add logic.
- `AddGameScreen.kt` created with IGDB search results list.
- `MainActivity.kt` updated with navigation to Add Game screen.

---

## 6. UI – Game List Screen (`ui/screens/GameListScreen.kt`)
Vertical grid of game covers:
- **Layout**: `LazyVerticalGrid` with adjustable 2 to 5 columns.
- **Minimal Spacing**: Padding between items set to 2dp for a "wall of covers" look.
- **Interaction**:
    - **Tap**: Opens a `ModalBottomSheet` with full info (summary, platform, release date).
    - **Density Control**: [+] and [-] buttons in the top bar to vary cover size.
    - **Removal**: Option to delete the game from the detail bottom sheet.

### Status: ✅ Complete
- `GameListViewModel.kt` implemented with games Flow and column count management.
- `GameListScreen.kt` implemented with a dynamic grid and a detail bottom sheet.
- `GameRepository.kt` updated with `deleteGame` functionality.
- `MainActivity.kt` updated to use the real library screen.

---

## 7. Main Activity + Navigation (`ui/navigation/MainNavigation.kt`)
- `MainActivity` → host a `NavHost` (Jetpack Compose Navigation).
- Bottom navigation with two tabs: "My Games" and "Add Game".

### Status: 📋 Pending
- Replace manual `when(screen)` logic with `NavHost`.
- Implement a proper `BottomNavigation` bar.
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

---

## 11. Project Plan Update Log (post-inspection)

The following were discovered during file inspection and fixed directly:

### ✅ Already Complete (previously inaccurate plan status)
- **IGDB deps in libs.versions.toml**: Added `ktor`, `serializationJson`, `datastore`, `ksp`, and `lifecycle-viewmodel-compose` versions.
- **Gradle Plugins**: Applied `kotlin-serialization` and `ksp` for Room and API support.
- **Ktor & DataStore additions to build.gradle.kts**: Added necessary dependencies for API and secure storage.
- **IGDB API layer**: Created `IgdbModels.kt`, `IgdbClient.kt`, and `SettingsRepository.kt`.
- **First-run Setup**: Created `SetupViewModel` and `SetupScreen`.
- **Repository**: Created `GameRepository.kt` and `GameSource.kt`.
- **Add Game UI**: Created `AddGameViewModel.kt` and `AddGameScreen.kt`.
- **Library UI**: Created `GameListViewModel.kt` and `GameListScreen.kt` with adjustable grid density.

### ✅ Fixed (were missing, now remediated)
- `src/main/AndroidManifest.xml` — added `<uses-permission android:name="android.permission.INTERNET" />`. Plan listed this as a checklist item; it was absent and needed for IGDB HTTP calls.
- `res/values/strings.xml` — created minimal file with `app_name` string resource referenced in manifest (`@string/app_name`).
- `build.gradle.kts` compileSdk DSL → replaced invalid nested braces syntax (`compileSdk { version = release(36) ... }`) with standard `compileSdk = 34`.
- Note: Coil is declared as `2.7.0` (Coil 2 / `io.coil-kt:coil-compose`), **not** `5.0.0` as the plan stated — the two major versions are different artifacts and coil-compose version ≥6 would be incompatible with this AGP/kotlin setup.

### 📋 Pending (now re-scheduled)
| Item | Plan Step# | Priority |
|---|---|---|
| Navigation Setup (NavHost + Bottom Bar) | Navigation (#7) | High |
| Platform filtering / Steam / GOG | Step 2 | Future |

---
