# ⚡ Thor ROM Butler

[![CI](https://github.com/Strugglechen1337/ThorROMButler/actions/workflows/ci.yml/badge.svg)](https://github.com/Strugglechen1337/ThorROMButler/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Strugglechen1337/ThorROMButler?label=release)](https://github.com/Strugglechen1337/ThorROMButler/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-gold.svg)](#license)
[![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84.svg)](#installation)

**Deutsch** | **English**

## Screenshots

| Archive / Archive | Zuordnung / Review | Einstellungen / Settings |
|-------------------|--------------------|--------------------------|
| <img src="docs/screenshots/archive-scan.png" alt="Thor ROM Butler archive scan screen" width="300"> | <img src="docs/screenshots/review.png" alt="Thor ROM Butler review screen" width="300"> | <img src="docs/screenshots/settings.png" alt="Thor ROM Butler settings screen" width="300"> |

## Deutsch

**Der Butler für deine ROM-Sammlung.** Thor ROM Butler erkennt heruntergeladene
ROM-Archive auf deinem Android-Gerät, analysiert sie - ohne sie vollständig zu
entpacken -, bestimmt das Zielsystem und verschiebt sie in den richtigen ROM-Ordner
deiner EmulationStation-DE-Struktur.

Gebaut für Retro-Gaming-Handhelds wie **AYN Thor / Odin** und **Retroid Pocket**,
läuft aber auf jedem Android-Smartphone ab Android 13.

> Kostenlos & Open Source. Distribution über GitHub Releases - nicht im Play Store.

### Features

- 🔍 **Scanner**: findet ROM-Archive (ZIP, 7z, RAR4) **und lose ROM-Dateien**
  im Download-Ordner
- 🧠 **Detection Engine**: bestimmt das Zielsystem über Dateiendungen und Magic
  Bytes (inkl. ISO-, RVZ- und CHD-Header) - mit ehrlichen Confidence-Leveln
  (*sicher* / *wahrscheinlich* / *unbekannt*)
- 🛡️ **Keine Automatik bei Unklarheit**: Nur eindeutig erkannte ROMs bekommen
  einen Zielordner-Vorschlag. Du entscheidest immer selbst - einzeln oder mit
  "Alle übernehmen".
- 📦 **Archiv-Analyse ohne Entpacken**: Inhalte werden direkt im Archiv gelesen;
  `.bin`+`.cue` und `.m3u` werden als Einheit behandelt, BIOS-Dateien erkannt
  und ignoriert
- 🚚 **Einsortieren**: entpackt ROMs mit Fortschrittsbalken, Abbrechen-Option
  und CRC-Prüfung in den richtigen Systemordner - als Foreground-Service, der
  auch ausgeschaltete Displays übersteht. Duplikate werden erkannt (Ersetzen
  nur auf Wunsch), Speicherplatz wird vorab geprüft, vollständig verarbeitete
  Archive werden optional gelöscht.
- 🕹️ **Arcade-Sets bleiben gepackt**: MAME-/Neo-Geo-ZIPs werden als Ganzes
  nach `roms/arcade` bzw. `roms/neogeo` verschoben
- 🗂️ **ES-DE-Ordnerkonvention**: `roms/nes`, `roms/psx`, `roms/dreamcast/<Spiel>/`, ...
- 🔄 **Update-Check & In-App-Download** direkt aus den Einstellungen
- 📜 **Aktions-Log**: jede Bewegung wird protokolliert
- 🌍 Deutsch & Englisch · 🌙 **Thor-Design**: Dark Mode only, Neonblau & Gold,
  dezente Glow-Effekte

### Unterstützte Systeme

NES · SNES · Game Boy · Game Boy Color · Game Boy Advance · Nintendo 64 ·
Nintendo DS · Nintendo 3DS · PlayStation 1 · PlayStation 2 · PSP · GameCube ·
Wii · Wii U · Dreamcast · Switch · Amiga · C64 · Mega Drive · Master System ·
Game Gear · Saturn · Atari 2600 · Arcade (MAME) · Neo Geo

### Unterstützte Archive

| Format | Status |
|--------|--------|
| ZIP    | ✅ Lesen & Analysieren |
| 7z     | ✅ Lesen & Analysieren |
| RAR4   | ✅ Lesen & Analysieren |
| RAR5   | ⚠️ Wird erkannt, aber als "nicht unterstützt" gemeldet |

### Große Archive

Große Archive, auch mehrere Gigabyte, werden beim Einsortieren blockweise
geschrieben und nicht vollständig in den Arbeitsspeicher geladen. Entscheidend
sind freier Speicherplatz im Zielordner und die Kompressionsart.

Für sehr große ROMs ist **ZIP** am zuverlässigsten. **7z/LZMA2** funktioniert,
kann aber je nach Dictionary-Größe viel RAM brauchen. Wenn ein 7z-Archiv trotz
`largeHeap` scheitert, bitte am PC als ZIP oder als 7z mit kleinerem Dictionary
(z. B. 32 MB oder 64 MB) neu packen.

### Berechtigungen

Die App benötigt **"Verwaltung aller Dateien"** (`MANAGE_EXTERNAL_STORAGE`).
Das ist eine bewusste Entscheidung: ROM-Archive sind oft mehrere Gigabyte groß,
und die Analyse ohne Entpacken braucht schnellen wahlfreien Zugriff auf die
Archivdateien - das Storage Access Framework ist dafür zu langsam.

Internet nutzt die App ausschließlich für den **manuellen Update-Check**
(Einstellungen -> "Auf Updates prüfen", fragt die GitHub-Releases-API ab).
Es werden keinerlei Nutzungsdaten gesendet.

### Installation

1. Neueste APK von den [GitHub Releases](../../releases) herunterladen
2. APK installieren ("Unbekannte Quellen" erlauben)
3. Beim ersten Start den Berechtigungs-Dialog bestätigen und ROM-Basisordner wählen

Änderungen je Version stehen im [Changelog](CHANGELOG.md).

### Build

Voraussetzungen: JDK 21 und Android SDK (API 37).

```powershell
$env:JAVA_HOME = "D:\Dev\tools\jdk-21"; .\gradlew.bat assembleDebug
```

Die Debug-APK liegt danach unter `app/build/outputs/apk/debug/`.

### Rechtlicher Hinweis

Thor ROM Butler verwaltet nur Dateien, die sich bereits auf deinem Gerät befinden.
Die App enthält keine ROMs und stellt keine Download-Funktionen bereit. Bitte
verwende nur Sicherungskopien von Spielen, die du besitzt.

### Optional unterstützen

Thor ROM Butler bleibt kostenlos. Wenn dir die App hilft und du freiwillig Danke
sagen möchtest, kannst du mir gerne einen Kaffee ausgeben:
[paypal.me/marcelstrohmeyer](https://paypal.me/marcelstrohmeyer)

## English

**The butler for your ROM collection.** Thor ROM Butler detects downloaded ROM
archives on your Android device, analyzes them without fully extracting them,
identifies the target system, and moves them into the correct ROM folder in your
EmulationStation-DE structure.

Built for retro gaming handhelds such as **AYN Thor / Odin** and
**Retroid Pocket**, but also works on any Android phone running Android 13 or
newer.

> Free & open source. Distributed through GitHub Releases - not through the Play Store.

### Features

- 🔍 **Scanner**: finds ROM archives (ZIP, 7z, RAR4) **and loose ROM files**
  in the Downloads folder
- 🧠 **Detection engine**: identifies the target system from file extensions and
  magic bytes, including ISO, RVZ, and CHD headers, with honest confidence levels
  (*certain* / *probable* / *unknown*)
- 🛡️ **No automation when unclear**: only clearly identified ROMs receive a
  suggested target folder. You always decide what gets applied, one item at a
  time or in bulk.
- 📦 **Archive analysis without extraction**: archive entries are read directly;
  `.bin`+`.cue` and `.m3u` files are treated as one unit, while BIOS files are
  detected and ignored
- 🚚 **Sorting**: extracts ROMs into the correct system folder with progress,
  cancellation, and CRC checks. Work runs as a foreground service, duplicate
  files are detected, storage space is checked beforehand, and fully processed
  archives can optionally be deleted.
- 🕹️ **Arcade sets stay packed**: MAME and Neo Geo ZIPs are moved as complete
  sets to `roms/arcade` or `roms/neogeo`
- 🗂️ **ES-DE folder convention**: `roms/nes`, `roms/psx`, `roms/dreamcast/<game>/`, ...
- 🔄 **Update check & in-app download** directly from Settings
- 📜 **Action log**: every move is recorded
- 🌍 German & English · 🌙 **Thor design**: dark mode only, neon blue and gold,
  subtle glow effects

### Supported Systems

NES · SNES · Game Boy · Game Boy Color · Game Boy Advance · Nintendo 64 ·
Nintendo DS · Nintendo 3DS · PlayStation 1 · PlayStation 2 · PSP · GameCube ·
Wii · Wii U · Dreamcast · Switch · Amiga · C64 · Mega Drive · Master System ·
Game Gear · Saturn · Atari 2600 · Arcade (MAME) · Neo Geo

### Supported Archives

| Format | Status |
|--------|--------|
| ZIP    | ✅ Read & analyze |
| 7z     | ✅ Read & analyze |
| RAR4   | ✅ Read & analyze |
| RAR5   | ⚠️ Detected, but reported as unsupported |

### Large Archives

Large archives, including multi-gigabyte files, are written in chunks while
sorting and are not loaded fully into memory. The important limits are free
space in the target folder and the archive compression method.

For very large ROMs, **ZIP** is the safest option. **7z/LZMA2** works, but can
require a lot of RAM depending on dictionary size. If a 7z archive still fails
despite `largeHeap`, repack it on a PC as ZIP or as 7z with a smaller dictionary
(for example 32 MB or 64 MB).

### Permissions

The app requires **Manage all files** (`MANAGE_EXTERNAL_STORAGE`). This is a
deliberate choice: ROM archives are often several gigabytes in size, and
analyzing them without extraction needs fast random access to archive files.
The Storage Access Framework is too slow for this workflow.

Internet access is used only for the **manual update check** in Settings. The
app calls the GitHub Releases API and does not send analytics or user data.

### Installation

1. Download the latest APK from [GitHub Releases](../../releases)
2. Install the APK and allow installation from unknown sources when Android asks
3. On first launch, grant the file permission and choose your ROM base folder

Version-by-version changes are listed in the [changelog](CHANGELOG.md).

### Build

Requirements: JDK 21 and Android SDK (API 37).

```powershell
$env:JAVA_HOME = "D:\Dev\tools\jdk-21"; .\gradlew.bat assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/`.

### Legal Notice

Thor ROM Butler only manages files that are already present on your device. The
app does not include ROMs and does not provide any download functionality. Please
use only backup copies of games you own.

### Optional Support

Thor ROM Butler stays free. If the app helps you and you would like to say
thanks voluntarily, you can buy me a coffee:
[paypal.me/marcelstrohmeyer](https://paypal.me/marcelstrohmeyer)

## License

MIT
