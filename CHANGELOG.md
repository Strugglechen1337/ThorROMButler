# Changelog

All notable user-facing changes are documented here.

## 1.8.0

### Deutsch

- Neuer Bibliotheks-Doktor als eigener Bildschirm: Ein Lauf bündelt
  Sammlungsstatistik, falsch einsortierte ROMs, Varianten, BIOS-/DAT-Status
  und Sicherungsbereitschaft in einer ruhigen, handlungsorientierten Ansicht.
- `.m3u`-Playlists und `.cue`-Dateien werden auf leere, unlesbare oder fehlende
  Verweise geprüft. Relative Unterordner, Kommentare, URLs sowie zitierte und
  einfache CUE-Dateinamen werden korrekt behandelt.
- Gepackte Arcade- und Neo-Geo-ZIPs werden über ihr Verzeichnis geprüft, ohne
  Inhalte zu entpacken. Diese Sets zählen jetzt außerdem korrekt in Statistik
  und SHA-256-Duplikatprüfung.
- Befunde bieten Details und können für den aktuellen Lauf ausgeblendet
  werden. Eindeutige Fehlzuordnungen gehen in die bestehende Review-Reparatur;
  eine fällige konfigurierte ROM-Sicherung lässt sich direkt starten.
- Der zweisprachige Markdown-Gesundheitsbericht enthält alle Befunde und auf
  Wunsch die Ergebnisse der separaten SHA-256-Tiefenprüfung. Die App löscht
  weiterhin nichts automatisch und erfindet keine fehlenden Disc-Dateien.

### English

- New dedicated Library Doctor screen: one run combines collection statistics,
  misplaced ROMs, variants, BIOS/DAT status, and backup readiness in a calm,
  action-oriented view.
- `.m3u` playlists and `.cue` sheets are checked for empty, unreadable, or
  missing references. Relative subfolders, comments, URLs, and both quoted and
  simple CUE file names are handled correctly.
- Packed Arcade and Neo Geo ZIPs are checked through their directory without
  extracting content. These sets are now also counted correctly in statistics
  and the SHA-256 duplicate scan.
- Findings provide details and can be hidden for the current run. Certain
  misplacements open the existing review repair; a due configured ROM backup
  can be started directly.
- The bilingual Markdown health report includes every finding and optionally
  the separate SHA-256 deep-scan results. The app still deletes nothing
  automatically and never invents missing disc files.

## 1.7.3

### Deutsch

- Aufräumarbeiten nach einem Code-Review: Beim „Rückgängig" eines
  Multi-Disc-Spiels wird jetzt auch die automatisch erzeugte
  `.m3u`-Playlist entfernt, statt als Leiche zurückzubleiben.
- Per „Teilen → Thor ROM Butler" gesendete Dateien mit gleichem Namen wie
  eine vorhandene Datei bekommen jetzt ein `(1)`-Suffix, statt still
  verworfen zu werden.
- Abgebrochene Archiv-Analysen (z. B. beim erneuten Scannen) werden früher
  gestoppt und laufen nicht mehr unnötig im Hintergrund weiter.

### English

- Cleanup after a code review: undoing a multi-disc game now also removes
  the auto-generated `.m3u` playlist instead of leaving it dangling.
- Files sent via "Share → Thor ROM Butler" that share a name with an
  existing file now get a `(1)` suffix instead of being silently dropped.
- Cancelled archive analyses (e.g. on rescan) are stopped sooner and no
  longer keep churning in the background.

## 1.7.2

### Deutsch

- Fehler behoben, durch den der goldene „einsortiert!"-Blitz nach dem
  Einsortieren hängen blieb und sich nicht wegtippen ließ — die App musste
  geschlossen werden. Der Erfolgs-Moment verschwindet jetzt von selbst und
  lässt sich zusätzlich durch Antippen sofort schließen.
- Absturz bei großen Ordnern behoben: Ein Archiv, dessen Analyse den
  Arbeitsspeicher sprengt (sehr große 7z/LZMA2-Kompression), wird jetzt
  sauber als fehlgeschlagen angezeigt, statt die ganze App zu beenden. Ein
  einzelnes problematisches Archiv bleibt eine Fehlerkarte — der Rest des
  Scans läuft weiter.

### English

