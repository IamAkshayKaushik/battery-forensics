# Database schema

Room database: `battery_forensics.db` (**version 2**)

## Migration note

v1→v2 adds monitoring columns. Debug builds use `fallbackToDestructiveMigration()` (see `docs/MONITORING.md`). Local samples are wiped on upgrade until a typed Migration ships.

## `monitoring_samples`

| Column | Type | Notes |
| --- | --- | --- |
| id | INTEGER PK | auto |
| timestampEpochMs | INTEGER | wall clock |
| batteryPercent | INTEGER? | Measured |
| voltageMv | INTEGER? | Measured |
| currentMicroamps | INTEGER? | Measured |
| chargeCounterMah | INTEGER? | Derived from charge counter |
| temperatureC | REAL? | Measured |
| isCharging | INTEGER? | bool |
| chargePlug | TEXT? | usb/ac/wireless |
| screenOn | INTEGER? | bool |
| brightnessPercent | INTEGER? | Measured |
| refreshRateHz | REAL? | Measured |
| thermalStatus | INTEGER? | PowerManager constant |
| wifiConnected | INTEGER? | bool |
| wifiRssiDbm | INTEGER? | Measured |
| cellularRssiDbm | INTEGER? | Measured when available |
| networkType | TEXT? | lte/5g/… |
| chargingCurrentMicroamps | INTEGER? | while charging |
| orientation | TEXT? | |
| cellId / carrierName / cellularBand | TEXT? | best-effort |
| bluetoothOn / bluetoothConnected | INTEGER? | |
| locationEnabled / nfcEnabled / hotspotOn | INTEGER? | |
| foregroundApp | TEXT? | UsageStats |
| memoryPressure | TEXT? | |
| storageFreeBytes | INTEGER? | |
| storageFreePercent | REAL? | |

## `timeline_events`

| Column | Type |
| --- | --- |
| id | INTEGER PK |
| timestampEpochMs | INTEGER |
| eventType | TEXT |
| title | TEXT |
| detail | TEXT |
| severity | TEXT |

Schema JSON is exported under `database/schemas/` via Room KSP.
