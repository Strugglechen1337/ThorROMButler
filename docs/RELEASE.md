# Release Checklist

Thor ROM Butler is distributed through GitHub Releases, not the Play Store.

## Local Signed Release

Use this when signing secrets are not configured on GitHub yet.

```powershell
$env:JAVA_HOME = "D:\Dev\tools\jdk-21"; .\gradlew.bat testDebugUnitTest
$env:JAVA_HOME = "D:\Dev\tools\jdk-21"; .\gradlew.bat assembleRelease
```

Verify the APK:

```powershell
$env:JAVA_HOME = "D:\Dev\tools\jdk-21"
& "C:\Users\malle\AppData\Local\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --verbose app\build\outputs\apk\release\app-release.apk
```

Create the GitHub release with the signed APK:

```powershell
New-Item -ItemType Directory -Force -Path build\release | Out-Null
Copy-Item app\build\outputs\apk\release\app-release.apk build\release\ThorROMButler-vX.Y.Z.apk -Force
D:\Dev\tools\gh\bin\gh.exe release create vX.Y.Z build\release\ThorROMButler-vX.Y.Z.apk --repo Strugglechen1337/ThorROMButler --target main --title "Thor ROM Butler vX.Y.Z" --notes-file CHANGELOG.md
```

## GitHub Actions Signing

For fully automatic tag releases, configure these repository secrets:

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Then a pushed `v*` tag can build a signed APK and attach it to the GitHub Release.
The same workflow can also be started manually from GitHub Actions with a release
tag such as `v0.4.0`.

The local keystore in `signing/` is intentionally ignored by Git. Keep an external
backup; losing the keystore means existing users cannot update to future builds.