- Fixed a bug where the golden "sorted in!" bolt froze after sorting and
  couldn't be tapped away — the app had to be closed. The success moment
  now dismisses itself and can also be tapped to close instantly.
- Fixed a crash on large folders: an archive whose analysis exhausts memory
  (very large 7z/LZMA2 compression) is now cleanly reported as failed
  instead of taking down the whole app. A single problematic archive stays
  one error card — the rest of the scan continues.

## 1.7.1

### Deutsch

- Der BIOS-Ordner schlägt sich jetzt selbst vor: Die App durchsucht die
  üblichen Orte (BIOS/-Ordner auf allen Speichern, RetroArch/system,
  bios/ neben dem ROM-Ordner) und bevorzugt Ordner, die bereits bekannte
  BIOS-Dateien enthalten. Ein Tipp auf „Übernehmen" genügt; existiert noch
  nichts, wird ein BIOS-Ordner neben dem ROM-Ordner angelegt.
- Core-Konventionen automatisch: Dreamcast-BIOS (dc_boot/dc_flash/naomi)
  landet ab jetzt im Unterordner dc/, wie Flycast es erwartet.

### English

- The BIOS folder now suggests itself: the app checks the usual places
  (BIOS/ folders on all volumes, RetroArch/system, bios/ next to the ROM
  folder) and prefers folders that already contain known BIOS files. One
  tap on "Apply" is enough; if nothing exists yet, a BIOS folder is
  created next to the ROM folder.
- Core conventions handled automatically: Dreamcast BIOS
  (dc_boot/dc_flash/naomi) now lands in the dc/ subfolder as expected by
  Flycast.

## 1.7.0

### Deutsch

- BIOS-Einsortierung: Erkannte BIOS-/Firmware-Dateien (PS1 scph*, GBA/NDS,
  Dreamcast, Kickstart, syscard, …) bekommen im Scan eine eigene Karte und
  wandern mit einem Tipp in einen frei wählbaren BIOS-Ordner (z. B. den
  System-Ordner des Emulators).
- Nichts wird überschrieben (Namenskollisionen bekommen ein `(1)`-Suffix),
  der Lauf steht als eine Aktion im Log und lässt sich rückgängig machen.
- Neuer optionaler BIOS-Ordner in den Einstellungen neben dem DAT-Ordner.

### English

- BIOS sorting: detected BIOS/firmware files (PS1 scph*, GBA/NDS,
  Dreamcast, Kickstart, syscard, …) get their own card in the scan and
  move to a freely chosen BIOS folder with one tap (e.g. your emulator's
  system folder).
- Nothing is overwritten (name collisions get a `(1)` suffix), the run is
  logged as one action and can be undone.
- New optional BIOS folder in Settings next to the DAT folder.

## 1.6.0

### Deutsch

- Design-Release: neue Display-Schrift (Chakra Petch, SIL OFL) für alle
  Überschriften, modernisierte Formen-Skala mit großzügigeren Eckenradien.
- Einstellungen entrümpelt: lange Erklärtexte sind hinter ⓘ-Icons
  einklappbar — jede Karte ist auf einen Blick erfassbar, alle Details
  bleiben einen Tipp entfernt.
