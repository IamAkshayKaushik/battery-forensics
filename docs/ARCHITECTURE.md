# Architecture

Battery Forensics follows Clean Architecture with strict dependency direction:

```
Presentation (app Compose / ViewModels)
        ↓
Domain (core models, ruleengine, diagnostics, reporting, statistics, analytics)
        ↓
    Data (database, monitoring repositories, collectors)
        ↓
Platform (Android system APIs, optional Shizuku)
```

## Evidence philosophy

Every diagnosis carries:

* Evidence (measured / derived / inferred / speculative — labeled)
* Confidence score + level
* Supporting metrics
* Counter-evidence
* Recommended actions

Raw `dumpsys` never reaches the UI. Parsers emit typed models only.

## Monitoring

* **WorkManager** periodic sampling (~15 min) for always-on background collection
* **Flight Recorder** optional foreground service (15s) when the user explicitly enables continuous capture — justified because forensic timelines need finer resolution than WorkManager allows

## Domain engines (shipped)

| Engine | Module | Role |
| --- | --- | --- |
| ChemistryEngine | `:battery` | Ri = ΔV/ΔI, voltage sag, cycles, wear, charge efficiency |
| ThermalAnalyzer | `:thermal` | ΔT/Δt, max/charging temp, throttling events |
| NetworkForensics | `:analytics` | Signal, transitions, inferred radio-active time |
| StatisticsEngine | `:statistics` | Standby/screen drain, MA, anomaly hooks, baselines |
| DifferentialAnalyzer | `:diagnostics` | Healthy vs problem window deltas |
| TimelineBuilder | `:timeline` | Meaningful event log + overnight replay |
| RuleEngine | `:ruleengine` | Ranked root causes (11 bundled rules) |
| ShizukuDiagnosticsCollector | `:shizuku` | dumpsys → typed models when Shizuku granted |

## Rule engine

Rules implement `ForensicRule`. `RuleEngine` ranks triggered diagnoses by probability then confidence. The LLM export is an assistant layer; conclusions already exist before export.

## Export

JSON, CSV, HTML, Markdown AI report, ZIP diagnostic bundle, SQLite SQL snapshot — all local.
