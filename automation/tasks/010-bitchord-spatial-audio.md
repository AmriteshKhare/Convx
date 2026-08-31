---
id: 010-bitchord-spatial-audio
status: pending
iteration: 0
iteration_cap: unlimited   # objective: feature ported and audibly working, or not
worker_model: opus
worker_effort: high
verifier_model: opus
verifier_effort: high
depends_on: []
---

## Goal

Port BitChord's spatial audio feature into Convx.

## Acceptance criteria

- Read `BitChord/app/src/main/java/com/music/bitchord/playback/SpatialAudioProcessor.kt`
  in full, and how it's wired into `PlaybackService.kt` and exposed in
  `SettingsSheet.kt`/`AppSettings.kt` there.
- Port the processor + its settings toggle into Convx's equivalent
  `MusicService.kt` audio-processor chain and settings screen, following
  the existing pattern for wiring a new `AudioProcessor` into the chain
  (see how `eqProcessor`/`SilenceDetectorAudioProcessor` are wired for the
  pattern to match).
- Respect this session's Bit Perfect mode: spatial processing must bypass
  when Bit Perfect is active, same as EQ/silence-skip already do
  (`EqualizerService.setBitPerfectBypass` pattern) — spatial audio
  deliberately alters the signal, which is incompatible with bit-perfect
  passthrough.
- Verified on-device: audible difference with a stereo/spatial-capable
  track and headphones, toggle on/off both audible and correct.

## Context

BitChord is cloned in-repo at `BitChord/` (do not re-fetch from GitHub).
Bit Perfect mode shipped this session: `app/src/main/kotlin/com/convx/music/playback/audio/BitPerfectAudio.kt`,
`EqualizerService.kt` (`setBitPerfectBypass`), `BitPerfectKey` in
`PreferenceKeys.kt`.
