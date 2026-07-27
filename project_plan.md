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
- Navigation managed via **Top App Bar Menu** (Dropdown):
    - **My Games**: The main library grid.
    - **Add Game**: Search and add from IGDB.
    - **Settings**: Update IGDB/Twitch credentials.

### Status: ✅ Complete
- Proper `NavHost` implemented with defined routes.
- `AppTopBar` created with a global navigation menu.
- `SetupScreen` updated to function as a settings page.
- Full localization support (English & German) implemented.

---

## 8. UI – Playtime Tracking, Completion Status & Grouping
- **Data Expansion**:
    - `playtimeMinutes: Int = 0`.
    - `labels: List<String> = emptyList()` (manually managed).
    - `completionStatus: CompletionStatus` (Enum).
- **Completion Status Enum**:
    - Values: `COMPLETED`, `PLAYING`, `ON_HOLD`, `BACKLOG`, `ABANDONED`.
    - Logical Order: (Highest) Completed > Playing > On-Hold > Backlog > Abandoned (Lowest).
- **UI Details**:
    - **Detail View**: Display playtime as "X hours, Y minutes" and show/edit completion status as well as labels.
    - **Library Grid**:
        - Long-press to enable **Multi-Select Mode**.
        - Action bar appears during multi-select to add/remove labels or update status for all selected games.
        - **Grouping**: Toggle in the top bar to "Group by Status" or "Group by Label" with sticky headers.
- **Sorting**: Add `PLAYTIME_DESC` and `PLAYTIME_ASC` to `SortOrder`.

**Status**: ✅ Complete — Playtime picker, status management, grouping, and multi-select implemented.

---

## 9. Feature – GOG & Steam Profile Import
- **Navigation**: Group imports under an "Import" submenu in the `AppTopBar`.
- **GOG Import**:
    - User provides GOG username.
    - App fetches `https://www.gog.com/u/[user]/games/stats` (JSON API).
    - Parse game titles, IDs, and playtimes.
- **Steam Import**:
    - User provides Steam ID or Vanity URL.
    - App uses official **Steam Web API** (`GetOwnedGames`).
    - Parse game titles and accurate playtimes.
- **UX**: UI informs users about privacy settings and Steam API key requirements.

**Status**: ✅ Complete — Steam API integration, GOG pagination, and fuzzy title matching implemented.

---

## 10. Feature – Data Portability (Export/Import)
- **CSV Export**:
    - Users can export their entire library to a CSV file.
    - File includes titles, platforms, playtimes, genres, and metadata.
    - Uses Android's Storage Access Framework for secure saving.
- **CSV Import**: Allow users to re-import collections from CSV.

**Status**: 🛠️ Partially Complete — CSV Export implemented.

---

## 11. What Is Explicitly *Not* in Scope (Step 1)
- Multi-platform imports (beyond public profile parsing) — Step 3+.
- Advanced cloud sync (deferred until after basic portability is solid).

---

## 12. Project Plan Update Log (post-inspection)

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
| Manually added via search from online Games Database | Step 1 | Med |
| CSV/JSON Import | Step 10 | Low |

---
