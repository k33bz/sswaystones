# Changelog

## Unreleased (k33bz fork)

- **Security:** a server-owned waystone can now only be edited by an admin. Previously its
  nominal owner (a regular player) could still rename it or hide its name — only the access
  mode was locked (k33bz.4). `canPlayerEdit` now returns admin-only for server-owned waystones,
  closing rename / hide-name / access / icon across the Java UI, Bedrock UI, and the command
  backend. (Forget/delete and block-breaking were already blocked for server-owned.)

- Waystone names are now colour-coded by reach, consistently in the viewer list *and* the
  in-world hologram: a **team** waystone shows its team's real colour (matching the floating
  name), while global (green), server (gold), and private (gray) use the access palette. One
  shared `WaystoneColors` helper drives the dialog selector, the list, and the hologram.

- **Security:** a non-admin can no longer demote a **server-owned** waystone. The access selector
  now locks a server-owned waystone to "server" for anyone without the admin `create.server`
  permission (previously it always offered Private, letting the nominal owner reclaim it), and
  the `/waystonesettings apply` backend rejects leaving server-owned without that permission.

- Global and server-owned waystones now render a globe marker head in place of the owner's
  chosen icon, so public destinations stand out in the viewer (global: minecraft-heads #102645,
  server-owned: #3638). Applied at render time, so demoting back to private/team restores the
  original icon. Toggle with the `access_mode_icons` config option (default on).
  - The sgui "Change Icon" button is crossed out (with an explanatory tooltip) while the marker
    overrides the icon, and becomes active again once the waystone is private/team.
- Guard null world in `/waystone remove` for removed dimensions (NPE).
- Fix `build.yml` artifact name (a `/` in the ref broke `upload-artifact` on PR builds) (deep-review).
