package com.batteryforensics.parser.wifi

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult
import com.batteryforensics.parser.WifiDumpSummary

/**
 * Best-effort `dumpsys wifi` parser for radio-drain context.
 * OEM formats vary — fields are Derived when present, never Measured RRC.
 */
class WifiDumpParser : DumpsysParser<WifiDumpSummary> {
    override val sourceName: String = "dumpsys wifi"

    override fun parse(rawDump: String): ParseResult<WifiDumpSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty wifi dump")
        val enabled = when {
            Regex("""Wi-?Fi\s+is\s+enabled""", RegexOption.IGNORE_CASE).containsMatchIn(rawDump) -> true
            Regex("""Wi-?Fi\s+is\s+disabled""", RegexOption.IGNORE_CASE).containsMatchIn(rawDump) -> false
            Regex("""mWifiEnabled\s*=\s*true""", RegexOption.IGNORE_CASE).containsMatchIn(rawDump) -> true
            Regex("""mWifiEnabled\s*=\s*false""", RegexOption.IGNORE_CASE).containsMatchIn(rawDump) -> false
            else -> null
        }
        val rssi = Regex("""(?:RSSI|mRssi|rssi)\s*[:=]\s*(-?\d+)\s*(?:dBm)?""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val ssid = Regex("""(?:SSID|mSSID)\s*[:=]\s*"?([^"\n\r]+)"?""", RegexOption.IGNORE_CASE)
            .findAll(rawDump)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("<unknown ssid>", true) && it != "0x" }
        val scanCount = Regex("""(?:Scan results?|scanResults?)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""ScanResult\{""").findAll(rawDump).count().takeIf { it > 0 }
        val supplicant = Regex("""(?:Supplicant state|mSupplicantState)\s*[:=]\s*(\w+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.getOrNull(1)
        val scanning = Regex("""(?:isScanning|mScanning|scanning)\s*[:=]\s*true""", RegexOption.IGNORE_CASE)
            .containsMatchIn(rawDump)
        return ParseResult.Success(
            WifiDumpSummary(
                wifiEnabled = enabled,
                connectedRssiDbm = rssi,
                connectedSsidHint = ssid?.take(48),
                scanResultCount = scanCount,
                isScanning = scanning.takeIf { it },
                supplicantState = supplicant,
                notes = buildList {
                    add("Wi-Fi dump fields are Derived — OEM dumpsys shapes vary")
                    if (rssi != null && rssi < -85) add("Weak Wi-Fi RSSI may elevate radio active time")
                    if (scanning) add("Active scan flag present")
                },
            ),
        )
    }
}
