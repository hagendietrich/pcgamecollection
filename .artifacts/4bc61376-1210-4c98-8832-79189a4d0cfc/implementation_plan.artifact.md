# Implementation Plan - Accurate Sync Reporting with Ignored Games

This plan updates the library synchronization and import reporting to accurately distinguish between newly added games, games already present in the library, and games that are being ignored due to the ignore list.

## Proposed Changes

### [Data Layer]

#### [MODIFY] [GameRepository.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/data/repository/GameRepository.kt)
- **`SyncResult` Update**: Add `ignoredCount: Int = 0` to the `SyncResult` data class.
- **Sync Methods Update**: Update the following methods to correctly track and return the number of games filtered by the ignore list:
    - `syncSteamGames`
    - `syncGogGames`
    - `syncEpicGames`
    - `syncUbisoftGames`
    - `syncBattleNetGames`
    - `syncPlayniteGames`

---

### [UI Layer]

#### [MODIFY] [SyncViewModel.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/viewmodel/SyncViewModel.kt)
- **Message Logic**: Update all success message builders to use the format:
  `Successfully synced X games from [Platform]. Y games were already in your library and Z games were ignored.`
- **`syncAllAccounts` Update**: Accumulate `totalIgnored` from individual sync results and include it in the final summary.

#### [MODIFY] [ImportViewModel.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/viewmodel/ImportViewModel.kt)
- Update the Playnite import success message to include the ignored count.

## Verification Plan

### Automated Tests
- Run `gradle app:assembleDebug` to verify no compilation errors.

### Manual Verification
1.  **Add to Ignore List**: Delete an online game and choose "Delete and Ignore".
2.  **Run Sync**: Trigger a sync for that platform (e.g., Steam).
3.  **Verify Report**: Ensure the success message correctly reports the number of games added (likely 0 if no other new games), the number of games already in the library, and the number of games ignored (should be at least 1).
4.  **Sync All**: Connect multiple accounts and run "Sync All" to verify the cumulative counts in the final report.
