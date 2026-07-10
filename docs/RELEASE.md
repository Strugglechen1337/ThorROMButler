# Release-Checkliste / Release Checklist

Thor ROM Butler wird über GitHub Releases verteilt, nicht über den Play Store.
Deutsch und Englisch sind ein verbindliches Release-Kriterium.

Thor ROM Butler is distributed through GitHub Releases, not the Play Store.
German and English are a mandatory release requirement.

## Vor jedem Release / Before every release

- Version und `versionCode` erhöhen / bump the version and `versionCode`
- `whatsnew_body` in `values-de` und `values` aktualisieren / update both locales
- README, HOWTO, Changelog und Release-Notes DE/EN prüfen / verify both languages
- GitHub-Beschreibung, Issue-Vorlagen und Screenshots prüfen / check repository metadata
- Update über die vorherige APK auf echter Hardware testen / test updating from the previous APK
- Scan, Importvorschau, Einsortieren, Log und eigene Systeme prüfen / run the smoke test

```powershell
$env:JAVA_HOME = "D:\Dev\tools\jdk-21"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

## Lokal signieren / Local signed release

```powershell
$env:JAVA_HOME = "D:\Dev\tools\jdk-21"
.\gradlew.bat assembleRelease
& "C:\Users\malle\AppData\Local\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --verbose app\build\outputs\apk\release\app-release.apk
```

Die Release-Notes liegen unter `docs/release-notes/vX.Y.Z.md`: zuerst Deutsch,
dann Englisch. / Release notes live at `docs/release-notes/vX.Y.Z.md`: German
first, then English.

```powershell
New-Item -ItemType Directory -Force -Path build\release | Out-Null
Copy-Item app\build\outputs\apk\release\app-release.apk build\release\ThorROMButler-vX.Y.Z.apk -Force
D:\Dev\tools\gh\bin\gh.exe release create vX.Y.Z build\release\ThorROMButler-vX.Y.Z.apk --repo Strugglechen1337/ThorROMButler --target main --title "Thor ROM Butler vX.Y.Z" --notes-file docs\release-notes\vX.Y.Z.md
```

## GitHub Actions

Für automatisch signierte Tag-Releases müssen diese Secrets gesetzt sein. /
These secrets are required for automatically signed tag releases:

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Ein gepushter `v*`-Tag baut die signierte APK und hängt sie an das GitHub-
Release. Ein manueller Workflow-Lauf ersetzt bei einem bestehenden Release nur
die APK und behält dessen Notes. / A pushed `v*` tag builds and attaches the
signed APK. A manual rerun replaces the APK of an existing release while keeping
its notes.

Der Keystore unter `signing/` ist absichtlich von Git ausgeschlossen und muss
extern gesichert sein. Ohne ihn sind keine Updates bestehender Installationen
möglich. / The keystore under `signing/` is intentionally ignored by Git and
must be backed up externally. Losing it prevents updates to existing installs.
