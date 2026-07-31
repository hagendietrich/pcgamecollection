# Implementation Plan - Multi-Row MultiSelectTopBar

The goal is to refactor the `MultiSelectTopBar` in `GameListScreen.kt` to display its options in two rows for better organization and usability when multiple games are selected.

## Proposed Changes

### [GameListScreen.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/screens/GameListScreen.kt)

#### [MODIFY] [MultiSelectTopBar](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/screens/GameListScreen.kt#L578)

- Wrap the existing `TopAppBar` and new button row in a `Surface` and `Column`.
- **First Row**: Use `TopAppBar` to show:
    - Navigation Icon: Close icon (Cancel Option).
    - Title: "$selectedCount Selected".
    - Actions: "Remove All" button (with error styling).
- **Second Row**: Add a horizontally scrollable `Row` containing:
    - **Status**: TextButton with a dropdown menu for completion statuses.
    - **Label**: TextButton to open the label manager.
    - **Genre**: TextButton to open the genre manager.
    - **Platform**: TextButton to open the platform manager.
    - **Mode**: TextButton to open the mode manager.
- Ensure all existing dialog logic (labels, genres, platforms, modes, delete confirmation) is preserved.

## Verification Plan

### Manual Verification
- Select one or more games in the library.
- Verify that the `MultiSelectTopBar` appears with two rows.
- Verify that "Remove All" is in the first row.
- Verify that "Status", "Label", "Genre", "Platform", and "Mode" are in the second row and are horizontally scrollable if needed.
- Test each button in the second row to ensure they still open their respective dialogs/menus.
- Test "Remove All" to ensure it still triggers the delete confirmation.
- Test the "Cancel" (Close) button to ensure it clears the selection.
