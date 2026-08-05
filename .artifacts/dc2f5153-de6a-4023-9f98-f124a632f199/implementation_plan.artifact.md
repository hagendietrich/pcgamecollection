# Unit Testing Strategy - PC Games Overview App

Currently, the project lacks comprehensive unit tests. This plan outlines the introduction of testing infrastructure and the initial suite of unit tests for core business logic.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/gradle/libs.versions.toml)
- Add `mockk`, `kotlinx-coroutines-test`, and `turbine` versions and libraries.

#### [MODIFY] [app/build.gradle.kts](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/build.gradle.kts)
- Add `testImplementation` for the new testing libraries.

### Initial Unit Tests

#### [NEW] [GameRepositoryTest.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/test/java/com/example/digitalcollectionmanager/data/repository/GameRepositoryTest.kt)
- Test title cleaning and fuzzy matching logic.
- Test date normalization.
- Mock DAOs and API clients to test sync logic (e.g., `syncSteamGames` flow).

#### [NEW] [GameListViewModelTest.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/test/java/com/example/digitalcollectionmanager/ui/viewmodel/GameListViewModelTest.kt)
- Test filtering and sorting logic.
- Test multi-select operations.
- Verify Flow interactions using `turbine`.

#### [NEW] [IgdbClientTest.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/test/java/com/example/digitalcollectionmanager/data/api/IgdbClientTest.kt)
- Test URL construction logic (e.g., `getFullCoverUrl`).
- (Optional) Use `MockEngine` from Ktor to test request/response handling without real networking.

## Verification Plan

### Automated Tests
- Run all unit tests via `./gradlew :app:testDebugUnitTest`.
- Ensure new tests pass and provide a foundation for future development.

## Open Questions
- Should we prioritize `MockK` or `Mockito`? (Plan assumes `MockK` as it is more idiomatic for Kotlin).
- Are there specific edge cases in title matching that the user wants to ensure are tested?
