# System-Pack-Schema v1

## Deutsch

Thor ROM Butler kann genau ein eigenes System-Pack zusätzlich zu den
eingebauten Systemen aktivieren. Packs werden **nur lokal** über
`ThorRomButler-system-pack.json` im festgelegten Download-Ordner importiert und
exportiert. Die App lädt keine Packs automatisch aus dem Internet.

### Beispiel

```json
{
  "schemaVersion": 1,
  "packId": "user.example",
  "displayName": "Meine Systeme",
  "systems": [
    {
      "id": "exampleconsole",
      "displayName": "Example Console",
      "folder": "exampleconsole",
      "extensions": {
        "exrom": "CERTAIN"
      },
      "gameSubfolder": false
    }
  ]
}
```

### Felder und Grenzen

| Feld | Regel |
|------|-------|
| `schemaVersion` | Muss `1` sein |
| `packId` | 3–64 Zeichen: Kleinbuchstaben, Ziffern, `.`, `_`, `-` |
| `displayName` | 1–80 sichtbare Zeichen |
| `systems` | 1–128 Definitionen |
| `id` | 1–32 Zeichen: Kleinbuchstaben, Ziffern, `_`, `-` |
| `folder` | 1–64 sichere Ordnerzeichen; keine Pfadtrenner oder `..` |
| `extensions` | Objekt aus Endung ohne Punkt und `CERTAIN`, `PROBABLE` oder `UNKNOWN` |
| `gameSubfolder` | Optionales Boolean; Standard `false` |
| `magicRuleIds` | Optional; ausschließlich von der App bekannte Regel-IDs |

Die Datei darf höchstens 1 MB groß sein. IDs und Zielordner müssen im Pack
eindeutig sein und dürfen keine eingebauten Systeme überschreiben. Mehrfach als
`CERTAIN` beanspruchte Endungen innerhalb eines Packs werden abgelehnt.

Wenn eine eigene Definition dieselbe Endung wie ein eingebautes System
beansprucht, zeigt die App einen Konflikt. Die Detection Engine liefert für
diese Endung `UNKNOWN`; der Nutzer wählt das Ziel selbst. Magic-Regeln sind
keine ausführbaren Skripte: Ein Pack kann nur auf bereits in der App enthaltene,
kontrollierte Regeln verweisen.

Der Editor in der App erstellt sichere, endungsbasierte Definitionen. Für ein
Pack mit `PROBABLE`, `UNKNOWN` oder einer bekannten `magicRuleId` kann die JSON-
Datei extern bearbeitet und anschließend importiert werden.

Bekannte Regel-IDs in Schema v1: `nes-ines`, `n64-native`, `chd-cd`, `chd-dvd`,
`playstation-iso`, `psp-iso`, `gamecube-disc`, `gamecube-rvz`, `wii-disc`,
`wii-rvz`, `dreamcast-disc`, `megadrive`, `genesis`, `saturn-disc`.

---

## English

Thor ROM Butler can activate one custom system pack in addition to its built-in
systems. Packs are imported and exported **locally only** as
`ThorRomButler-system-pack.json` in the selected download folder. The app never
downloads packs automatically.

### Example

```json
{
  "schemaVersion": 1,
  "packId": "user.example",
  "displayName": "My systems",
  "systems": [
    {
      "id": "exampleconsole",
      "displayName": "Example Console",
      "folder": "exampleconsole",
      "extensions": {
        "exrom": "CERTAIN"
      },
      "gameSubfolder": false
    }
  ]
}
```

### Fields and limits

| Field | Rule |
|-------|------|
| `schemaVersion` | Must be `1` |
| `packId` | 3–64 characters: lowercase letters, digits, `.`, `_`, `-` |
| `displayName` | 1–80 visible characters |
| `systems` | 1–128 definitions |
| `id` | 1–32 characters: lowercase letters, digits, `_`, `-` |
| `folder` | 1–64 safe folder characters; no path separators or `..` |
| `extensions` | Object mapping dotless extensions to `CERTAIN`, `PROBABLE`, or `UNKNOWN` |
| `gameSubfolder` | Optional Boolean; defaults to `false` |
| `magicRuleIds` | Optional; only rule IDs already known to the app |

The file may be at most 1 MB. IDs and target folders must be unique inside the
pack and cannot overwrite built-in systems. An extension claimed as `CERTAIN`
by multiple systems in one pack is rejected.

If a custom definition claims the same extension as a built-in system, the app
shows a conflict. The detection engine returns `UNKNOWN` for that extension and
the user chooses the target. Magic rules are not executable scripts: a pack can
only reference controlled rules already shipped inside the app.

The in-app editor creates safe, extension-based definitions. To use
`PROBABLE`, `UNKNOWN`, or a known `magicRuleId`, edit the JSON file externally
and import it afterward.

Known rule IDs in schema v1: `nes-ines`, `n64-native`, `chd-cd`, `chd-dvd`,
`playstation-iso`, `psp-iso`, `gamecube-disc`, `gamecube-rvz`, `wii-disc`,
`wii-rvz`, `dreamcast-disc`, `megadrive`, `genesis`, `saturn-disc`.
