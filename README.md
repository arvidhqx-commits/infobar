# InfoBar
Persistent, animated boss bars — server info, playtime, TPS, or any text at the top of the screen. Paper 1.21+/26.x.

Any number of named bars, each with its own text, color, overlay style, progress, and update interval. Text supports MiniMessage and legacy `&` formats, placeholders (`{player}` `{online}` `{max}` `{tps}` `{world}` `{time}`), and optional animation frames that rotate on every update. Progress can be a fixed value or track the in-game time of day. Per-player toggle (persisted), zero dependencies.

Commands: `/infobar` (toggle, players) · `/infobar reload` (admin)

## Tested on
Paper 1.21.11 and Paper 26.2 (runtime-tested, not just "it loads").

## License
MIT — see [LICENSE](LICENSE).

## Development note
This project is **AI-assisted**: the code is written with Claude under the direction, testing and
release approval of the maintainer. Every release is run against a live Paper server before it ships.
