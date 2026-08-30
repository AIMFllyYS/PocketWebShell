# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow the rules in `docs/VERSIONING.md`.

## [0.1.2] - 2026-08-31

### Changed

- Light theme rebuilt on the iOS grouped-list standard: gray background `#F2F2F7` now hosts pure-white cards, replacing the previous white-background/light-gray-card layering.
- All secondary text (`onSurfaceVariant`, `secondary`, `tertiary`) deepened to `#55555B` — 7.4:1 on white cards (WCAG AAA), up from a marginal 4.74:1.
- `surfaceVariant` deepened to `#E5E5EA` so icon placeholders and browser input areas stay visible on the new gray background.
- Dark-theme card surface aligned to iOS grouped dark (`#1C1C1E`).
- Bottom glass bar: selected tab now shows a capsule indicator fill (primary at 12% alpha, color-only animation, no layout change).

### Added

- `AppCard` gains a 1dp `outlineVariant` hairline border (static edge-light, zero performance cost).

### Fixed

- Eliminated the "dirty pink" card appearance caused by near-white gray cards over a pure-white background (simultaneous-contrast effect).

## [0.1.1] - 2026-08-30

### Added

- Unified design-system layer in `core/designsystem`: full light/dark color tokens, MiSans typography scale, shape/spacing/motion tokens, and shared components (`AppCard`, `AppListRow`, `AppListDivider`, `AppSectionHeader`, `AppBadge`, `AppConfirmDialog`, `glassSurface`).
- Bundled subsetted MiSans font (regular/medium/semibold) so text metrics are identical on every device.
- Design Playbook under 我的 → 开发者选项: live preview of color tokens, typography, shapes, spacing, list rows, buttons, dialogs, glass material and motion specs.
- Component-library inventory documented in `docs/DESIGN.md`.

### Changed

- Me settings pages rebuilt on the unified components with iOS-style grouped sections (section header above edge-to-edge cards).
- Browser top bar rebuilt: self-drawn capsule address field keeps text vertically centered; tab-count button uses neutral surface tokens; tonal-elevation tint removed.
- Bottom bar glass effect now consumes the shared `glassSurface` component instead of a one-off implementation.
- Layout sliders use a single primary-color track with a white thumb (gradient track removed).

### Fixed

- Settings subtitle text no longer uses the low-contrast `outline` token (all secondary text now `onSurfaceVariant`, ≥4.5:1 on card backgrounds).
- Browser address-bar placeholder text no longer clipped in half by the fixed 46dp field height.
- Purple tint on cards and badges eliminated by defining every Material 3 color token explicitly.
- Hard-coded Google-blue fallback icon colors replaced with `primaryContainer`/`onPrimaryContainer` theme tokens.

### Removed

- Dropped unused library declarations (Lottie, reorderable) to keep one unified library per capability.

## [0.1.0] - 2026-08-30

> 版本计数从本版本起重置（旧线止于 0.2.0 / versionCode=2）。新线为 versionCode=1 起；
> 已安装旧版 0.2.0 的设备需先卸载再安装。

### Added

- Apple-style theme system: pure-white / pure-black base schemes, follow-system mode, and a photo-wallpaper mode that extracts the accent color from the user's photo (Me → 外观与主题).
- iOS Liquid Glass floating capsule bottom bar powered by the Haze library (live backdrop blur + specular edge highlight).
- Project specification documents: `docs/DESIGN.md`, `docs/VERSIONING.md`, `docs/PERFORMANCE.md`, referenced from `AGENTS.md`.

### Changed

- Version counting restarted from 0.0.0 (versionCode 1); default increment is the third digit only.

## [旧线 0.2.0] - 2026-08-29

### Added

- In-app update-log page under Me → 项目更新日志, kept in sync with this file.
- Two-level settings structure on the Me page with per-section detail pages.

### Changed

- Rebranded the app to 「玄览」 with a new tian-dao themed adaptive launcher icon.
- Compacted the bottom navigation bar height.
- Slimmed the browser top bar into a single row; aligned the address field and tab-switcher button heights; moved bookmark, find-in-page and new-tab actions into the overflow menu.
- Moved running background sessions to the top of the Me page and restyled layout sliders with a gradient track and bordered thumb.

## [旧线 0.1.0] - 2026-08-29

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

[0.2.0]: https://github.com/AIMFllyYS/PocketWebShell/releases
[0.1.0]: https://github.com/AIMFllyYS/PocketWebShell/releases
[旧线 0.2.0]: https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.2.0
[旧线 0.1.0]: https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.1.0

