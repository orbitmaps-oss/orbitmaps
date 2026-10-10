# 0001: Own navigation logic instead of Ferrostar

- **Date:** 2026-10-09
- **Status:** Accepted (project lead)

## Context

The plan (step 3) named Ferrostar for turn-by-turn navigation: its core does trip state, off-route
detection and instructions, on top of on-device Valhalla through a custom route provider.

Adding `com.stadiamaps.ferrostar:core` 0.57.0 showed that it **requires core library desugaring**,
which ships Google's `desugar_jdk_libs` (OpenJDK code, GPL-2.0 with the Classpath Exception) inside
the APK. AGENTS.md section 4 doesn't allow third-party GPL code in the app, because it can't carry
our App Store additional permission. Ferrostar also adds JNA, a Rust native library, OkHttp 5 and
AppCompat, while we would use only a small part of it (no HTTP adapters, no map UI).

## Decision

Write our own navigation logic in Kotlin (GPL-3.0-or-later), on Valhalla's own route output: the
route line and the maneuver list with its written and spoken instructions. It covers following the
route, the current step, distance to the next turn, remaining distance and time, arrival, off-route
detection and rerouting with the on-device engine, and when to speak each instruction (Android
text-to-speech). No new dependency.

## Consequences

- Fully ours: no licence questions, no extra native code, and the logic is unit-tested without a
  device.
- We maintain map matching and announcement timing ourselves; Valhalla supplies the hard parts
  (routes, instructions in many languages).
- Ferrostar stays an option if it drops the desugaring requirement or the licence question is
  settled; the navigation code sits behind one small interface.
