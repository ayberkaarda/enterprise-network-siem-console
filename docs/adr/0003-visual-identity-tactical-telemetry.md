# 0003 — Visual identity: dark, data-first SOC console

Status: Accepted

## Context

The current frontend uses default-looking styling: generic card shadows, a
conventional blue/red palette, no consistent type scale, and no design token
layer — colors and spacing are ad hoc per component. A security console is
read by an analyst for hours at a time, scanning dense tables and status
indicators under pressure; the visual language needs to support that, not just
look presentable in a screenshot. It also should not read as an off-the-shelf
admin template.

## Decision

Adopt a dark-only, data-first visual identity with one governing rule: **color
carries meaning, nothing else.**

- **Surface colors** are a gunmetal/green-gray scale, not neutral gray or
  black: `--color-bg #0D1012`, `--color-surface #14181B`,
  `--color-surface-2 #1A2024`, `--color-surface-3 #212930`. Text uses a
  phosphor-white family: `#E4EAE7` (primary), `#9AA8A4` (secondary), `#64716E`
  (tertiary/disabled).
- **One neutral accent**, `--color-accent #EFE7D3` (a warm bone/paper tone),
  is used for selection state, focus rings, the active nav item, and primary
  buttons — and nothing else. It deliberately avoids the generic cyan/violet
  gradient look common to security-product marketing sites.
- **Severity has its own palette**, used only for status, never for chrome:
  critical `#FF4D6D`, high `#FF9F45`, medium `#F5D65B`, low `#6CA6FF`, info
  `#8E9BA6`. Each severity is additionally encoded by shape (triangle,
  diamond, square, dot, line) so the distinction does not depend on color
  perception alone. Device health uses a separate green, `#4FD08A`, also
  reserved for that one meaning.
- **Typography**: headings and large numeric readouts (KPIs, the threat-level
  band) use a condensed grotesque (Barlow Condensed); body text uses its
  regular-width sibling (Barlow) so the two don't clash; every identifier,
  IP address, timestamp, and tabular number uses a monospace face with tabular
  figures (IBM Plex Mono), so columns of numbers actually align and `l`/`I`/`1`
  stay distinguishable.
- **Layout**: a fixed-width left rail (not a collapsible hamburger menu) for
  primary navigation, dense table rows (~34px), no drop shadows or gradients,
  a single small corner radius used consistently rather than varied per
  component, and charts that stay visually quiet (muted series, faint grid)
  so a color only draws the eye when it means something is wrong.

## Consequences

- No light theme is planned. This is a deliberate choice for a single-purpose
  operational console, not an oversight — revisit only if a genuine use case
  for a light mode appears (e.g. daytime NOC wall displays with strong
  ambient light).
- Any new UI component must pull colors from the token set above via CSS
  custom properties, not hardcode a hex value. Introducing a new "meaning"
  (a new status category, a new kind of alert) requires picking a new token,
  not reusing an existing severity color for something else.
- Because the severity palette is tested for color-vision-deficiency
  distinguishability and backed by shape coding, dashboards remain usable for
  colorblind users without a separate "accessible mode."
- The accent color is intentionally the only saturated-adjacent color outside
  the severity/health set. Any future request to add a second brand accent
  (e.g. for marketing/landing use) should get its own decision record rather
  than diluting this one.
