# Monitoring

## Sample fields (`MonitoringSample`)

Core battery/display/radio plus expanded signals (all nullable / best-effort):

| Field | Source | Notes |
| --- | --- | --- |
| chargingCurrentMicroamps | BatteryManager while charging | Absolute µA |
| orientation | WindowManager rotation | portrait/landscape |
| cellId / carrierName | Telephony (+ location/phone perms) | Best-effort |
| cellularBand | — | Usually null without OEM/privileged APIs |
| bluetoothOn / bluetoothConnected | BluetoothAdapter (+ CONNECT on API 31+) | Profile state for connected |
| locationEnabled | LocationManager | |
| nfcEnabled | NfcAdapter | |
| hotspotOn | WifiManager `isWifiApEnabled` (reflection) | May be null on some OEMs |
| foregroundApp | UsageStatsManager | Needs usage-access grant |
| memoryPressure | ActivityManager.MemoryInfo | low/moderate/critical |
| storageFreeBytes / storageFreePercent | StatFs | |

## Intervals

- **WorkManager** periodic monitoring: minimum period is **15 minutes** (`TimeConstants.WORKMANAGER_MIN_PERIOD_MS`). Settings intervals below 15 min **cannot** be honored by WM.
- **Flight Recorder** (FGS): reads Settings `sampleIntervalMs` each loop (clamped ≥5s, ≤15 min). Enable Flight Recorder when you need fine sampling.

## Room schema

- Database version **3** adds `hdrActive` (API 34+ Display HDR sampling when available).
- Debug uses `fallbackToDestructiveMigration()` (documented here). Production should add a typed `Migration` once the schema stabilizes — upgrading wipes local samples across major schema bumps.
