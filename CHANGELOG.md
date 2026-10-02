# Changelog

## 1.3.2+k33bz.10

Security fixes from a review of the fork. Same fixes on `main` (26.3), `26.2` and `26.1`.

- **Waystone head icons no longer stall the server.** Building an owner's head icon called Mojang's profile service (`SessionService.fetchProfile`) on the server thread: a blocking HTTP request per icon. Paging the viewer, or every tick with `physical_icon_display` on, could freeze the server for as long as Mojang took to answer, or longer when it was down. Skins are now fetched on a background thread and cached (refreshed hourly; a failed lookup is retried after five minutes). The first view shows a plain head and the skin appears the next time the icon is drawn.
- **`/waystonesettings apply` is no longer a free portable waystone.** It checked edit rights but then reopened the waystone viewer wherever the player stood, so anyone who knew one of their own waystone hashes could teleport from anywhere. It now only works within reach of the waystone (managers may still apply from afar), and it never opens the viewer remotely.
- **Teleports are re-checked when you click.** The viewer only checked combat and access when it opened. Now, at click time, a teleport is refused if you are in combat, if the waystone was removed, or if you lost access to it while the menu was open. Experience is charged last, so a refused teleport costs nothing.
- **Paranoid teleport never breaks or places blocks.** It used to break the block above the destination waystone *with drops* and fill air under it with cobblestone, ignoring claims, so anyone allowed to teleport could empty a chest stacked on someone else's waystone. Now a waystone with no safe spot beside it (solid ground, two clear blocks, no lava or fire) refuses the teleport when `paranoid_teleport` is on. With it off, you land on the waystone as before. `paranoid_teleport_unremovable_blocks` is no longer used.
- **Bedrock forms run on the server thread.** Floodgate delivers form responses off the server thread, and the forget and settings handlers changed player and waystone data there. All form handlers now hop to the server thread first, and the settings form re-checks edit rights when it is submitted.
- New messages: `error.sswaystones.no_access`, `no_safe_spot` and `too_far`, in all 13 languages.
- **Each line only loads on its own Minecraft version.** `fabric.mod.json` declared `"minecraft": ">=<version>"`, so the 26.1 jar claimed to run on 26.2 and 26.3 and would load there and crash. It now declares `~<version>` (26.1 jar: 26.1.x only, and so on).
- **Edit menus re-check rights on every click.** The Java name, icon and access-settings menus, and the admin "steal" button, checked permissions only when they opened. If an admin took the waystone over, or a permission was revoked, while the menu was open, the player could still change it.
- **Formatting codes are stripped from waystone names.** Bedrock forms and `/waystonesettings apply` let `§` codes and control characters through, so a name could fake colours, obfuscate itself or break across lines in everyone's viewer and on the hologram. Every name, including ones already saved, is now cleaned (`WaystoneNames`, unit tested).
- **Forgetting a waystone really forgets it.** Placing a waystone added it to the creator's discovered list twice (on creation and on the first right-click), and forgetting removed only one copy, so the waystone stayed usable. Duplicates are no longer added, every copy is removed, and saves with duplicates are cleaned when loaded.
- **Withers and the Ender Dragon can't break waystones.** All waystone blocks are in the vanilla `wither_immune` and `dragon_immune` block tags, through a new `sswaystones:waystones` tag. TNT already couldn't break them.
- **`/waystonesettings apply` no longer reveals where waystones are.** A waystone hash is the SHA-256 of its position, so anyone can compute one, and "not found" versus "no permission" told players whether a waystone stood at any coordinates they tried. Both now get the same answer.
- README: recommends setting `waystone_limit` on public servers (default stays `0`, unlimited) and describes the new paranoid teleport behaviour.

CI (no mod change):

- **Real-server CI test** (`scripts/server_test.py`, adapted from Sanctuary's): every push and PR boots a real
  Fabric server on the newest release of the branch's Minecraft line (the 26.1 line tests 26.1.2, what servers
  run) with the newest fabric-api, and checks through the console that sswaystones initializes and injects its
  village pieces, that `/sswaystones` and `/sswaystones list` answer, that all five village waystone templates
  load and place, that a waystone block placed by command ticks cleanly, that a plains village generates
  through the injected pools, and that no mixin or tick error reaches the log.
- **README badges per release line** (`main` 26.3, `26.2`, `26.1`): build, mod version, the Minecraft release
  tested, Fabric loader, fabric-api, Polymer and the server-test count. CI writes shields.io endpoint JSON to an
  orphan `badges` branch on pushes to a release line only (`scripts/publish_badges.py`,
  `scripts/commit_badges.sh`).
- **Release workflow** (`.github/workflows/release.yml`): pushing a tag `v<mod_version>+<minecraft_version>` on a release line checks the tag against that commit's `gradle.properties` and branch, builds, runs the real-server test, and publishes `sswaystones-<mod_version>+<minecraft_version>.jar` (with a `.sha256`) as a GitHub release, with notes taken from this changelog. The build names every line's jar the same, so the Minecraft suffix is what tells them apart. Only `main` releases are marked latest.

## 1.3.2+k33bz.9

- Translations for zh_cn, zh_tw, ru_ru, es_es, es_mx, pt_br, de_de, ja_jp, fr_fr, fr_ca, ko_kr and pl_pl. A unit test keeps them in step with en_us.
- Fork code is now maintained on `main` (26.3), `26.2` and `26.1`.

## 1.3.2+k33bz.8

- A server-owned waystone shows the icon an admin chose, and the globe only if none was chosen. Global waystones always show the globe.
- `/waystonesettings icon <hash> <item>` sets a waystone's icon from the console.

## 1.3.2+k33bz.7

- Only admins can edit a server-owned waystone. Its original owner could still rename it and hide its name.

## 1.3.2+k33bz.6

- Team waystones use their team's colour in the viewer list and on the hologram.

## 1.3.2+k33bz.5

- Waystone names in the viewer list are coloured by access: gray private, aqua team, green global, gold server.

## 1.3.2+k33bz.4

- A non-admin can no longer demote a server-owned waystone to private or global through the settings dialog or `/waystonesettings apply`.

## 1.3.2+k33bz.3

- The Change Icon button is crossed out while a globe marker overrides the icon.

## 1.3.2+k33bz.2

- Global and server-owned waystones show a globe head in place of the owner's icon. Config `access_mode_icons` turns this off.

## 1.3.2+k33bz.1

- Native settings dialog for Java players, enabled with `settings_ui: "dialog"`. Name, access and hide-name in one form.
- One Access selector (Private / Team / Global / Server-owned) in the dialog and the Bedrock form instead of three toggles.
- Hide Name toggle, from Hellscaped's upstream PR #51.
- Viewer: page arrows only when there is more than one page, left/right-click hints on entries.
- `/waystone remove` no longer crashes on a waystone in a removed dimension.
- Unit tests and a build pipeline per Minecraft version.
