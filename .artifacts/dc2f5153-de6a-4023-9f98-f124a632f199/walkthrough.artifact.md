# Walkthrough - Unit Testing Infrastructure

I have introduced a robust unit testing foundation to the project, covering core business logic in the repository, view models, and API clients.

## Changes

### Build Configuration

- **Testing Stack**: Integrated [MockK](https://mockk.io/) for mocking, [Turbine](https://github.com/cashapp/turbine) for testing Kotlin Flows, and `kotlinx-coroutines-test` for coroutine support.
- **Gradle Updates**: Added necessary dependencies to `libs.versions.toml` and `app/build.gradle.kts`.

### Data Layer Tests

- [GameRepositoryTest.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/test/java/com/example/digitalcollectionmanager/data/repository/GameRepositoryTest.kt):
    - Verified `cleanTitle` correctly handles special characters and edition suffixes.
    - Improved and tested `normalizeDate` to handle both `YYYY-MM-DD` and `DD.MM.YYYY` formats.
    - Verified game mode sorting and IGDB mapping logic.

### UI Layer Tests

- [GameListViewModelTest.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/test/java/com/example/digitalcollectionmanager/ui/viewmodel/GameListViewModelTest.kt):
    - Tested search query filtering.
    - Verified multi-select logic and state management.
    - Ensured sorting order is correctly applied to grouped games.

### API Layer Tests

- [IgdbClientTest.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/test/java/com/example/digitalcollectionmanager/data/api/IgdbClientTest.kt):
    - Tested cover URL construction.
    - Implemented a mock network test for authentication using Ktor's `MockEngine`.
    - Refactored `IgdbClient` to support dependency injection of the `HttpClient` for better testability.

## Verification Results

### Automated Tests
- Ran `:app:testDebugUnitTest`.
- **Result**: All 13 tests passed successfully.

> [!TIP]
> You can now run the tests yourself using the terminal:
> ```bash
> ./gradlew :app:testDebugUnitTest
> ```
