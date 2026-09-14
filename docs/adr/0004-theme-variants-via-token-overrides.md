# 0004 — Theme variants via token overrides

Status: Accepted

Amends: [0003 — Visual identity: dark, data-first SOC console](0003-visual-identity-tactical-telemetry.md)
(specifically its "no light theme is planned" consequence, which this record
supersedes).

## Context

The console shipped as dark-only: a single `:root` block of CSS custom
properties, `color-scheme: dark` fixed in both the stylesheet and the page
`<meta>` tag, and every component reading colors through those custom
properties rather than hardcoding hex values. A request came in for
additional themes beyond dark and light — not a redesign, but more options
for the operator's own working conditions (daylight NOC walls, low-vision
accessibility, personal preference).

Because no component ever hardcodes a color, the token layer is the only
place that needs to change to support this — the question is how to
structure that change, not whether the rest of the app needs to be touched.

## Decision

Add theme variants as **overrides of the same token set**, selected by a
`data-theme` attribute, not as separate parallel stylesheets and not as a
redesign of the token categories themselves.

- **Four themes**, one always active: `gunmetal` (the existing dark theme,
  unchanged, and the default when no theme has been chosen), `daylight` (a
  light theme for bright ambient conditions), `phosphor` (a deeper, more
  saturated dark variant in the same green-gray family, for a CRT-telemetry
  feel), and `high-contrast` (near-black/near-white, AAA-target contrast
  ratios, for low-vision use).
- **Only color tokens vary between themes.** Typography, spacing, layout,
  and radius tokens (`--font-*`, `--s-*`, `--rail-width`, `--radius`, etc.)
  stay identical across all four — the visual identity's structure is one
  thing, its palette is another, and only the palette is user-selectable.
- **Severity colors keep the same hue across themes**, adjusted only in
  lightness/saturation for contrast against each theme's surface. A critical
  incident reads as "red-ish" in every theme; what changes is how dark or
  light that red needs to be to stay readable, not which color family it
  belongs to. This keeps the meaning-carries-color rule from 0003 intact
  across themes instead of redefining it four times.
- **Mechanism**: `<html data-theme="...">` plus `[data-theme="..."] { ... }`
  blocks in the token stylesheet, each one only overriding the color custom
  properties. No `data-theme` attribute (a fresh session, or storage
  cleared) means the plain `:root` block applies — today's gunmetal theme,
  byte-for-byte unchanged. A small inline script in `index.html`'s `<head>`
  reads the stored preference and sets the attribute before first paint, so
  there is no flash of the wrong theme.
- **Preference storage**: a `localStorage` key read/written by a small
  Angular service, following the same read-with-fallback pattern already
  used for stored auth tokens elsewhere in the app (missing or invalid
  stored value falls back to the default theme rather than erroring).
- **Selection UI**: a settings screen, not a quick header toggle. Four
  themes is a real choice with a visible preview, not a binary switch, and
  the console's navigation was already missing a settings destination.
- **The two token files stay in sync deliberately, not accidentally.**
  `network-ui/src/styles/tokens.css` is the file the app actually ships;
  `docs/design/tokens.css` is a documentation copy of it. Both are updated
  together whenever a theme changes, and they should be byte-identical
  after each such change.

## Consequences

- 0003's "no light theme is planned" is superseded: a light theme (and two
  further variants) now exist, but 0003's actual rule — color carries
  meaning, values live in tokens, no hardcoded hex in components — is
  unchanged and now enforced across four palettes instead of one.
- Any future theme is added the same way: a new `[data-theme="..."]` block
  overriding only color tokens, checked for contrast, with severity hues
  kept consistent with the other themes. It does not require touching
  component code, because components never reference a color literal.
- The documentation copy of the token file must be kept byte-identical to
  the shipped one by whoever changes either — this is a manual discipline,
  not currently enforced by tooling. If the two drift again, that is worth
  fixing with an automated check rather than another manual reminder.
- A settings screen now exists as an app destination; expanding it with
  content unrelated to appearance is a separate decision, not implied by
  this one.
