# Implementation Plan - Collapsible Group Headers with Game Count

This plan adds game counts to group headers and implements collapsible sections in the library grid.

## Proposed Changes

### UI Layer

#### [MODIFY] [GameListScreen.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/screens/GameListScreen.kt)
- **Collapse State:** Add a `remember { mutableStateMapOf<String, Boolean>() }` to track which groups are collapsed. By default, all groups will be expanded.
- **Header UI Update:**
    - Update the header `Text` to include the game count: `groupName (count)`.
    - Add a `Row` in the header to show an expand/collapse icon (e.g., `KeyboardArrowDown` / `KeyboardArrowUp`).
    - Add a `clickable` modifier to the header `Surface` to toggle the collapse state for that group.
- **Grid Content Update:**
    - Wrap the `items(gamesInGroup)` call in a conditional check based on the collapse state. If a group is collapsed, its games will not be rendered in the grid.

## Verification Plan

### Manual Verification
1.  **Group by Platform:** Enable platform grouping.
2.  **Verify Counts:** Check that each header shows the correct number of games (e.g., "Steam (198)").
3.  **Toggle Collapse:** Tap a header (like "Steam"). Verify that the 198 games disappear and only the header remains.
4.  **Verify Icon:** Ensure the arrow icon flips direction when toggled.
5.  **Expand:** Tap again to bring the games back.
