# Modules

| Module | Package | Notes |
| --- | --- | --- |
| app | `com.batteryforensics.app` | Application entry, navigation, screens |
| core | `com.batteryforensics.core` | Evidence, confidence, MonitoringSample |
| database | `com.batteryforensics.database` | Room DB `battery_forensics.db` |
| monitoring | `com.batteryforensics.monitoring` | Collector + repository + workers/FGS |
| battery | `com.batteryforensics.battery` | BatteryManager collector + ChemistryEngine |
| display | `com.batteryforensics.display` | Brightness / refresh / screen |
| thermal | `com.batteryforensics.thermal` | PowerManager thermal + ThermalAnalyzer |
| telephony | `com.batteryforensics.telephony` | Network type + CellInfo RSSI when permitted |
| wifi | `com.batteryforensics.wifi` | Wi-Fi connectivity / RSSI |
| ruleengine | `com.batteryforensics.ruleengine` | Forensic rules + ranking (11 rules) |
| diagnostics | `com.batteryforensics.diagnostics` | Investigation + DifferentialAnalyzer |
| parser | `com.batteryforensics.parser` | dumpsys parsers (batterystats, power, deviceidle, alarm, jobs, usagestats, thermal) |
| shizuku | `com.batteryforensics.shizuku` | Gateway + privileged dumpsys collector |
| reporting | `com.batteryforensics.reporting` | ForensicReport builder |
| ai | `com.batteryforensics.ai` | Markdown AI report |
| export | `com.batteryforensics.export` | JSON / MD / CSV / HTML / ZIP / SQL |
| timeline | `com.batteryforensics.timeline` | TimelineBuilder + overnight replay |
| settings | `com.batteryforensics.settings` | DataStore preferences |
| permissions | `com.batteryforensics.permissions` | Permission catalog |
| charts | `com.batteryforensics.charts` | MetricSparkline (+ Vico deps available) |
| statistics | `com.batteryforensics.statistics` | StatisticsEngine drain / MA / anomalies |
| analytics | `com.batteryforensics.analytics` | NetworkForensics aggregation |

There is no `network` module name; network signals live in `wifi` + `telephony` with analysis in `analytics.NetworkForensics`.
