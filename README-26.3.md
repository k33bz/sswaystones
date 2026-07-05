# 26.3 branch — experimental stub (does not build yet)

This branch is the placeholder for a future Minecraft **26.3** build of sswaystones.
As of **2026-07-04 it does not compile**, on purpose, because the toolchain the
build sees cannot resolve a 26.3 dependency set.

## Why it can't build yet

| Dependency | Newest published | 26.3 build? |
|---|---|---|
| Minecraft (Fabric meta) | `26.2` stable; `26.3-snapshot-1/2` | ❌ only unstable snapshots |
| Fabric Loader | `0.19.3` | — |
| Fabric API | `0.152.1+26.2` | ❌ |
| polymer-core / polymer-virtual-entity (`maven.nucleoid.xyz`) | `0.17.1+26.2` | ❌ |
| sgui (`maven.nucleoid.xyz`) | `2.1.0+26.2` | ❌ |
| server-translations-api (`maven.nucleoid.xyz`) | `3.1.0+26.2` | ❌ |

polymer, sgui and server-translations are **hard dependencies** of this mod
(the whole GUI + virtual-entity display stack is built on them). Until each
publishes a `+26.3` artifact against a stable 26.3 game release, dependency
resolution fails and there is nothing to compile against.

Checked against:
- `https://meta.fabricmc.net/v2/versions/game`
- `https://maven.nucleoid.xyz/eu/pb4/polymer-core/maven-metadata.xml`
- `https://maven.nucleoid.xyz/eu/pb4/sgui/maven-metadata.xml`
- `https://maven.nucleoid.xyz/xyz/nucleoid/server-translations-api/maven-metadata.xml`

## What's here

The **source is identical** to `main` (26.2) — same branch-per-MC-version
model, only `gradle.properties` differs between branches. So the moment the
26.3 deps land, wiring a real build is a pins-only change:

1. In `gradle.properties`, bump each `TODO 26.3:` line to its real `+26.3`
   build and set `minecraft_version=26.3` / `loader_version` as needed.
2. In `.github/workflows/build.yml`, delete the `continue-on-error: true` on
   the `build (26.3 stub)` step so the job becomes required again.

Until then CI on this branch is **allowed to fail** (`continue-on-error`) so a
red 26.3 build never blocks the fork, while still surfacing the exact
resolution error for whoever revisits it.
