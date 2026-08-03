# Battery Forensics

> **Don't guess. Investigate.**

Battery Forensics is an open-source Android application that investigates **why** battery drain happens instead of simply displaying battery statistics.

Unlike traditional battery monitors, Battery Forensics combines Android system telemetry, statistical analysis, Shizuku-powered diagnostics, and an explainable rule engine to identify likely root causes of battery drain.

## Vision

Modern Android phones expose thousands of battery-related signals, yet most apps only display battery percentage or app usage.

Battery Forensics transforms raw telemetry into understandable forensic reports.

Instead of asking:

> Which app used my battery?

Battery Forensics answers:

* Why did my battery drain overnight?
* Did my phone enter Doze?
* Is weak cellular signal keeping the modem awake?
* Are wake locks preventing deep sleep?
* Is the battery physically aging?
* Is charging behavior contributing?
* Did an OS update introduce a regression?

## Core Features

### Live Monitoring

* Battery metrics
* Charging metrics
* Temperature
* Voltage
* Current
* Screen state
* Refresh rate
* Brightness
* Signal strength
* Wi-Fi
* LTE / 5G
* Foreground application
* Thermal status

### Historical Analytics

* Hourly
* Daily
* Weekly
* Monthly
* Long-term degradation trends

### Battery Chemistry

* Capacity estimation
* Voltage sag
* Dynamic internal resistance
* Charge efficiency
* Battery wear trends

### Android Power Diagnostics

* Doze timeline
* Wake lock analysis
* AlarmManager analysis
* JobScheduler analysis
* App Standby Buckets
* Foreground service detection

### Network & Modem Diagnostics

* Signal quality
* Cell handovers
* Network transitions
* Radio activity estimation
* Weak signal detection

### Root Cause Engine

Every diagnosis contains:

* Evidence
* Confidence score
* Supporting metrics
* Counter evidence
* Recommendations

### Timeline

Replay important battery events like a flight recorder.

### AI Reports

Generate detailed Markdown reports suitable for ChatGPT, Claude, Gemini, Perplexity, and other LLMs.

### Export Formats

* JSON
* CSV
* Markdown
* HTML
* ZIP diagnostic bundle
* SQLite snapshot

## Technology

* Kotlin
* Jetpack Compose
* Material 3
* Clean Architecture
* MVVM
* Hilt
* Room
* Coroutines
* Flow
* WorkManager
* DataStore
* Shizuku Integration

## Project Philosophy

Battery Forensics distinguishes between:

### Measured

Directly observed metrics.

### Derived

Calculated metrics.

### Inferred

Evidence-based conclusions.

The application never presents guesses as facts.

## Roadmap

### Phase 1

* Live monitoring
* Dashboard
* Historical charts
* Local database

### Phase 2

* Rule engine
* Timeline
* Differential analysis
* Overnight diagnostics

### Phase 3

* Shizuku diagnostics
* Advanced parsers
* Wake lock investigation
* Doze analysis

### Phase 4

* AI exports
* Battery chemistry
* Predictive analytics
* Plugin architecture

## Privacy

Battery Forensics is privacy-first.

* No tracking
* No analytics
* No telemetry
* Offline-first
* Local database
* User-controlled exports

## Contributing

Contributions are welcome.

Priority areas include:

* Android parsers
* Diagnostics
* Rule engine
* Battery chemistry
* Charts
* Documentation
* Testing

## License

Apache 2.0 (recommended)

## Motto

**Don't guess. Investigate.**
