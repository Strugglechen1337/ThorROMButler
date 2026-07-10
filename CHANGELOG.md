# Changelog

All notable user-facing changes are documented here.

## 1.2.0

### Deutsch

- Versionierte System-Packs: Die eingebauten Systemdefinitionen liegen jetzt
  als geprüftes JSON-Schema v1 mit sicherem Code-Fallback vor.
- Eigene Systeme können in den Einstellungen angelegt, bearbeitet und entfernt
  sowie über `ThorRomButler-system-pack.json` importiert/exportiert werden.
- Pack-Größe, Felder, IDs, Zielordner, Endungen und Magic-Regeln werden vor der
  Aktivierung geprüft. Eingebaute Systeme können nicht überschrieben werden.
- Konfligierende Endungen werden sichtbar gewarnt und bleiben für die
  automatische Erkennung `UNKNOWN`.
- Eine Importvorschau zeigt Pack-Name, Systemanzahl und Konflikte, bevor eigene
  Systeme ersetzt werden.
- Einstellungs-Backups werden vollständig validiert und in einer atomaren
  DataStore-Transaktion übernommen; unsichere Zielordner werden abgelehnt.
- GitHub-Beschreibung und Issue-Vorlagen sind durchgehend Deutsch/Englisch;
  Compose-Tests sichern Pack-Vorschau und Editor-Validierung ab.

### English

- Versioned system packs: built-in system definitions now use a validated JSON
  schema v1 with a safe code fallback.
- Custom systems can be created, edited, and removed in Settings, then imported
  or exported through `ThorRomButler-system-pack.json`.
- Pack size, fields, IDs, target folders, extensions, and magic rules are
  validated before activation. Built-in systems cannot be overwritten.
- Conflicting extensions are shown as warnings and remain `UNKNOWN` for
  automatic detection.
- An import preview shows the pack name, system count, and conflicts before
  custom systems are replaced.
- Settings backups are validated completely and applied in one atomic DataStore
  transaction; unsafe target folders are rejected.
- The GitHub description and issue forms are fully bilingual; Compose tests
  cover the pack preview and editor validation.

## 1.1.1

### Deutsch

- Sicheres Ersetzen: Neue ROM-Dateien werden zunächst vollständig in eine
  versteckte Teildatei geschrieben und geprüft. Vorhandene Dateien bleiben bis
  zum erfolgreichen Abschluss gesichert und werden bei Fehlern wiederhergestellt.
- Quellarchive bleiben bei neuen Installationen standardmäßig erhalten.
  „Rückgängig“ erkennt Archive nun auch im siebentägigen Papierkorb.
- LAN-Empfang mit zufälliger Sitzungsadresse, atomarer Dateiübernahme,
  Speicherplatzprüfung und automatischem Ende nach 30 Minuten.
- Geteilte Dateien werden mit bereinigten Dateinamen und atomar übernommen.
- Android Lint ist fehlerfrei und läuft jetzt verpflichtend in der CI.

### English

- Safe replacement: new ROM files are fully written to a hidden partial file
  and verified first. Existing targets stay backed up until commit and are
  restored after failures.
- Source archives are kept by default on new installations. Undo now also
  recognizes archives in the seven-day trash folder.
- LAN receive now uses a random session address, atomic file commits, free-space
  checks, and an automatic 30-minute timeout.
- Shared files are imported atomically with sanitized file names.
- Android Lint is error-free and now runs as a required CI check.

## 1.1.0

- Added a Quick Settings tile for the LAN receive mode: toggle receiving
  from the notification shade without opening the app; when active, the
  tile shows the upload URL.
- Dependency maintenance (XZ 1.12) and faster builds via Gradle
  configuration cache.
- New social preview image for the GitHub repo.

## 1.0.1

- Much faster analysis of solid 7z archives: header prefixes are now read
  in a single pass instead of reopening (and re-decompressing) the archive
  per entry.
- Archives are analyzed two at a time, and a 90-second timeout per archive
  ensures one broken/pathological file no longer stalls the whole scan.

## 1.0.0

