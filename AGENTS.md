# Thor ROM Butler

Kostenlose Android-App für Retro-Gaming-Handhelds (AYN Thor/Odin, Retroid Pocket) und
Android-Smartphones. Erkennt heruntergeladene ROM-Archive, analysiert sie ohne
vollständiges Entpacken, bestimmt das Zielsystem und verschiebt sie in den richtigen
ROM-Ordner. Distribution über GitHub Releases, NICHT Play Store.

## Tech-Stack (verbindlich)

- Kotlin, Jetpack Compose, Material 3
- MVVM + Clean Architecture, Single-Module mit Package-Trennung (`domain`/`data`/`ui`/`di`)
- Hilt für DI (KSP), DataStore (Preferences) für Settings
- minSdk 33 (Android 13+), targetSdk/compileSdk 37 (aktuelle AndroidX-Libs erzwingen 37)
- Gradle Version Catalog (`gradle/libs.versions.toml`)
- Storage: `MANAGE_EXTERNAL_STORAGE` + direkter `java.io.File`-Zugriff
  (bewusste Entscheidung gegen SAF: Performance + Random Access für Archive)
- Archive: Apache Commons Compress (ZIP + 7z, XZ-Lib für LZMA2), junrar nur für
  RAR4-Lesen. RAR5 sauber als "nicht unterstützt" melden.

### Versions-Entscheidungen

- **AGP 9.2.1 mit Built-in Kotlin** (Hilt 2.60+ VERLANGT AGP 9; AGP 8.x ist keine
  Option mehr). Konsequenzen: kein `org.jetbrains.kotlin.android`-Plugin anwenden,
  kein `kotlinOptions`-Block; jvmTarget folgt `compileOptions.targetCompatibility`.
- Compose-Plugin (`org.jetbrains.kotlin.plugin.compose`) Version **2.3.10** = die von
  AGP 9.2 gebündelte Kotlin-Compiler-Version. Beim AGP-Update mit anheben!
- KSP 2.3.x (neues, von Kotlin entkoppeltes Versionsschema), Gradle 9.4.1
- Compose BOM 2026.06.01, Hilt 2.60
- Build-JDK: Temurin 21 unter `D:\Dev\tools\jdk-21` (kein Java im System-PATH!
  Vor Gradle-Aufrufen `$env:JAVA_HOME` setzen, siehe unten)

### Build-Kommando (Windows, PowerShell)

```powershell
$env:JAVA_HOME = "D:\Dev\tools\jdk-21"; .\gradlew.bat assembleDebug
```

Android SDK: `C:\Users\malle\AppData\Local\Android\Sdk` (via `local.properties`).

### Release & Signing

- Keystore: `signing/thor-release.jks` + `signing/keystore.properties`
  (BEIDE gitignored — Keystore unbedingt extern sichern, sonst sind keine
  App-Updates mehr möglich!)
- Lokal: `gradlew assembleRelease` liest `signing/keystore.properties`
- CI: `.github/workflows/release.yml` baut bei `v*`-Tags eine signierte APK
  und hängt sie ans GitHub-Release (Secrets: `SIGNING_KEYSTORE_BASE64`,
  `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`)
- Update-Check in der App: `GitHubUpdateChecker` (REPO_OWNER/REPO_NAME
  müssen zum GitHub-Repo passen)
- GitHub CLI: `D:\Dev\tools\gh\bin\gh.exe`

## Kern-Design der Detection Engine

- **SystemRegistry**: pro System ein Eintrag (Name, ES-DE-Ordnername, Extensions,
  optionale Magic-Byte-Regel). Neue Systeme = nur neuer Registry-Eintrag.
- **Confidence-Level**: `CERTAIN` / `PROBABLE` / `UNKNOWN`.
  Nur `CERTAIN` bekommt automatischen Zielordner-Vorschlag. NIEMALS automatisch
  verschieben bei Unklarheit – der Nutzer entscheidet.
- Mehrdeutige Extensions (`.iso` = PS2/PSP/GC/Wii, `.bin`, `.chd`) per Magic Bytes
  auflösen, sonst `UNKNOWN`.
- Multi-File-ROMs (`.bin`+`.cue`, `.m3u`) als Einheit behandeln.
- Systeme v0.1: NES, SNES, GB, GBC, GBA, N64, NDS, 3DS, PS1, PS2, PSP,
  GameCube, Wii, Wii U, Dreamcast, Switch. Ordnernamen nach ES-DE-Konvention
  (`roms/nes`, `roms/snes`, `roms/gba`, `roms/ps2`, ...).

## UI/Design

Dark Mode only. Schwarz/dunkelblauer Hintergrund, neonblaue Highlights, goldene
Akzente, dezente Glow-Effekte, große Touch-Buttons, Kartenansicht, flüssige
Animationen. Thema "Thor" (Blitz-Motiv, dezent).
Screens v0.1: Setup (Ordnerwahl + Berechtigung), Scan (Archivliste),
Review (Zuordnung/Vorschläge), Log.

## Arbeitsweise

- Meilensteine einzeln umsetzen, nach jedem Meilenstein `gradlew assembleDebug`
  ausführen und Fehler selbst fixen, dann Git-Commit.
- M0: Projekt-Setup, Gradle, Hilt, Thor-Theme, README, .gitignore
- M1: Ordnerwahl + DataStore-Persistenz + Berechtigungs-Flow
- M2: Download-Scanner + Archivliste (Kartenansicht)
- M3: Detection Engine + Unit-Tests
- M4: Archiv-Analyse (Inhalt lesen ohne Entpacken)
- M5: Review-UI + fehlende Ordner anlegen
- M6: Verschieben + Log + Fehlerbehandlung
- M7: Polish (Animationen, Glow, App-Icon)
- Code gut dokumentiert (KDoc), **deutsche UI-Texte, englische Code-Kommentare**.
- Aktuelle stabile Library-Versionen recherchieren, nicht raten.
- Bei jeder Änderung prüfen, ob README, GitHub-Release-Notes, Screenshots,
  Topics, Kurzbeschreibung oder andere GitHub-Metadaten angepasst werden müssen.
  Nutzerrelevante Features, Fixes, Einschränkungen und neue Releases immer auch
  auf GitHub/README nachvollziehbar dokumentieren.

## Projektstruktur

```
app/src/main/java/dev/thor/rombutler/
  ThorRomButlerApp.kt        # @HiltAndroidApp
  MainActivity.kt            # Single-Activity, Compose Navigation
  di/                        # Hilt-Module
  domain/                    #   model/  (SystemDefinition, Confidence, ...)
                             #   detection/ (SystemRegistry, DetectionEngine)
                             #   repository/ (Interfaces)
                             #   usecase/
  data/                      #   repository/ (Implementierungen)
                             #   archive/ (Commons-Compress/junrar-Reader)
                             #   settings/ (DataStore)
  ui/                        #   theme/  navigation/  setup/  scan/  review/  log/
                             #   components/ (gemeinsame Composables)
```
