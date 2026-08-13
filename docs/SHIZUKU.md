# Shizuku notes

Shizuku unlocks privileged shell (`newProcess` / `sh -c`) so Battery Forensics can run **read-only** `dumpsys` / `cmd` collectors.

## Collector

`ShizukuDiagnosticsCollector` (`:shizuku`) runs when availability is `Available` and Settings → Advanced diagnostics is on.

Flow:

```
dumpsys / cmd → typed parsers (:parser) → PrivilegedEvidence → RuleEngine / Differential / Timeline / Export / AI
```

Raw dump text never reaches Compose UI.

## Commands collected

Core:

* `dumpsys power`, `deviceidle` (+ optional `-a`), `jobscheduler`, `batterystats` (+ optional `-c`)
* `dumpsys alarm`, `usagestats`, `thermalservice`, `activity` (+ optional `activity services`)
* `cmd battery get status` / `cmd battery`
* `cmd jobscheduler` fallbacks when pending count missing

Radio / wake context (soft-fail if OEM-missing):

* `dumpsys wifi`, `connectivity`, `sensorservice`, `location`, `notification`

## Graceful degradation

If Shizuku is missing, not running, or denied, public API monitoring continues. UI surfaces limited features and never pretends dumpsys depth exists.

## Safety

* No `cmd battery unplug` / set charge state
* No write/mutate shell commands
* Never claim Measured RRC from dumpsys
