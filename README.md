# ⚡ Thor ROM Butler

**Der Butler für deine ROM-Sammlung.** Thor ROM Butler erkennt heruntergeladene
ROM-Archive auf deinem Android-Gerät, analysiert sie – ohne sie vollständig zu
entpacken –, bestimmt das Zielsystem und verschiebt sie in den richtigen ROM-Ordner
deiner EmulationStation-DE-Struktur.

Gebaut für Retro-Gaming-Handhelds wie **AYN Thor / Odin** und **Retroid Pocket**,
läuft aber auf jedem Android-Smartphone ab Android 13.

> Kostenlos & Open Source. Distribution über GitHub Releases – nicht im Play Store.

## Features

- 🔍 **Scanner**: findet ROM-Archive (ZIP, 7z, RAR4) **und lose ROM-Dateien**
  im Download-Ordner
- 🧠 **Detection Engine**: bestimmt das Zielsystem über Dateiendungen und Magic
  Bytes (inkl. ISO-, RVZ- und CHD-Header) – mit ehrlichen Confidence-Leveln
  (*sicher* / *wahrscheinlich* / *unbekannt*)
- 🛡️ **Keine Automatik bei Unklarheit**: Nur eindeutig erkannte ROMs bekommen
  einen Zielordner-Vorschlag. Du entscheidest immer selbst – einzeln oder mit
  „Alle übernehmen".
- 📦 **Archiv-Analyse ohne Entpacken**: Inhalte werden direkt im Archiv gelesen;
  `.bin`+`.cue` und `.m3u` werden als Einheit behandelt, BIOS-Dateien erkannt
  und ignoriert
- 🚚 **Einsortieren**: entpackt ROMs mit Fortschrittsbalken, Abbrechen-Option
  und CRC-Prüfung in den richtigen Systemordner – als Foreground-Service, der
  auch ausgeschaltete Displays übersteht. Duplikate werden erkannt (Ersetzen
  nur auf Wunsch), Speicherplatz wird vorab geprüft, vollständig verarbeitete
  Archive werden optional gelöscht.
- 🕹️ **Arcade-Sets bleiben gepackt**: MAME-/Neo-Geo-ZIPs werden als Ganzes
  nach `roms/arcade` bzw. `roms/neogeo` verschoben
- 🗂️ **ES-DE-Ordnerkonvention**: `roms/nes`, `roms/psx`, `roms/dreamcast/<Spiel>/`, …
- 🔄 **Update-Check & In-App-Download** direkt aus den Einstellungen
- 📜 **Aktions-Log**: jede Bewegung wird protokolliert
- 🌍 Deutsch & Englisch · 🌙 **Thor-Design**: Dark Mode only, Neonblau & Gold,
  dezente Glow-Effekte

## Unterstützte Systeme

NES · SNES · Game Boy · Game Boy Color · Game Boy Advance · Nintendo 64 ·
Nintendo DS · Nintendo 3DS · PlayStation 1 · PlayStation 2 · PSP · GameCube ·
Wii · Wii U · Dreamcast · Switch · Amiga · C64 · Mega Drive · Master System ·
Game Gear · Saturn · Atari 2600 · Arcade (MAME) · Neo Geo

## Unterstützte Archive

| Format | Status |
|--------|--------|
| ZIP    | ✅ Lesen & Analysieren |
| 7z     | ✅ Lesen & Analysieren |
| RAR4   | ✅ Lesen & Analysieren |
| RAR5   | ⚠️ Wird erkannt, aber als „nicht unterstützt" gemeldet |

## Berechtigungen

Die App benötigt **„Verwaltung aller Dateien"** (`MANAGE_EXTERNAL_STORAGE`).
Das ist eine bewusste Entscheidung: ROM-Archive sind oft mehrere Gigabyte groß,
und die Analyse ohne Entpacken braucht schnellen wahlfreien Zugriff auf die
Archivdateien – das Storage Access Framework ist dafür zu langsam.

Internet nutzt die App ausschließlich für den **manuellen Update-Check**
(Einstellungen → „Auf Updates prüfen", fragt die GitHub-Releases-API ab).
Es werden keinerlei Nutzungsdaten gesendet.

## Installation

1. Neueste APK von den [GitHub Releases](../../releases) herunterladen
2. APK installieren („Unbekannte Quellen" erlauben)
3. Beim ersten Start den Berechtigungs-Dialog bestätigen und ROM-Basisordner wählen

## Build

Voraussetzungen: JDK 17+, Android SDK (API 36).

```bash
./gradlew assembleDebug
```

Die Debug-APK liegt danach unter `app/build/outputs/apk/debug/`.

## Rechtlicher Hinweis

Thor ROM Butler verwaltet nur Dateien, die sich bereits auf deinem Gerät befinden.
Die App enthält keine ROMs und stellt keine Download-Funktionen bereit. Bitte
verwende nur Sicherungskopien von Spielen, die du besitzt.

## Lizenz

MIT
