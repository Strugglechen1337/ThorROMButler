# Changelog

All notable user-facing changes are documented here.

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
