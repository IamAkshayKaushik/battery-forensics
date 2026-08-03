# Developer guide

## Getting started

1. Install JDK 17 and Android SDK platform 35
2. Copy `local.properties` with `sdk.dir=...` (gitignored)
3. `./gradlew :app:assembleDebug`
4. Open in Android Studio and Run on Android 9+ (minSdk 28)

## Conventions

* Prefer immutable `data class` / `sealed class` models
* Business logic stays out of Composables
* Add JVM unit tests for parsers, rules, chemistry, thermal, statistics, timeline
* Do not add analytics / ads / network telemetry dependencies
* Ask of every feature: does it help explain **why** battery drains?
* Label every claim Measured / Derived / Inferred / Speculative

## Useful tasks

```bash
./gradlew :ruleengine:test
./gradlew :parser:test
./gradlew :export:test
./gradlew :battery:test
./gradlew :thermal:test
./gradlew :statistics:test
./gradlew :diagnostics:test
./gradlew :timeline:test
./gradlew :app:assembleDebug
```

## Device verification

1. Install `app/build/outputs/apk/debug/app-debug.apk` via USB/adb
2. Grant location (cellular RSSI) and phone state when prompted
3. Capture samples from Live Monitor / enable Flight Recorder overnight
4. Open Chemistry / Thermal / Network / Causes / Timeline
5. Optional: install Shizuku and grant permission for dumpsys path

## Contribution priorities

Parsers, rules, chemistry estimation, timeline fidelity, charts, and tests.
