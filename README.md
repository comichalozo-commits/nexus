# Nexus Agent — Android KI-Assistent

Nexus Agent ist eine native Android-App, die einen intelligenten KI-Assistenten direkt auf dem Smartphone bereitstellt. Die App unterstützt mehrere LLM-Provider (OpenRouter, Anthropic Claude, OpenAI GPT, Google Gemini) und bietet einen Agent-Loop mit Tool-Nutzung, Speicherverwaltung, geplanten Aufgaben und einem schwebenden Overlay-Chat.

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
| **Sprache** | Kotlin 2.1+ |
| **UI Framework** | Jetpack Compose mit Material 3 |
| **DI** | Dagger Hilt 2.52 |
| **Datenbank** | Room 2.6+ (SQLite) |
| **Netzwerk** | Retrofit 2.11 + OkHttp 4.12 |
| **Async** | Kotlin Coroutines + Flow |
| **Scheduling** | WorkManager 2.10 |
| **Serialization** | Kotlinx Serialization 1.7 |
| **Version Catalog** | Gradle `libs.versions.toml` |
| **Build System** | Kotlin DSL |
| **Min SDK** | 26 (Android 8.0) |
| **Target/Compile SDK** | 35 (Android 15) |
| **Java** | 17 |

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
- **Web-Abruf** — URL-Inhalte extrahieren
- **Dateizugriff** — Lesen/Schreiben auf dem Gerät
- **Shell-Befehle** — Eingeschränkte Shell-Ausführung
- **Speicher** — Fakten speichern und abrufen
- **Planung** — Geplante Aufgaben via WorkManager

### Overlay
- Schwebendes Chat-Overlay über anderen Apps
- AccessibilityService-basiert
- NotificationListener für intelligente Benachrichtigungen

### Datenschutz
- Alle Daten lokal auf dem Gerät gespeichert
- API-Keys nur lokal in SharedPreferences
- Keine Telemetrie oder Tracking

---

## Setup-Anleitung

### Voraussetzungen
- Android Studio Hedgehog (2023.1.1) oder neuer
- JDK 17
- Android SDK 35

### Projekte klonen
```bash
git clone <repository-url>
cd projekt-ki-agent-android
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

### Build & Run
```bash
# Debug Build
./gradlew assembleDebug

# Release Build
./gradlew assembleRelease

# Auf verbundenem Gerät starten
./gradlew installDebug
```

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

## Lizenz

MIT License — Siehe [LICENSE](LICENSE) für Details.

---

## Mitwirken

Beiträge sind willkommen! Bitte erstellen Sie einen Pull Request oder öffnen Sie ein Issue.
