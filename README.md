# Nexus Agent — Android KI-Assistent

Nexus Agent ist eine native Android-App, die einen intelligenten KI-Assistenten direkt auf dem Smartphone bereitstellt. Die App unterstützt mehrere LLM-Provider (OpenRouter, Anthropic Claude, OpenAI GPT, Google Gemini) und bietet einen Agent-Loop mit Tool-Nutzung, Speicherverwaltung, geplanten Aufgaben und einem schwebenden Overlay-Chat.

---

## Screenshots

| Chat | Einstellungen | Overlay |
|------|---------------|---------|
| ![Chat](docs/screenshots/chat.png) | ![Settings](docs/screenshots/settings.png) | ![Overlay](docs/screenshots/overlay.png) |

*(Platzhalter — Screenshots werden ergänzt)*

---

## Architektur

```
┌─────────────────────────────────────────────────────────────┐
│                         App-Modul                            │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ MainActivity │  │NavHost/Theme │  │ NexusApplication  │  │
│  └──────────────┘  └──────────────┘  └───────────────────┘  │
└───────────────────────────┬─────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌───────────────┐  ┌────────────────┐  ┌────────────────────┐
│ Feature-Chat  │  │Feature-Settings│  │  Feature-Overlay   │
│               │  │                │  │                    │
│ • ChatScreen  │  │ • SettingsUI  │  │ • FloatingService  │
│ • ViewModel   │  │ • ProviderCfg │  │ • OverlayView      │
│ • MessageCard │  │ • ViewModel   │  │ • NotificationLstn │
└───────┬───────┘  └───────┬────────┘  └─────────┬──────────┘
        │                  │                     │
        ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│                        Core-Modul                            │
│                                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ Database │ │ Models   │ │Providers │ │ Domain/Agent  │  │
│  │ (Room)   │ │ (DTOs)   │ │ (Retrofit│ │ • AgentLoop   │  │
│  │          │ │          │ │  OkHttp) │ │ • ToolRegistry│  │
│  │ Messages │ │ LlmProv. │ │          │ │ • Tools       │  │
│  │ Tools    │ │ ChatMsg  │ │ OpenRout.│ │ • MemorySys   │  │
│  │ Memory   │ │ ToolCall │ │ Anthropic│ │ • Permissions │  │
│  │ Jobs     │ │ etc.     │ │ OpenAI   │ │               │  │
│  │ Skills   │ │          │ │ Gemini   │ │               │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘  │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     Agent-Engine-Modul                       │
│                                                             │
│  ┌──────────────────┐ ┌───────────────┐ ┌───────────────┐  │
│  │AgentOrchestrator │ │ SkillExecutor │ │ScheduleManager│  │
│  │                   │ │               │ │ (WorkManager) │  │
│  │ • Heartbeat       │ │ • install     │ │               │  │
│  │ • processRequest  │ │ • uninstall   │ │ • createJob   │  │
│  │ • scheduleJob     │ │ • execute     │ │ • enable/dsb  │  │
│  └──────────────────┘ └───────────────┘ └───────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Modul-Struktur

| Modul | Beschreibung |
|-------|-------------|
| `app` | Hauptanwendung, Navigation, Theme, MainActivity |
| `core` | Datenbank, Modelle, LLM-Provider, Agent-Loop, Tools, Speichersystem |
| `feature-chat` | Chat-UI mit Nachrichtenblasen, Tool-Cards, Eingabeleiste |
| `feature-settings` | Einstellungen, Provider-Konfiguration, Autonomie-Stufen |
| `feature-overlay` | Accessibility-Service, schwebendes Overlay, Notification-Listener |
| `agent-engine` | AgentOrchestrator, SkillExecutor, ScheduleManager (WorkManager) |

---

## Tech-Stack

| Kategorie | Technologie |
|-----------|-------------|
| **Sprache** | Kotlin 2.1.20 |
| **UI Framework** | Jetpack Compose mit Material 3 (BOM 2025.05.00) |
| **DI** | Dagger Hilt 2.52 |
| **Datenbank** | Room 2.7.0 (SQLite) |
| **Netzwerk** | Retrofit 2.11 + OkHttp 4.12 |
| **Async** | Kotlin Coroutines 1.9 + Flow |
| **Scheduling** | WorkManager 2.10 |
| **Serialization** | Kotlinx Serialization 1.7.3 |
| **Bilder** | Coil 2.7.0 |
| **HTML-Parsing** | Jsoup 1.18.1 |
| **Kamera** | CameraX 1.4.0 |
| **ML Kit** | Text Recognition 16.0, Barcode Scanning 17.3 |
| **Maps/Location** | Google Play Services Maps 19.0, Location 21.3 |
| **Version Catalog** | Gradle `libs.versions.toml` |
| **Build System** | Kotlin DSL |
| **KSP** | 2.1.20-1.0.25 |
| **Min SDK** | 26 (Android 8.0) |
| **Target/Compile SDK** | 35 (Android 15) |
| **Java** | 17 |
| **Testing** | JUnit 5.11, Espresso 3.6.1, Turbine 1.2.0 |

---

## Features

### Chat-System
- Echtzeit-Streaming-Antworten von LLMs
- Unterstützung für Tool-Aufrufe mit visueller Darstellung
- Nachrichtenverlauf in Room-Datenbank
- Mehrere Unterhaltungen

### LLM-Provider
- **OpenRouter** — Zugriff auf hunderte Modelle
- **Anthropic** — Claude Sonnet/Opus
- **OpenAI** — GPT-4o, o3, o4-mini
- **Google** — Gemini 2.0 Flash/Pro

### Agent-Fähigkeiten
- **Web-Suche** — Internetsuche via DuckDuckGo
- **Web-Abruf** — URL-Inhalte extrahieren (Jsoup)
- **Dateizugriff** — Lesen/Schreiben auf dem Gerät
- **Shell-Befehle** — Eingeschränkte Shell-Ausführung
- **Speicher** — Fakten speichern und abrufen (Vektor-Suche)
- **Planung** — Geplante Aufgaben via WorkManager

### Overlay
- Schwebendes Chat-Overlay über anderen Apps
- AccessibilityService-basiert
- NotificationListener für intelligente Benachrichtigungen

### Datenschutz
- Alle Daten lokal auf dem Gerät gespeichert
- API-Keys nur lokal in DataStore Preferences
- Keine Telemetrie oder Tracking

---

## Build-Anleitung

### Voraussetzungen
- Android Studio Hedgehog (2023.1.1) oder neuer
- JDK 17
- Android SDK 35

### Projekt klonen
```bash
git clone <repository-url>
cd projekt-ki-agent-android
```

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### Tests ausführen
```bash
# Unit Tests
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug
```

### Auf verbundenem Gerät starten
```bash
./gradlew installDebug
```

### In Android Studio öffnen
1. Android Studio starten
2. "Open" wählen → Ordner `projekt-ki-agent-android` auswählen
3. Gradle-Sync abwarten

### API-Key konfigurieren
1. App starten → Einstellungen
2. Einen LLM-Provider auswählen
3. API-Schlüssel eingeben (z.B. von [openrouter.ai](https://openrouter.ai))
4. Modell auswählen
5. Verbindung testen

### Berechtigungen aktivieren
Für die volle Funktionalität:
- **Kamera** — Für Bildanalyse
- **Standort** — Für ortsbasierte Aktionen
- **Zugriffsdienst** — Für Chat-Overlay
- **Benachrichtigungs-Zugriff** — Für intelligente Antworten

---

## Projektstruktur

```
projekt-ki-agent-android/
├── .github/
│   ├── workflows/
│   │   └── android.yml           # CI/CD Pipeline
│   └── ISSUE_TEMPLATE/
│       ├── bug_report.md         # Bug Report Template
│       └── feature_request.md    # Feature Request Template
├── app/                          # Hauptanwendung
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/de/nexus/agent/
│       │   ├── NexusApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── ui/theme/
│       │   └── navigation/
│       └── res/
├── core/                         # Kernbibliothek
│   ├── build.gradle.kts
│   └── src/main/java/de/nexus/agent/core/
│       ├── AppModule.kt          # Hilt DI Module
│       ├── data/db/              # Room Datenbank
│       ├── data/model/           # Datenmodelle
│       ├── data/provider/        # LLM-Provider
│       ├── domain/agent/         # Agent-Loop & ToolRegistry
│       ├── domain/tools/         # Tool-Implementierungen
│       ├── domain/memory/        # Speichersystem
│       ├── domain/permissions/   # Berechtigungs-Hilfen
│       └── common/               # Utilities
├── feature-chat/                 # Chat-Funktion
├── feature-settings/             # Einstellungen
├── feature-overlay/              # Overlay-Services
│   └── src/main/res/xml/
│       └── accessibility_service_config.xml
├── agent-engine/                 # Agent-Orchestrierung
├── build.gradle.kts              # Root Build
├── settings.gradle.kts
├── gradle/libs.versions.toml     # Version Catalog
├── gradle.properties
├── gradlew.bat
├── .gitignore
└── README.md
```

---

## CI/CD

Das Projekt verwendet GitHub Actions für Continuous Integration. Bei jedem Push auf `main`/`develop` und bei Pull Requests werden automatisch ausgeführt:

1. **Lint** — Statische Code-Analyse (`./gradlew lintDebug`)
2. **Unit Tests** — JUnit-Tests (`./gradlew testDebugUnitTest`)
3. **Build** — Debug-APK wird gebaut (`./gradlew assembleDebug`)
4. **Artifact Upload** — Die APK wird als Build-Artefakt hochgeladen

---

## Mitwirken

Beiträge sind willkommen! So kannst du mithelfen:

1. **Forke** das Repository
2. Erstelle einen Feature-Branch (`git checkout -b feature/meine-funktion`)
3. **Committe** deine Änderungen (`git commit -m "feat: meine Funktion"`)
4. **Pushe** den Branch (`git push origin feature/meine-funktion`)
5. Erstelle einen **Pull Request**

### Code-Stil
- Kotlin Coding Conventions befolgen
- Alle öffentlichen Klassen und Funktionen mit KDoc dokumentieren
- Unit Tests für neue Features schreiben
- Lint-Fehler vor dem PR beheben

### Commit-Konvention
Wir folgen [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` — Neue Funktion
- `fix:` — Bugfix
- `docs:` — Dokumentation
- `refactor:` — Code-Refactoring
- `test:` — Tests hinzufügen/ändern
- `chore:` — Build, CI, Tooling

---

## Lizenz

Apache License 2.0 — Siehe [LICENSE](LICENSE) für details.

```
Copyright 2026 Nexus Agent Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
