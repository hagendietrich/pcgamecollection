# PC Game Collection

PC Game Collection is an Android application designed to centralize and visualize your PC game collection from multiple sources in a modern, cover-based UI.

## Features

- **Multi-Store Sync**: Seamlessly import your library from Steam, GOG, Epic Games, Ubisoft Connect, and Battle.net.
- **Rich Metadata**: Automatically fetches high-quality covers, summaries, genres, and ratings using the IGDB database.
- **Beautiful UI**: Built entirely with Jetpack Compose, featuring a responsive grid-based library and detailed game views.
- **Advanced Filtering**: Organize your collection by platform, genre, completion status, or custom labels.
- **Playtime Tracking**: View and aggregate your playtime across different platforms.
- **Manual Management**: Add games manually or via search, and manage a "blacklist" of ignored titles.
- **Data Portability**: Import and export your collection using JSON, including support for Playnite exports.

## Setup & Configuration

To use all features of the app, especially synchronization and metadata enrichment, you need to provide some external credentials in the **Setup** screen.

### 1. IGDB (Metadata & Covers)
The app uses the IGDB database for fetching game information. You need a Twitch Developer account for this:
- Go to the [Twitch Developers Console](https://dev.twitch.tv/console).
- Register a new application (e.g., "PC Game Collection").
- Set the OAuth Redirect URL to `http://localhost`.
- Generate a **Client ID** and **Client Secret**.
- Enter these in the app's Setup screen.

### 2. Steam Integration
To sync your Steam library:
- Obtain a **Steam Web API Key** from [steamcommunity.com/dev/apikey](https://steamcommunity.com/dev/apikey).
- In your Steam Profile settings, ensure that **My profile** and **Game details** are set to **Public**.

### 3. GOG.com Integration
GOG synchronization relies on your public profile page:
- No API key is required.
- Go to your GOG account settings and ensure your **Profile is Public**.
- Specifically, the **Game list and stats** must be visible to everyone.

### 4. Epic and Battle.net
These integrations use a secure web-based login (OAuth or session cookies) directly within the app. No external API keys are needed; just follow the prompts in the **Sync** screen.

### 5. EA app and Ubisoft Connect
These stores don't provide any interface that can be accessed (neither api nor webpage) to fetch a list of owned games.
You have to add your owned games manually or import them from a Playnite export.

## Architecture

The project follows modern Android development patterns:
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room for local persistence
- **Networking**: Ktor for API communication
- **Image Loading**: Coil

For a more detailed technical overview, see [docs/overview.md](docs/overview.md).

## License

This project is released under the [Unlicense](LICENSE).
