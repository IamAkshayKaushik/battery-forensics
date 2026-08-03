# Permissions — when and why

Battery Forensics requests permissions only to collect **on-device evidence** that explains battery drain. Nothing is uploaded. No analytics, ads, or telemetry SDKs.

## Runtime permissions

| Permission | When requested | Why |
|---|---|---|
| `POST_NOTIFICATIONS` (API 33+) | First launch (guided), enabling Flight Recorder, Settings → Request | Required for the Flight Recorder foreground-service notification so you can see and stop sampling. |
| `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | First launch, Live Monitor / Investigate / Flight Recorder, Settings | Android gates `CellInfo` / cellular RSSI behind location. Used for **radio evidence only** — never maps, never shared. |
| `READ_PHONE_STATE` | Same as location | Network type (LTE/5G/…) for modem / transition forensics. No call logs or contacts. |

## Special intents (not runtime dialogs)

| Action | When | Why |
|---|---|---|
| Ignore battery optimizations | Settings → Request on that row | Optional. Reduces gaps in overnight Flight Recorder sampling. |

If a permission is denied permanently, the UI offers **App Settings** instead of re-spamming the system dialog.

## Shizuku (optional, advanced)

| State | Meaning |
|---|---|
| Not installed | Install Shizuku, then start it |
| Installed but not running | Open Shizuku and **start the server** (wireless debugging or root) — status is not “not installed” |
| Running but not authorized | Tap **Authorize Shizuku** — uses `Shizuku.requestPermission` |
| Authorized | Investigate runs `dumpsys` collectors → typed parsers (Doze, wake locks, alarms, jobs, …) |

Without Shizuku the app still works with public APIs; Doze / wake-lock / alarm depth stays limited.

## Storage

Exports use **app-specific storage**. Broad storage permissions are not requested.
