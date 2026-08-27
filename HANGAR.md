# InfoBar

**Persistent, animated boss bars — server info, TPS, time of day, or any text at the top of the screen.
Paper 1.21+ and 26.x.**

---

## What it does

Boss bars are the one piece of screen real estate that is always visible and never in the way. InfoBar lets
you put anything there: a welcome line, the player count, live TPS, the in-game time — as many named bars as
you want, each with its own style and refresh rate.

## Features

- **Any number of named bars**, each independently configurable
- **MiniMessage and legacy `&` text**, with placeholders `{player}` `{online}` `{max}` `{tps}` `{world}` `{time}`
- **Animation frames** — give a bar a list of texts and it rotates through them on every update
- **Progress** as a fixed value, or `time` to track the in-game day
- **Colours** (PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE) and **overlays**
  (PROGRESS, NOTCHED_6/10/12/20)
- **Per-bar update interval**
- **Per-player toggle**, persisted
- No dependencies, one jar

## Example config

```yaml
bars:
  server:
    enabled: true
    text: '&6Welcome! &7Online: &f{online}&7/&f{max} &8| &7TPS: &f{tps}'
    color: YELLOW
    overlay: PROGRESS
    progress: 1.0
    update-interval-seconds: 5
  daytime:
    enabled: false
    text: '&bTime: &f{time}'
    color: BLUE
    overlay: NOTCHED_10
    progress: 'time'
    update-interval-seconds: 5
```

## Commands

| Command | What it does |
|---|---|
| `/infobar` | Hide or show all bars for yourself |
| `/infobar reload` | Reload the config (permission `infobar.admin`) |

| Permission | Default |
|---|---|
| `infobar.see` | true |
| `infobar.admin` | op |

## Compatibility

Built for the Paper API 1.21 and up. Every release is started on a **live Paper 1.21.11 server and a live
Paper 26.2 server** and the actual behaviour is checked — not just "the plugin loads".

## Source & licence

MIT licensed, source on [GitHub](https://github.com/arvidhqx-commits/infobar).

## Development note

This project is **AI-assisted**: the code is written with Claude under the direction, testing and release
approval of the maintainer. Every release is run against a live Paper server before it ships.
