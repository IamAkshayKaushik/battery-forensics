# Parser guide

Battery Forensics converts privileged `dumpsys` / `cmd` text into **typed Kotlin models**. Raw dump strings never reach the UI.

## Sources (Shizuku / privileged)

| Source | Parser | Model |
|--------|--------|-------|
| `dumpsys batterystats` | `BatteryStatsParser` | `BatteryStatsSummary` |
| `dumpsys power` | `PowerParser`, `WakeLockParser` | `PowerSummary`, `WakeLockSummary` |
| `dumpsys deviceidle` | `DeviceIdleParser`, `DozeParser` | `DeviceIdleSummary`, `DozeTimelineSummary` |
| `dumpsys alarm` | `AlarmParser` | `AlarmSummary` |
| `dumpsys jobscheduler` | `JobSchedulerParser` | `JobSchedulerSummary` |
| `dumpsys usagestats` | `UsageStatsParser` | `UsageStatsSummary` |
| `dumpsys activity` | `ActivityParser` | `ActivitySummary` |
| `dumpsys thermalservice` | `ThermalServiceParser` | `ThermalServiceSummary` |
| `cmd battery` | `CmdBatteryParser` | `CmdBatterySummary` |
| `cmd jobscheduler` | `CmdJobSchedulerParser` | `JobSchedulerSummary` |

## Confidence honesty

* Tokens present in dump text (state names, counts) → **Measured** or **Derived** depending on whether the field is a direct parse vs aggregated.
* Reason tokens (`motion`, `location`) for Doze exits → **Derived** (not IMU logs).
* Wake-lock taxonomy (modem / wifi / sensors / Power HAL / app) → **Inferred** when based on tag heuristics; OEM formats vary.
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
