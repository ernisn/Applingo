# Applingo

Force a per-app language on any Android app, powered by [Shizuku](https://shizuku.rikka.app/).

> **Status:** early development — no release yet.

Applingo is a modern take on [Language Selector](https://github.com/VegaBobo/Language-Selector). Goals:

- Set any language (or an ordered fallback chain such as `ja → en`) for any app, even apps that don't declare support for it — Android falls back gracefully.
- Settings survive app updates: Applingo remembers what you chose and repairs it if the system resets it.
- Fast, cached app list with forgiving search (case/whitespace-insensitive, multi-keyword, pinyin), filters and sorting.
- Material 3 UI that adapts to phones, landscape, tablets and foldables.

**Requirements:** Android 13 (API 33) or newer, Shizuku (or Sui) running.

See [docs/DESIGN.md](docs/DESIGN.md) for the design and [docs/RELEASING.md](docs/RELEASING.md) for building and releasing (both in Chinese).

## 中文简介

（开发中）Applingo 的目标是借助 Shizuku 为任意应用强制设置语言（支持多语言回退链），设置在应用更新后依然保持；提供带缓存的快速应用列表、宽容的搜索/过滤/排序，以及适配手机、横屏、平板和折叠屏的 Material 3 界面。需要 Android 13 及以上和运行中的 Shizuku。设计文档见 [docs/DESIGN.md](docs/DESIGN.md)。

## License

[MIT](LICENSE)
