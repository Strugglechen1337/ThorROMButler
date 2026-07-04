# How to use Thor ROM Butler / Anleitung

**Deutsch** | [English](#english)

## Deutsch

### Was macht die App?

Thor ROM Butler nimmt dir das Einsortieren von ROMs ab: Du legst neue Spiele
(als ZIP/7z/RAR oder lose Datei) in einen Ordner, die App erkennt das
Zielsystem und verschiebt sie in die richtige Ordnerstruktur für
EmulationStation-DE & Co. — z. B. `roms/gba`, `roms/psx`, `roms/dreamcast/…`

**Wichtigstes Prinzip:** Die App verschiebt **nie** etwas ohne deine
Bestätigung. Bei Unsicherheit entscheidest immer du.

### Schritt 1: Installieren & einrichten

1. Neueste APK von den [Releases](../../../releases) laden und installieren
   („Unbekannte Quellen" erlauben)
2. Beim ersten Start führt dich die App durch drei Schritte:
   - **Dateizugriff erlauben** (nötig, um Archive zu lesen und ROMs zu verschieben)
   - **Download-Ordner wählen** — hier landen deine neuen ROMs
     (z. B. `Download/`)
   - **ROM-Basisordner wählen** — der Ordner, der deine System-Unterordner
     enthält (z. B. `ROMs/` mit `gba/`, `psx/`, …). Darf auch auf der
     **SD-Karte** liegen! Fehlende Ordner kann die App selbst anlegen.

<img src="screenshots/setup.png" alt="Setup" width="600">

### Schritt 2: ROMs aufs Gerät bringen

Egal wie: Browser-Download, vom PC per USB kopiert, Telegram, … — Hauptsache,
die Dateien landen im gewählten Download-Ordner. Gepackt (ZIP, 7z, RAR4) oder
ungepackt, beides funktioniert. Weitere Quellordner kannst du in den
Einstellungen ergänzen.

### Schritt 3: Scannen

Die App durchsucht den Ordner und analysiert jeden Fund — bei Archiven
**ohne sie zu entpacken**. Auf jeder Karte siehst du, welche ROMs erkannt
wurden.

<img src="screenshots/scan.png" alt="Scan" width="600">

### Schritt 4: Zuordnung prüfen

Jedes ROM bekommt eine Einschätzung:

| Farbe | Bedeutung | Was du tun musst |
|-------|-----------|------------------|
| 🔵 **Sicher** | Eindeutig erkannt (Endung/Magic Bytes) | Nichts — Ziel ist vorbelegt |
| 🟡 **Wahrscheinlich** | Starker Hinweis, aber keine Garantie | Vorschlag antippen zum Bestätigen |
| ⚪ **Unbekannt** | Keine sichere Zuordnung möglich | System selbst wählen |

Mit „Alle übernehmen" bestätigst du alle gelben Vorschläge auf einmal.
Duplikate werden erkannt und übersprungen, außer du wählst „Ersetzen".

<img src="screenshots/assign.png" alt="Zuordnung" width="600">

### Schritt 5: Einsortieren

Ein Tipp auf **„n ROMs einsortieren"** — die App entpackt bzw. verschiebt
alles in die richtigen Systemordner (mit Fortschrittsbalken, auch bei
ausgeschaltetem Display). Vollständig verarbeitete Archive werden aus dem
Download-Ordner entfernt (abschaltbar, optional in einen Papierkorb).

Jede Aktion steht im **Log** — inklusive **Rückgängig**, falls du es dir
anders überlegst.

### Gut zu wissen

- **SD-Karte**: Downloads intern, ROMs auf SD? Kein Problem — die App kopiert
  verifiziert über Speichergrenzen hinweg.
- **Wächter-Modus** (Einstellungen): überwacht den Download-Ordner im
  Hintergrund und meldet neue Funde per Benachrichtigung.
- **Arcade/MAME-Sets** bleiben gepackt und werden als Ganzes verschoben.
- **Eigene Ordnernamen** (z. B. `ps1` statt `psx`): Einstellungen →
  „Systemordner anpassen".
- **Updates**: Einstellungen → „Auf Updates prüfen", oder via
  [Obtainium](https://github.com/ImranR98/Obtainium).

<img src="screenshots/settings.png" alt="Einstellungen" width="600">

---

## English

### What does the app do?

Thor ROM Butler sorts your ROMs for you: drop new games (as ZIP/7z/RAR or
loose files) into a folder, the app detects the target system and moves them
into the right folder structure for EmulationStation-DE & co. — e.g.
`roms/gba`, `roms/psx`, `roms/dreamcast/…`

**Core principle:** the app **never** moves anything without your
confirmation. When in doubt, you decide.

### Step 1: Install & set up

1. Download the latest APK from the [releases](../../../releases) and install
   it (allow unknown sources)
2. On first launch the app walks you through three steps:
   - **Allow file access** (needed to read archives and move ROMs)
   - **Choose the download folder** — where your new ROMs arrive
     (e.g. `Download/`)
   - **Choose the ROM base folder** — the folder containing your per-system
     subfolders (e.g. `ROMs/` with `gba/`, `psx/`, …). It can live on the
     **SD card**! Missing folders can be created by the app.

<img src="screenshots/setup.png" alt="Setup" width="600">

### Step 2: Get ROMs onto the device

Any way you like: browser download, copied from a PC over USB, Telegram, … —
as long as the files end up in your chosen download folder. Zipped (ZIP, 7z,
RAR4) or loose, both work. Additional source folders can be added in the
settings.

### Step 3: Scan

The app scans the folder and analyzes every find — archives are inspected
**without extracting them**. Each card shows which ROMs were detected.

<img src="screenshots/scan.png" alt="Scan" width="600">

### Step 4: Review

Every ROM gets an honest confidence rating:

| Color | Meaning | What you do |
|-------|---------|-------------|
| 🔵 **Certain** | Unambiguous (extension/magic bytes) | Nothing — target is prefilled |
| 🟡 **Probable** | Strong hint, no guarantee | Tap the suggestion to confirm |
| ⚪ **Unknown** | No reliable match | Pick the system yourself |

"Accept all" confirms every yellow suggestion at once. Duplicates are
detected and skipped unless you opt into replacing them.

<img src="screenshots/assign.png" alt="Review" width="600">

### Step 5: Sort

Tap **"Sort in n ROMs"** — the app extracts/moves everything into the right
system folders (with a progress bar, even with the screen off). Fully
processed archives are removed from the download folder (optional, can also
go to a trash folder instead).

Every action is recorded in the **log** — including **undo** if you change
your mind.

### Good to know

- **SD card**: downloads on internal storage, ROMs on SD? No problem — the
  app copies with verification across storage boundaries.
- **Watcher mode** (settings): monitors the download folder in the background
  and notifies you about new finds.
- **Arcade/MAME sets** stay zipped and are moved as a whole.
- **Custom folder names** (e.g. `ps1` instead of `psx`): Settings →
  "Customize system folders".
- **Updates**: Settings → "Check for updates", or via
  [Obtainium](https://github.com/ImranR98/Obtainium).

<img src="screenshots/settings.png" alt="Settings" width="600">
