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

**Status**: ✅ Complete — CSV Export implemented.

---

## 11. Ideas for future features
- Add online platforms: ubisoft


---

## 12. Project Plan Features Realized (post-inspection)
- New game characteristic "Mode". Valid values: Singeplayer, Multiplayer, Co-op 
- Filtering (visualized by a funnel in the top bar), based on labels, genres, mode
- Advanced cloud sync (steam, gog, epic, battle.net)
- Add a ignore_list for online titles to not sync, link it on SyncScreen
- In the Resolve-Step show an option to add to blacklist instead of resolving
- Save the epic e-mail and password