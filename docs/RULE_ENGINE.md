# Rule engine

## Contract

```kotlin
interface ForensicRule {
    val id: String
    val title: String
    fun evaluate(context: RuleContext): RuleEvaluation?
}
```

Each triggered evaluation must produce a `Diagnosis` with evidence, confidence level, counter-evidence, and actions.

## Bundled rules

1. **Weak cellular signal** — RSSI ≤ -110 dBm majority (Measured)
2. **Excessive screen brightness / high refresh** — screen-on window (Measured)
3. **Elevated temperature / rapid heating** — peak ≥ 40°C or Δ ≥ 5°C
4. **Charging heat** — ≥ 42°C while charging
5. **High overnight standby drain** — ≥ 3%/h over ≥ 4h mostly screen-off (Derived)
6. **Modem-induced heating** — hot + weak signal while screen-off (Inferred)
7. **Weak Wi-Fi drain** — connected Wi-Fi ≤ -80 dBm majority
8. **Battery aging / voltage sag** — rest vs load gap ≥ 150 mV (Derived)
9. **Sustained high discharge current** — avg ≥ 500 mA
10. **Frequent network transitions** — ≥ 2 type changes/h (Derived)
11. **Thermal throttling** — PowerManager status ≥ MODERATE or very high temp

## Ranking

`RuleEngine.evaluate` sorts by `probabilityPercent` desc, then `confidence.scorePercent` desc.

## Confidence labels

Never mix levels in a claim:

* Measured ★★★★★
* Derived ★★★★☆
* Inferred ★★★☆☆
* Speculative ★☆☆☆☆
