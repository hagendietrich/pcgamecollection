# Implementation Plan - GOG Parsing & Matching Fixes

This plan fixes the incomplete GOG import by correctly handling empty stats arrays and improves the title matching success rate for games with complex names.

## Proposed Changes

### GOG Client

#### [MODIFY] [GogClient.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/data/api/GogClient.kt)
- **Safe Stats Parsing:** Updated the JSON traversal to check if the `stats` field is a `JsonObject` before attempting to access user-specific stats. If it's a `JsonArray` (empty stats), it will now correctly default to 0 playtime instead of skipping the game.

### Repository & Sync Logic

#### [MODIFY] [GameRepository.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/data/repository/GameRepository.kt)
- **Enhanced `findBestIgdbMatch`:**
    - Use results from the **Cleaned Search** for the final fuzzy fallback, as the raw search is often too narrow.
    - Normalize titles by removing ALL non-alphanumeric characters for a "Last Resort" comparison.
    - Handle `4` -> `IV` and `IV` -> `4` bidirectional swaps.
- **Suffix Cleanup:** Added more specific GOG and Steam suffixes to the filter list.

## Verification Plan

### Manual Verification
1.  **Run GOG Sync:** Enter `tarrega8472`.
2.  **Verify Count:** It should now successfully parse all games across all pages (230+).
3.  **Verify Specific Matches:**
    - Check if `The Settlers 4` is now found.
    - Check if `Deus Ex GOTY` is now found.
