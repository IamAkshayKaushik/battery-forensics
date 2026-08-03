# Database schema

Room database: `battery_forensics.db` (version 1)

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
