# Walkthrough - Custom Search for Manual Matching

I have implemented a **Custom Search** feature within the manual matching step. This allows you to refine or completely change the search query if the automatic matching doesn't find the correct game on IGDB.

## Changes Made

### 1. Refined Resolution UI
- **[MODIFY] [ImportScreen.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/screens/ImportScreen.kt):**
    - Added an editable text field to the expanded **"Resolve"** section for each unmatched game.
    - Added a **"Search"** button that triggers a new search on IGDB using your custom text.
    - The search text defaults to the title found in your Steam/GOG library.

### 2. ViewModel Search Logic
- **[MODIFY] [ImportViewModel.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/viewmodel/ImportViewModel.kt):**
    - Added `searchCustomCandidates()` to handle the logic of fetching new candidates from IGDB and updating the UI state in real-time.

## Verification Results

### Automated Tests
- **Build Success:** `gradle_build(app:assembleDebug)` passed successfully.

### Manual Verification Recommended
1.  **Sync Library:** Run a sync that results in unmatched games.
2.  **Open Resolve:** Tap **Resolve** on an unmatched title.
3.  **Refine Search:**
    - If no matches appear (e.g., for a title like "Gothic 1 Classic"), edit the text to just "Gothic".
    - Tap **Search**.
4.  **Confirm Results:** Verify that the list updates with fresh results for "Gothic". You can then select the correct one to complete the import.
