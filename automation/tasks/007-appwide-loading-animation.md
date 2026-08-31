---
id: 007-appwide-loading-animation
status: pending
iteration: 0
iteration_cap: unlimited   # objective: every call site replaced+screenshotted, or not
worker_model: sonnet
worker_effort: medium
verifier_model: opus
verifier_effort: high
depends_on: []
---

## Goal

Replace the app-wide loading animation/page (currently shows red or black)
with BitChord's loading treatment, everywhere a loading state appears —
not just the artist page.

## Acceptance criteria

- Find every loading/skeleton state in the app (search for the current
  red/black loading composable — likely a shared loading indicator or
  splash-style placeholder) and identify all call sites.
- Replace with BitChord's loading animation/page look
  (`BitChord/app/src/main/java/com/music/bitchord/ui/` — find its loading
  component), applied consistently across every screen that currently shows
  the red/black version.
- No screen regresses to a blank/crashing state during the swap.
- Screenshot each affected screen's loading state before/after.

## Context

Reference clone at `BitChord/`. This was called out explicitly separate
from the artist-page chroma-tint task — scope is the whole app.
