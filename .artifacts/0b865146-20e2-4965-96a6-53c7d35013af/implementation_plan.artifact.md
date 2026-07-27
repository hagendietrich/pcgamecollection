# Implementation Plan - Custom Search for Manual Matching

This plan allows users to manually refine the search query when resolving unmatched games from Steam or GOG. This is useful when the store title is so different from the IGDB title that the automatic "Best Guess" search returns no results.

## Proposed Changes

### ViewModels

#### [MODIFY] [ImportViewModel.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/viewmodel/ImportViewModel.kt)
- Add `searchCustomCandidates(unmatched: UnmatchedGame, query: String)`:
    - This will call `gameRepository.searchGames(query)`.
    - It will then update the `unmatchedGames` list in the current `ImportUiState.Success` by replacing the specific `UnmatchedGame` object with a copy containing the new candidates.

### UI Layer

#### [MODIFY] [ImportScreen.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/screens/ImportScreen.kt)
- **Update `UnmatchedGameRow`:**
    - Add a `mutableStateOf` for the search text, initialized with the store title.
    - Add an `OutlinedTextField` and a "Search" button inside the expanded section.
    - When "Search" is clicked, trigger the new ViewModel function.
    - Show a small loading indicator while the custom search is in progress.

## Verification Plan

### Manual Verification
1.  **Run Sync:** Trigger a sync that results in unmatched games (e.g., GOG sync).
2.  **Open Resolve:** Expand an unmatched game row.
3.  **Perform Custom Search:**
    - If no results are found for "Gothic 1 Classic", change the text to just "Gothic".
    - Tap **Search**.
    - Verify that a new list of candidates (Gothic, Gothic II, etc.) appears.
4.  **Confirm Selection:** Pick the correct game and verify it imports correctly.
