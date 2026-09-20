# Projekt-Kontext: PC Games Overview App

## Ziel
Eine Android-App, die im Cover-Flow-Design eine Übersicht der eigenen PC-Spiele anzeigt.
Import der eigenen Spiele soll aus verschiedenen Quellen möglich sein wie:
- Steam
- Gog.com
- Ubisoft
- EA
- Epic Games Store
- Battle.net
- Manually added via search from an online Games Database

## Tech-Stack & Architektur
- **Sprache:** Kotlin
- **UI-Framework:** Jetpack Compose (Modernes UI, deklarativ)
- **Architektur-Muster:** MVVM (Model-View-ViewModel)
- **Datenbank:** Room (Lokale SQLite-Speicherung)
- **Netzwerk:** Ktor oder Retrofit (Für Steam Web API)
- **Bilder-Loading:** Coil (Asynchrones Laden von Cover-Bildern)

## Kern-Funktionen (Roadmap)
1. [x] Projekt-Setup & Basis-Datenklassen
2. [x] Steam Web API Integration (`GetOwnedGames`)
3. [x] Lokale Room-Datenbank & Repository-Pattern
4. [x] UI-Entwicklung: Grid-basierte Library
5. [x] Caching & Performance-Optimierung (Coil)

## Richtlinien für Code-Generierung
- Nutze ausschließlich Jetpack Compose für die UI (keine XML-Layouts für Ansichten).
- Schreibe sauberen, modularisierten Kotlin-Code.
- Beachte die Trennung von Business-Logik (ViewModel) und UI (Compose).