- Federnde Animationen: Screen-Übergänge gleiten mit Spring-Physik, der
  Gamepad-Fokus poppt sanft, und nach einem fehlerfreien Einsortieren
  erscheint ein goldener Blitz-Moment („N ROMs einsortiert!").
- Scan-Screen: pulsierender Blitz während des Scans und ein schwebendes
  Blitz-Badge im „Alles aufgeräumt"-Zustand.

### English

- Design release: new display font (Chakra Petch, SIL OFL) for all
  headlines, modernized shape scale with more generous corner radii.
- Decluttered settings: long explanations collapse behind ⓘ icons —
  every card is scannable at a glance, all details stay one tap away.
- Springy animations: screen transitions glide with spring physics, the
  gamepad focus pops gently, and a golden bolt moment ("N ROMs sorted
  in!") celebrates a clean sorting run.
- Scan screen: pulsing bolt while scanning and a floating bolt badge in
  the all-tidied-up state.

## 1.5.0

### Deutsch

- Neue ROM-Sicherung: Das komplette ROM-Verzeichnis mit allen Systemen wird
  als 1:1-Ordnerspiegel in ein frei wählbares Ziel gesichert (SD-Karte, USB,
  beliebiger Ordner). Folgeläufe sind inkrementell und kopieren nur Neues.
- Wiederherstellung eingebaut: Fehlende Dateien werden aus der Sicherung
  zurückkopiert — vorhandene Dateien in der Bibliothek werden dabei nie
  überschrieben.
- Läuft als Foreground-Service mit Fortschritts-Benachrichtigung und
  Abbrechen; ein Abbruch behält bereits kopierte Dateien (nächster Lauf
  setzt inkrementell fort). Speicherplatz wird vorab geprüft.
- Ein Manifest an der Sicherung zeigt Datum, Dateizahl und Größe der
  letzten Sicherung direkt in den Einstellungen.

### English

- New ROM library backup: the complete ROM folder with all systems is
  mirrored 1:1 into a target of your choice (SD card, USB, any folder).
  Follow-up runs are incremental and only copy what is new.
- Restore built in: missing files are copied back from the backup — files
  already in the library are never overwritten.
- Runs as a foreground service with progress notification and cancel;
  cancelling keeps completed files (the next run continues incrementally).
  Free space is checked upfront.
- A manifest at the backup shows date, file count, and size of the last
  backup right in Settings.

## 1.4.2

### Deutsch

- GameCube- und Wii-Abbilder im CISO-Format werden jetzt anhand ihres
  Container- und Disc-Headers automatisch dem richtigen System zugeordnet.
- Die Erkennung arbeitet direkt auf dem Dateianfang und funktioniert daher
  auch für CISO-Dateien innerhalb von ZIP-, 7z- und RAR4-Archiven, ohne das
  komplette Spiel vorab in den Arbeitsspeicher zu laden.
- Fehlt eine gültige GameCube- oder Wii-Kennung, bleibt die Zuordnung wie
  vorgesehen `UNKNOWN`; die App entscheidet in diesem Fall nicht eigenmächtig.

### English

- GameCube and Wii images in CISO format are now assigned automatically by
  inspecting both the container and embedded disc headers.
- Detection reads only the file prefix, so it also works for CISO files inside
  ZIP, 7z, and RAR4 archives without loading the complete game into memory.
- If no valid GameCube or Wii marker is present, assignment remains `UNKNOWN`;
  the app still leaves the decision to the user.

## 1.4.1

### Deutsch

- Fehler behoben, durch den ein lesbares Archiv ohne automatisch erkannte
  ROM-Endung nach manueller Systemwahl als komplette Datei in den ROM-Ordner
  verschoben wurde. Betroffen waren insbesondere 7z-Archive mit unbekannten
  oder eigenen Dateiendungen.
- Der Analyzer behält solche internen Dateien jetzt für die manuelle Zuordnung
  als entpackbare Archiveinträge bei. 7z- und RAR4-Archive werden dadurch auch
  in diesem Fallback-Fall regulär entpackt.
- Die Ausnahme ist jetzt eng gefasst: Nur ein ZIP, das ausdrücklich Arcade
  oder Neo Geo zugeordnet wurde, bleibt als vollständiges ROM-Set gepackt.

### English

- Fixed a bug where a readable archive without an automatically recognized ROM
  extension was moved as a complete file after manual system assignment. This
  particularly affected 7z archives with unknown or custom file extensions.
- The analyzer now preserves those internal files as extractable archive
  entries for manual assignment. 7z and RAR4 archives are therefore extracted
  normally in this fallback case as well.
- The exception is now deliberately narrow: only a ZIP explicitly assigned to
  Arcade or Neo Geo remains packed as a complete ROM set.

## 1.4.0

- Added a ROM patcher for IPS/UPS/BPS patches (fan translations,
  romhacks): drop the patch next to the ROM with the same file name and
  apply it with one tap — the original stays untouched, checksums are
  verified where the format provides them (UPS/BPS).
- Multi-disc games now get an automatic `.m3u` playlist when several
  discs land in a system folder (existing playlists are never touched;
  can be disabled in Settings).
- New opt-in "Rename to DAT names": verified single-file ROMs are renamed
  to their canonical No-Intro/Redump name when sorted in.
- Added frontend profiles: switch all system folder names to the
  Batocera/Knulli or Onion OS (Miyoo) convention in one step — ES-DE
  remains the default.

## 1.3.0

### Deutsch

- Der LAN-Empfang überträgt Dateien jetzt in fortsetzbaren Blöcken. Die
  Browser-Warteschlange zeigt pro Datei Fortschritt, Geschwindigkeit und
  Restzeit und bietet Abbrechen sowie Wiederholen. Unterbrochene große Uploads
  werden nach erneuter Auswahl derselben Datei fortgesetzt.
- Unvollständige Uploads bleiben in einem versteckten Arbeitsordner und werden
  erst nach vollständiger Übertragung atomar im Download-Ordner sichtbar.
  Verwaiste Teildateien werden nach sieben Tagen entfernt.
- Die Sitzungsansicht zeigt einen lokal erzeugten QR-Code und bietet Kopieren,
  Teilen sowie eine Verbindungsprüfung für Berechtigung, Netzwerk, LAN-Adresse,
  Zielordner und Server.
- Die Bibliotheks-Prüfung kann optional gleich große ROM-Dateien per
  Streaming-SHA-256 vergleichen. Bytegleiche Gruppen, Prüfsummen und das
  mögliche Einsparpotenzial werden angezeigt und im Markdown-Export ergänzt;
  die App löscht dabei nichts.
- Ein freiwillig teilbarer Diagnosebericht erfasst App-, Android-, Geräte-,
  Speicher-, Berechtigungs- und LAN-Status. ROM-Namen und geschützte
  Sitzungscodes werden bewusst ausgelassen.

### English

- LAN receive now transfers files in resumable chunks. The browser queue shows
  per-file progress, speed, and ETA with cancel and retry actions. Interrupted
  large uploads continue when the same file is selected again.
- Incomplete uploads stay in a hidden working directory and become visible in
  Downloads only after an atomic final commit. Stale partial files are removed
  after seven days.
- The session view displays a locally generated QR code and adds copy, share,
  and connection checks for permission, network, LAN address, target folder,
  and server reachability.
- The library check can optionally compare same-sized ROM files with streaming
  SHA-256. Byte-identical groups, hashes, and reclaimable space are shown and
  added to the Markdown export; the app never deletes them automatically.
- A voluntarily shared diagnostic report captures app, Android, device,
  storage, permission, and LAN status. ROM names and protected session codes
  are deliberately omitted.

## 1.2.2

### Deutsch

- Die Schnelleinstellungs-Kachel öffnet jetzt eine kompakte Anzeige mit der
  vollständigen, zufälligen Sitzungsadresse. Das funktioniert auf allen von der
  App unterstützten Android-Versionen ab Android 13.
- Die Adresse ist auswählbar und kann kopiert werden; der Empfang lässt sich im
  selben Fenster beenden. Ein erneuter Tipp auf die aktive Kachel zeigt die
  Adresse wieder an.
- Der sechsstellige Sitzungscode steht zusätzlich direkt im sichtbaren
  Kachelnamen, auch wenn die Android-Oberfläche den Untertitel ausblendet.

### English

- The Quick Settings tile now opens a compact view with the complete random
  session address on every supported Android version starting with Android 13.
- The address is selectable and can be copied; receiving can be stopped in the
  same window. Tapping an active tile again shows the address again.
- The six-character session code is also included in the visible tile label,
  even when the Android skin hides tile subtitles.

## 1.2.1

### Deutsch

- LAN-Empfang unter Android 17 repariert: Die dort erforderliche
  `ACCESS_LOCAL_NETWORK`-Laufzeitberechtigung wird erst angefragt, wenn der
  Nutzer den Empfang ausdrücklich startet.
- Der Berechtigungsfluss funktioniert sowohl in den Einstellungen als auch
  über die Schnelleinstellungs-Kachel. Ohne Freigabe wird kein LAN-Server
  gestartet.
- Ein automatischer Server-Test prüft geschützte Sitzungsadresse,
  Multipart-Upload und die unveränderte Dateiübernahme.

### English

- Fixed LAN receive on Android 17: the required `ACCESS_LOCAL_NETWORK` runtime
  permission is requested only when the user explicitly starts receiving.
- Permission handling works from both Settings and the Quick Settings tile. No
  LAN server is started without access.
- An automated server test covers the protected session address, multipart
  upload, and byte-identical file storage.

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
