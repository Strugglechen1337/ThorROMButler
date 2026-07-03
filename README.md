# ⚡ Thor ROM Butler

**Der Butler für deine ROM-Sammlung.** Thor ROM Butler erkennt heruntergeladene
ROM-Archive auf deinem Android-Gerät, analysiert sie – ohne sie vollständig zu
entpacken –, bestimmt das Zielsystem und verschiebt sie in den richtigen ROM-Ordner
deiner EmulationStation-DE-Struktur.

Gebaut für Retro-Gaming-Handhelds wie **AYN Thor / Odin** und **Retroid Pocket**,
läuft aber auf jedem Android-Smartphone ab Android 13.

> Kostenlos & Open Source. Distribution über GitHub Releases – nicht im Play Store.

## Features (v0.1)

- 🔍 **Download-Scanner**: findet ROM-Archive (ZIP, 7z, RAR4) im Download-Ordner
- 🧠 **Detection Engine**: bestimmt das Zielsystem über Dateiendungen und Magic Bytes
  – mit ehrlichen Confidence-Leveln (*sicher* / *wahrscheinlich* / *unbekannt*)
- 🛡️ **Keine Automatik bei Unklarheit**: Nur eindeutig erkannte ROMs bekommen einen
  Zielordner-Vorschlag. Du entscheidest immer selbst, was verschoben wird.
- 📦 **Archiv-Analyse ohne Entpacken**: Inhalte werden direkt im Archiv gelesen
- 🚚 **Einsortieren**: erkannte ROMs werden aus dem Archiv in den Zielordner
  entpackt; vollständig verarbeitete Archive werden aus dem Download-Ordner entfernt
- 🗂️ **ES-DE-Ordnerkonvention**: `roms/nes`, `roms/snes`, `roms/gba`, `roms/ps2`, …
- 🌙 **Thor-Design**: Dark Mode only, Neonblau & Gold, dezente Glow-Effekte

## Unterstützte Systeme (v0.1)

NES · SNES · Game Boy · Game Boy Color · Game Boy Advance · Nintendo 64 ·
Nintendo DS · Nintendo 3DS · PlayStation 1 · PlayStation 2 · PSP · GameCube ·
Wii · Wii U · Dreamcast · Switch

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
Die App sendet keinerlei Daten ins Internet.

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
