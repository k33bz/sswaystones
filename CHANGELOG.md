# Changelog

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
