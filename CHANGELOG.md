# Changelog

## Unreleased (k33bz fork)

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
