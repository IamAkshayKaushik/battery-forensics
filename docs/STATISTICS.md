# Statistical model guide

Battery Forensics uses lightweight on-device statistics — not cloud ML.

## Drain rate

`DrainStats.percentPerHour(startPct, endPct, durationMs)` → `(start − end) / hours`.

Used by overnight standby rules, differential analysis, and timeline overnight replay.

## Moving / window aggregates

From `MonitoringSample` sequences (sorted by timestamp):

* Screen-on / charging / Wi-Fi ratios
* Average / max temperature
* Average cellular / Wi-Fi RSSI
* Network type transition counts
* Deep-idle **proxy**: fraction of screen-off samples with |current| below a quiet threshold (Derived)

## Differential analysis

`DifferentialAnalyzer.compare(healthy, problem, healthyPrivileged?, problemPrivileged?)` ranks metric deltas by magnitude, including privileged deltas when both nights have dumpsys evidence:

* Alarm wakeups
* Wake lock counts
* Doze motion / location interrupts

Confidence: more samples → Derived; sparse windows → Inferred / Speculative.

## Thermal rates

`ThermalAnalyzer.maxRisingRate` / `maxFallingRate` → ΔT/Δt (°C/min) between consecutive samples.

CPU runaway is **Inferred**: rapid heat + elevated thermal status while screen-off, not charging, and without a deep weak-signal modem signature. Android does not expose Measured CPU watt counters to this app.

## Network forensics

`NetworkForensics.analyze` estimates radio-active minutes from weak-signal / non-Wi-Fi windows. Always labeled **Inferred** — not RRC.

## Anomaly / baseline

`baseline_anomaly_regression` compares current overnight drain to a prior healthy window (`RuleContext.baselineSamples`). Labeled **Inferred**.

## What we refuse to invent

* Measured RRC Connected time
* Measured static-content detection (static-120Hz rule is Inferred with caveats)
* Measured HDR on API < 34 (`Display.isHdr` is API 34+)
* Continuous IMU / sensor HAL energy without a dump signal
