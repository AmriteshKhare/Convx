---
id: 012-glass-scope-navbar-miniplayer
status: pending
iteration: 0
iteration_cap: unlimited   # objective: setting scoped correctly or not
worker_model: sonnet
worker_effort: high
verifier_model: opus
verifier_effort: high
depends_on: []
---

## Goal

The user-facing Glass setting (`LiquidGlassStyleKey`) currently changes
glass style app-wide via `LocalGlassEffectConfig`. Narrow it so it only
controls the nav bar and mini player. Every other glass surface (top app
bars, bottom sheets, buttons, DIY editor, etc.) keeps glass but at one
fixed, non-user-adjustable default style.

## Acceptance criteria

- `LiquidGlassStyleKey`/`LocalGlassEffectConfig` only drives
  `FloatingNavBar`/`FloatingSideBar` (nav bar) and `MiniPlayer`/
  `NewMiniPlayer`/`AppleMiniPlayer` (mini player).
- Every other current consumer of `LocalGlassEffectConfig` (top bars,
  `BottomSheetMenu`/`BottomSheetPage`, `GlassCircleButton`, DIY editor,
  etc.) is repointed to a fixed constant `GlassStyle` value instead of the
  user preference — glass effect itself must remain visible on all of
  them, only the style becomes non-configurable.
- The exact fixed style value is a placeholder until the user supplies it
  — use the current default `GlassStyle` as the placeholder and clearly
  flag in the progress report that this value is provisional pending the
  user's final choice, rather than blocking the whole task on it.
- Settings screen for Glass Effect (`GlassEffectSettings.kt`) UI updated to
  make clear the setting now only affects nav bar + mini player.
- Verified on-device: changing the Glass setting visibly changes nav bar +
  mini player, and visibly does NOT change a top bar or bottom sheet's
  glass style.

## Context

`app/src/main/kotlin/com/convx/music/ui/component/GlassEffect.kt`
(`GlassEffectConfig`, `LocalGlassEffectConfig`, `GlassStyle` enum, `MINI_PLAYER`
enum member already exists suggesting per-surface awareness may partly
exist already — check before assuming a rewrite is needed). This task must
land before 011 (settings redesign) since that task's glass section depends
on this one being done first.
