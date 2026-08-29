# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).

## [0.2.0] - 2026-08-29

### Added

- In-app update-log page under Me → 项目更新日志, kept in sync with this file.
- Two-level settings structure on the Me page with per-section detail pages.

### Changed

- Rebranded the app to 「玄览」 with a new tian-dao themed adaptive launcher icon.
- Compacted the bottom navigation bar height.
- Slimmed the browser top bar into a single row; aligned the address field and tab-switcher button heights; moved bookmark, find-in-page and new-tab actions into the overflow menu.
- Moved running background sessions to the top of the Me page and restyled layout sliders with a gradient track and bordered thumb.

## [0.1.0] - 2026-08-29

### Added

- Website metadata and icon discovery flow.
- Fixed-capacity launcher pages with folders and persisted ordering.
- DragLayer-based long-press dragging, reorder targets, folder hotspot and edge paging.
- Multi-tab browser with a compact-width tab switcher.
- Managed WebView sessions, site settings and foreground-service integration.
- Notification permission and battery-optimization status surfaces.
- Project, contribution, agent and signed-release operating standards.

### Fixed

- Prevented remote icons and drag scaling from changing launcher cell geometry.
- Rebound WebView listeners and current-tab callbacks after tab switches.
- Applied persisted home-grid settings and paginated large app collections deterministically.
- Connected global keep-alive settings to persistent state and service control.

[0.2.0]: https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.2.0
[0.1.0]: https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.1.0

