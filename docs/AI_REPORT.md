# AI diagnostic report specification

Generated on-device by `AiReportGenerator`. LLMs are assistants — not the diagnostic engine.

## Required sections

1. **Executive Summary**
2. **Battery Overview**
3. **Device Info**
4. **Timeline**
5. **Historical Stats**
6. **Evidence**
7. **Confidence**
8. **Measured / Derived / Inferred**
9. **Charts refs**
10. **Chemistry / Thermal / Network / Wake locks / Alarms / Doze / Apps**
11. **Shizuku Findings**
12. **Differential**
13. **Root Cause Ranking**
14. **Recommendations**
15. **Unknown Factors**
16. **Supporting Metrics Snapshot**
17. **Raw appendix**
18. **LLM Instruction Block** — asks for probabilities: hardware issue, software issue, battery degraded, modem responsible, rogue app, Android bug, battery replacement help

## Formats / share

* Markdown (AI-ready) — primary
* JSON — full `ForensicReport`
* CSV — full sample columns
* HTML — ranked causes + evidence
* ZIP — bundle + optional Room `.db` binary + SQL text
* SQL snapshot — portable `.sql` TEXT (honestly labeled; not a binary SQLite file)

**User deliverable:** Export screen saves under app-specific `files/exports/session_*` and shares via `FileProvider` + `ACTION_SEND` chooser.
