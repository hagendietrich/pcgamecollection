# Implementation Plan: Multiple Named Saved Views

## Context
Today the app saves **one** library view (search term, grouping, filters, sort order, column count) as single flat keys in `SettingsRepository` (`SAVED_*` preferences), and "Load view" restores that single snapshot. Goal: save **multiple named views**; saving prompts for a name; loading shows a list of saved views (with names) the user can pick from.

Current touch points:
- `data/repository/SettingsRepository.kt` — `saveLibrarySnapshot(...)` / `restoreLibrarySnapshot()` (single snapshot, flat keys)
- `ui/viewmodel/GameListViewModel.kt` — `saveViewSnapshot()` / `restoreViewSnapshot()` (~line 656–677)
- `ui/screens/GameListScreen.kt` — wires `onSaveView`/`onLoadView` to `AppTopBar`; contains local dialog composables at the bottom
- `ui/components/AppTopBar.kt` — dropdown items `save_current_view` / `load_view` (~line 134–145)
- `app/src/main/res/values/strings.xml` + `values-de/strings.xml` — `save_current_view`, `load_view`, `view_saved`, `view_restored`

## Tasks

### Task 1: New data model `LibraryViewSnapshot`
Create `data/model/LibraryViewSnapshot.kt`:
```kotlin
@Serializable
data class LibraryViewSnapshot(
    val name: String,
    val savedAt: Long,
    val searchTerm: String,
    val grouping: GroupingType,
    val filters: LibraryFilters,
    val sortOrder: SortOrder,
    val columns: Int
)
```
`GroupingType`/`SortOrder` are plain Kotlin enums → serializable as-is.

### Task 2: `SettingsRepository` — store/list/restore/delete named views
- Add one Preferences key: `SAVED_VIEWS = stringPreferencesKey("saved_views")` holding a JSON list of `LibraryViewSnapshot`.
- API:
  - `val savedViews: Flow<List<LibraryViewSnapshot>>` (decode JSON, return `emptyList()` on error; sorted by `savedAt` descending)
  - `suspend fun saveLibrarySnapshot(name, searchTerm, grouping, filters, sortOrder, columns)`: upsert by name (case-insensitive match replaces the existing entry with a new `savedAt`)
  - `suspend fun restoreLibrarySnapshot(name: String): String?` — finds the snapshot, writes its settings back into the live `SAVED_*`→current keys (same logic as today), returns the saved search term; updates `savedAt`? → no, keep immutable
  - `suspend fun deleteSavedView(name: String)`
- **One-time migration:** if the `SAVED_VIEWS` list is empty but the old flat keys (`saved_grouping/saved_filter/saved_sort_order/saved_search_term/save_column_count`) all exist, build a single legacy snapshot named `"My View"` / `"Meine Ansicht"` and remove (or keep) the old keys. This preserves existing users' saved view on first update.

### Task 3: `GameListViewModel` — dialog state + multi-view operations
Replace `saveViewSnapshot()`/`restoreViewSnapshot()` with:
- `val savedViews: StateFlow<List<LibraryViewSnapshot>> = settingsRepository.savedViews.stateIn(...)`
- Dialog trigger state (MVVM: VM exposes state, screen renders dialogs):
  - `val showSaveViewDialog: StateFlow<Boolean>` + `fun requestSaveViewDialog()` / `fun dismissSaveViewDialog()`
  - `val restoreRequest: StateFlow<LibraryViewSnapshot?>` — non-null ⇒ "show confirmation dialog for this view"; `fun requestRestoreView(view)`, `fun cancelRestoreView()`
- `fun saveViewSnapshot(name: String)` — calls repo with current `searchQuery/groupingType/libraryFilters/sortOrder/columnCount` values, emits `R.string.view_saved`
- `fun restoreViewSnapshot()` — restores the view referenced by `restoreRequest`, clears it, sets `_searchQuery`, emits `R.string.view_restored`
- `fun deleteSavedView(name: String)` — repo delete, event optional

### Task 4: `GameListScreen` — dialogs + wiring
- Change top bar wiring: `onSaveView = { viewModel.requestSaveViewDialog() }`, `onLoadView = { viewModel.requestLoadViewDialog() }` (new VM function that opens the list dialog state), i.e. the VM needs a `showLoadViewDialog` state (add it in Task 3).
- New `@Composable SaveViewDialog(...)`: `AlertDialog`, title "Save view", `TextField` (state = name), Cancel / Save buttons; Save disabled when name blank (after `trim()`).
- New `@Composable LoadViewDialog(...)`: `AlertDialog`, title "Load view"; body is a `Column` (or `LazyColumn`) of `savedViews`: row shows name + saved date; tapping a row calls `viewModel.requestRestoreView(snapshot)` and closes the list; a delete `IconButton` on each row deletes (optionally with a small confirmation). Empty-list state shows "No saved views" text.
- New confirmation dialog for restore ("Restore view '<name>'?") with Confirm/Cancel buttons — restore only on confirm (matches app style, e.g. `DeleteConfirmDialog`).
- Render these dialogs in `GameListScreen` off the VM states (collectAsState), following the existing local-dialog pattern at the bottom of the file.

### Task 5: String resources (en + de)
`values/strings.xml`:
- `save_view_title` = "Save view"
- `view_name_label` = "Name"
- `view_name_placeholder` = "e.g. RPG, Finished, Action"
- `save` = "Save"
- `cancel` = "Cancel"
- `load_view_empty` = "No saved views"
- `delete_view` = "Delete view"
- `restore_view_confirm_title` = "Restore view?"
- `restore_view_confirm_text` = "This replaces your current search, filters, grouping, sort and columns."

`values-de/strings.xml` (German):
- "Ansicht speichern", "Name", "z. B. RPG, Beendet, Action", "Speichern", "Abbrechen", "Keine gespeicherten Ansichen" → "Keine gespeicherten Ansichten", "Ansicht löschen", "Ansicht wiederherstellen?", "Ersetzt deine aktuelle Suche, Filter, Gruppierung, Sortierung und Spalten."

### Task 6: Build & verify
- `gradle_build app:assembleDebug`
- Deploy to device: open Library menu → "Save current view" → enter name → verify toast "View has been saved."
- Menu → "Load view…" → list shows the named entries → tap one → confirm → view settings (search/filter/grouping/sort/columns) restored, toast "View has been restored."
- Save a second view; verify both appear; delete one via the list; verify the first legacy snapshot migrated correctly on first launch.

## Notes / constraints
- Compose-only UI (no XML layouts), MVVM separation per AGENTS.md.
- Keep `AppTopBar`'s existing `onSaveView`/`onLoadView` parameters unchanged; behavior change is wired through `GameListScreen`.
- No database (Room) change; DataStore preferences are sufficient and match how the app already persists settings.
