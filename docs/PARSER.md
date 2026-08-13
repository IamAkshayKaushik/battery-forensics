# Parser guide

Battery Forensics converts privileged `dumpsys` / `cmd` text into **typed Kotlin models**. Raw dump strings never reach the UI.

## Sources (Shizuku / privileged)

| Source | Parser | Model |
|--------|--------|-------|
| `dumpsys batterystats` | `BatteryStatsParser` | `BatteryStatsSummary` |
| `dumpsys batterystats -c` | `BatteryStatsParser` (merged) | checkin uid drain hints + radio ms |
| `dumpsys power` | `PowerParser`, `WakeLockParser` | `PowerSummary`, `WakeLockSummary` |
| `dumpsys deviceidle` / `deviceidle -a` | `DeviceIdleParser`, `DozeParser` | `DeviceIdleSummary`, `DozeTimelineSummary` |
| `dumpsys alarm` | `AlarmParser` | `AlarmSummary` |
| `dumpsys jobscheduler` | `JobSchedulerParser` | `JobSchedulerSummary` |
| `dumpsys usagestats` | `UsageStatsParser` | `UsageStatsSummary` |
| `dumpsys activity` / `activity services` | `ActivityParser` | `ActivitySummary` |
| `dumpsys thermalservice` | `ThermalServiceParser` | `ThermalServiceSummary` |
| `dumpsys wifi` | `WifiDumpParser` | `WifiDumpSummary` |
| `dumpsys connectivity` | `ConnectivityParser` | `ConnectivitySummary` |
| `dumpsys sensorservice` | `SensorServiceParser` | `SensorServiceSummary` |
| `dumpsys location` | `LocationDumpParser` | `LocationDumpSummary` |
| `dumpsys notification` | `NotificationDumpParser` | `NotificationDumpSummary` |
| `cmd battery` | `CmdBatteryParser` | `CmdBatterySummary` |
| `cmd jobscheduler` | `CmdJobSchedulerParser` | `JobSchedulerSummary` |

**Not run (destructive):** `cmd battery unplug` / set-level — read-only collectors only.

Optional enrichments (`-a`, `-c`, `activity services`, wifi/location/sensor/notification) fail soft when OEM-missing.

## Confidence honesty

* Tokens present in dump text (state names, counts) → **Measured** or **Derived** depending on whether the field is a direct parse vs aggregated.
* Reason tokens (`motion`, `location`) for Doze exits → **Derived** (not IMU logs).
* Wake-lock taxonomy (modem / wifi / sensors / Power HAL / app) → **Inferred** when based on tag heuristics; OEM formats vary.
* Location / sensor continuous listeners → **Derived** tokens; drain rules that correlate with screen-off samples are **Inferred**.
* RRC Connected / modem state machines → **never Measured** from these parsers.

## Doze timeline

`DozeParser` extracts:

* Known states: ACTIVE, INACTIVE, IDLE_PENDING, SENSING, LOCATING, IDLE, IDLE_MAINTENANCE, LIGHT_*
* Transition hints with optional `reason=`
* `motionTriggeredInterruptions` / `locationTriggeredInterruptions`

## Alarms

`AlarmParser` classifies RTC_WAKEUP vs ELAPSED_REALTIME_WAKEUP when the dump exposes type counts, computes wakeups/hour from elapsed realtime since boot, and builds an impact estimate string.

## App Standby

Buckets modeled: ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED — plus bypass/exemption package hints.

## Fixtures

Unit fixtures live under `parser/src/test/resources/fixtures/`. Every parser change should extend `ParserFixtureTest`.
