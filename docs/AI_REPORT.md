# AI diagnostic report specification

Generated on-device by `AiReportGenerator`. LLMs are assistants — not the diagnostic engine.

## Required sections

1. **Executive Summary** — top ranked causes or explicit insufficiency statement
2. **Device Information**
3. **Investigation Window** — start/end, sample count, battery delta
4. **Evidence** — per diagnosis with confidence labels, supporting metrics, counter-evidence, actions
5. **Root Cause Ranking** — table
6. **Supporting Metrics Snapshot** — latest sample
7. **Historical / Differential Notes**
8. **Unknown Factors** — missing permissions / short window / dumpsys gaps
9. **Timeline Notes**
10. **Privacy**
11. **LLM Instruction Block** — Measured vs Derived vs Inferred; no invented fields; no fake RRC

## Formats

* Markdown (AI-ready) — primary
* JSON — full `ForensicReport` serialization
* CSV — full sample columns
* HTML — ranked causes + evidence
* ZIP — bundle of the above
* SQLite snapshot — portable `.sql` INSERT script (local)