- Added gamepad controls: D-pad/stick focus navigation across all screens,
  A confirms, B goes back — fully additive, touch keeps working everywhere.
- Added color themes: Thor (blue/gold), Odin (violet/silver) and
  CRT (phosphor green/amber); glow effects follow the theme.
- Accessibility audit: all interactive elements labeled for TalkBack.

## 0.10.0

- Added LAN receive mode: a local upload page served by the app — open
  the shown address in a PC browser (same Wi-Fi) and drop ROMs straight
  into the download folder. Runs as a foreground service with a stop
  action; files are never overwritten.

## 0.9.0

- Added DAT verification: sorted ROMs are checked by CRC32 against
  user-provided No-Intro/Redump .dat files (Settings → DAT folder); the
  verdict is recorded in the log.
- Added a 1G1R duplicate overview to the library check: same titles in
  multiple regions/revisions are listed (details in the collection export).

## 0.8.1

- Storage preflight now warns in red and blocks sorting when space is
  insufficient.
- The scan screen detects a revoked all-files permission and offers to
  re-grant it instead of showing an empty state.
- One-time what's-new dialog after app updates.
- Share target: send files from other apps straight to the butler.
- Collection export as Markdown from the library check.

## 0.8.0

- Added adaptive two-column layout for scan and review on wide/landscape
  screens (AYN Thor & co.).
- Added support for multiple source folders in addition to the download
  folder.
- Added optional trash mode: processed archives move to a hidden trash
  folder (auto-purged after 7 days) instead of being deleted.
- Added settings backup: export/import all settings as JSON via the
  download folder.
- Added per-app language switch (Android 13+ system settings).
- Added the missing MIT LICENSE file and GitHub repository topics.

## 0.7.0

- Added optional watcher mode: periodic background check of the download
  folder with a notification for new finds (sorting still needs your
  confirmation).
- Added library check: per-system statistics plus detection of misplaced
  ROMs, which can be re-sorted through the normal review flow.
- Butler-flavored empty state and SD-card FAQ in the README.

## 0.6.0

- Added undo for sorted ROMs directly from the log (extracted files are
  removed while the source archive still exists; moved files go back).
- Added per-system folder overrides in Settings for non-ES-DE frontends
  (e.g. `roms/ps1` instead of `roms/psx`).
- Added folder-name detection hints: ambiguous files inside folders like
  `SNES/` or `PS2/` now get a "probable" suggestion.
- Added a local, privacy-friendly crash report that can be shared manually.
- Review list now sorts items needing decisions first and shows a
  confidence color legend.
- Added a battery-optimization shortcut in Settings for long sorting runs.

## 0.5.0

- Added 8 systems: Atari 7800, Atari Lynx, PC Engine/TurboGrafx-16, Sega 32X,
  Neo Geo Pocket (Color), WonderSwan (Color).
- Added "create new folder" inside the folder picker.
- Added opt-in automatic update check on app start (badge on the settings icon).
- Fixed the in-app update check to use the renamed GitHub repository.
- Notification permission is now also requested on the review screen for
  existing installations.
- Updated the empty-scan hint to mention loose ROM files.

## 0.4.0

- Added retry flow for failed sorting jobs; failed ROMs remain in review and can be started again.
- Added storage preflight hints before sorting, including a 7z RAM warning for large archives.
- Added log sharing/export from the Log screen.
- Added optional support link in Settings and GitHub funding metadata.
- Added GitHub issue templates and release documentation.
- Added README badges and large-archive guidance.

## 0.3.1

- Fixed 7z/LZMA2 extraction memory handling for large archives.
- Enabled Android `largeHeap` for archive extraction workloads.
- Improved cleanup and user-facing log messages when extraction runs out of memory.

## 0.3.0

- Added CHD detection.
- Added Arcade and Neo Geo handling that keeps ROM sets packed.
- Added in-app update check and download.
- Added English app translation.
- Expanded supported systems and archive handling.

## 0.2.0

- Added loose ROM file handling.
- Added duplicate detection and replace-on-request behavior.
- Added foreground extraction service.
- Added settings for deleting processed archives.
