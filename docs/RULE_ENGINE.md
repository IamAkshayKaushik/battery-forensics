# Rule engine

## Contract

```kotlin
interface ForensicRule {
    val id: String
    val title: String
    fun evaluate(context: RuleContext): RuleEvaluation?
}

data class RuleContext(
    val samples: List<MonitoringSample>,
    val nowEpochMs: Long = …,
    val privileged: PrivilegedEvidence? = null, // typed dumpsys summaries
    val baselineSamples: List<MonitoringSample> = emptyList(),
)
```

Each triggered evaluation must produce a `Diagnosis` with evidence, confidence level, counter-evidence, and actions.

Privileged dumpsys never claims Measured for RRC/modem state machines — use Derived/Inferred.

## Bundled rules (`DefaultRules.all()`) — 31 rules

### Sample-based

1. weak_cellular_signal
2. excessive_screen_brightness
3. elevated_temperature
4. charging_heat
5. overnight_standby_drain
6. modem_induced_heating (Inferred)
7. weak_wifi_drain
8. battery_aging_voltage_sag
9. high_discharge_current
10. frequent_network_transitions
11. thermal_throttling
12. location_enabled_drain
13. bluetooth_left_on_drain
14. hotspot_on_drain
15. display_120hz_screen_on
16. display_static_120hz_inferred (Inferred; Android has no static-content API)
17. display_hdr_active_drain (Measured when hdrActive sampled; API 34+)
18. thermal_runaway_ish
19. charging_inefficiency_heat
20. baseline_anomaly_regression (Inferred; needs baselineSamples)
21. nfc_left_on_drain (Speculative)
22. low_storage_pressure

### Privileged (Shizuku dumpsys → RuleContext.privileged)

23. doze_failure_to_enter
24. frequent_doze_exits
25. doze_motion_location_interrupts
26. alarm_storm
27. wake_lock_abuse
28. app_standby_bypass
29. fgs_abuse
30. gms_wakeup_pattern (Inferred)
31. jobscheduler_thrash

### Skipped (no signal)

- Continuous IMU / sensor HAL drain — not sampled; unknown-factor note only.

## Ranking

`RuleEngine.evaluate` sorts by `probabilityPercent` desc, then `confidence.scorePercent` desc.

## Confidence labels

Never mix levels in a claim:

* Measured ★★★★★
* Derived ★★★★☆
* Inferred ★★★☆☆
* Speculative ★☆☆☆☆
