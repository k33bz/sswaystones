# Changelog

## Unreleased (k33bz fork)

- Global and server-owned waystones now render a globe marker head in place of the owner's
  chosen icon, so public destinations stand out in the viewer (global: minecraft-heads #102645,
  server-owned: #3638). Applied at render time, so demoting back to private/team restores the
  original icon. Toggle with the `access_mode_icons` config option (default on).
- Guard null world in `/waystone remove` for removed dimensions (NPE).
- Fix `build.yml` artifact name (a `/` in the ref broke `upload-artifact` on PR builds) (deep-review).
