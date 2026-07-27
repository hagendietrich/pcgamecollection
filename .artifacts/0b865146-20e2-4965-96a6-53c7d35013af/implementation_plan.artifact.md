# Implementation Plan - Permanent Search Bar in Library

This plan replaces the "My Collection" title with a permanent, always-visible search bar for faster filtering of the game library.

## Proposed Changes

### UI Components

#### [MODIFY] [AppTopBar.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/components/AppTopBar.kt)
- Update the `TopAppBar` to support a "Permanent Search" mode.
- If `isSearchActive` is true, the `title` slot will directly contain the `TextField`.
- Remove the search toggle icon if it's already active or if a new `showSearchIcon` flag is false.
- Ensure the `TextField` uses the full available width to provide a large tap target.

### Screens

#### [MODIFY] [GameListScreen.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/screens/GameListScreen.kt)
- Remove the `isSearchMode` local state.
- Call `AppTopBar` with `isSearchActive = true` at all times for the library screen.
- Remove the search magnifying glass `IconButton` from the `actions` block, as the search bar is now always visible.
- Remove the "Close Search" (X) logic from the actions, as the search bar is permanent (though the "Clear Text" button inside the TextField remains).

## Verification Plan

### Manual Verification
1.  Open the **My Collection** screen.
2.  Verify that the top bar no longer says "My Collection" and instead shows the search field with the placeholder "Search your collection...".
3.  Type a game title and verify real-time filtering works immediately.
4.  Verify that other screens (Import/Export, Settings) still show their correct titles and do NOT have an always-on search bar.
5.  Check that grouping and sorting buttons are still accessible and functional.
