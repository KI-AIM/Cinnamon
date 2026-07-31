---
name: cinnamon-frontend-design
description: Guidance for building or restyling Angular UI in cinnamon-frontend so it matches the existing (undocumented) design system - LESS design tokens, the custom Material M2 theme, established dialog/card/button/table/layout patterns, and especially configuration/settings forms (the `.config-input-row*` and `.settings-label`/`.settings-info` classes used throughout wizards and settings pages). Use this whenever adding or changing anything visual in cinnamon-frontend - a new page, component, dialog, form, config field, settings field, card, table, chart, or a restyle of an existing one - even if the user doesn't say "design" or "style guide", e.g. "add a settings page", "add a config option for X", "build a confirmation dialog", "show this as a card", "make a table of X", "add an icon next to Y".
---

# Cinnamon frontend design

`cinnamon-frontend` (Angular 19 + Angular Material + Bootstrap 5 + ngx-echarts) has a consistent
visual language, but it was never written down — it lives only in the existing code. This skill
is that write-up. The goal when adding new UI is to make it look like it was written by the same
person who wrote the rest of the app, not to introduce a new pattern that happens to also work.

Read `references/tokens.md` for the full list of color/spacing/breakpoint variables before writing
any component styles — guessing a hex value or a `border-radius` instead of using the existing
token is the single most common way new UI ends up looking slightly "off".

## Non-negotiable structural conventions

These aren't style preferences, they're enforced by `angular.json` schematics defaults and by what
the rest of the codebase does — deviating breaks consistency immediately and is easy to spot in review:

- **NgModule components, not standalone.** `angular.json` sets `"standalone": false` as the default
  for generated components/directives/pipes, and every existing component follows it. Angular 19
  defaults to standalone components — don't let that default win here. New components must be
  declared in an existing `NgModule` (usually `shared.module.ts` or the owning feature module).
- **`.less` stylesheets, not `.scss`.** Also an `angular.json` schematics default. The only `.scss`
  file in the app is `angular-material.scss` (the Material theme itself, which needs Sass `@use`)
  — don't touch it for ordinary component work.
- **Three files per component**: `name.component.ts` / `name.component.html` / `name.component.less`,
  wired with `templateUrl`/`styleUrl` (not inline `template`/`styles`). The one exception is the
  chart family (`chart-calendar`, `chart-density`, …), which subclasses a shared `ChartComponent`
  and reuses its template — see "Charts" below.
- **Selector prefix `app-`, kebab-case.** `app-info-card`, `app-user-settings`, etc. File/folder
  names are kebab-case too (`feature/pages/page-name/page-name.component.ts`).

## Design tokens

All shared tokens are LESS variables under `src/app/styles/` — `colors.less`, `variables.less`
(spacing, radius, shadow, breakpoints), `sizes.less` (Bootstrap 5 breakpoint values), and
`material-colors.less` (the full classic Material Design palette, `@mdc-<color>-<50..900>`).
Import what you need with a relative path from the component's own folder, e.g. from
`src/app/features/foo/pages/bar/`:

```less
@import "../../../../styles/colors.less";
@import "../../../../styles/variables.less";
```

Never hardcode a hex color or a `border-radius`/`box-shadow` value that already has a token — see
`references/tokens.md` for the full list. The ones you'll reach for constantly:

- `@color-primary` (`#3b7dd8`), `@color-error`, `@color-warning`, `@color-success` — status/semantic colors
- `@color-background` (page canvas), `@color-foreground` (card/content surfaces), `@color-text`
- `@border-radius` (10px, for content cards/boxes), `@border-radius-sm` (5px, for interactive elements)
- `@shadow-box` — the standard drop shadow for a `.box`/card-style container
- `@element-breakpoint`, `@phone-breakpoint` — for the app's few responsive `@media` rules

Angular Material's own theme is defined once in `angular-material.scss` (a custom M2 palette,
primary shade 700 = `@color-primary`, kept in sync by a code comment — don't recolor Material
components by hand). Just use `color="primary"` / `color="accent"` / `color="warn"` on Material
components and the theme applies itself.

## Component patterns

Match these exactly rather than improvising a new but equally-valid way to do the same thing —
consistency is the whole point.

**Buttons.** `mat-raised-button` is the default for essentially every button in the app — primary
CTAs (dialog confirm, main form submit) and secondary row-level actions ("Change username", "Change
password" style rows) alike; there's no separate "secondary" button variant in use (`mat-stroked-button`
appears in zero templates app-wide, so don't reach for it as a lower-emphasis alternative).
`mat-icon-button` is for icon-only actions (table row actions, an inline field-clear button), and
`mat-flat-button` shows up in a small cluster of `data-inspection-*` components — not worth generalizing from, prefer
`mat-raised-button` unless you're extending that specific family. Always pass an explicit
`color="primary"` or `color="warn"` — never leave it unstyled.

**Generic content containers ("boxes").** For a plain content section (settings section, grouped
content, a chart + its metrics), use the hand-rolled `.box` class from `layout.less`, not
`mat-card`:
```less
.box {
    background-color: @color-foreground;
    border-radius: 0.3rem;   // note: hardcoded, not @border-radius (10px) — an existing inconsistency, don't "fix" it in isolation
    color: @color-text;
    padding: 1rem;
    box-shadow: @shadow-box;
}
```
Reserve actual `<mat-card>` for `app-info-card` — the existing wrapper component for inline
alerts/banners, driven by a `typeClass` input (`card-warn` / `card-failure` / `card-success`) that
maps to the success/failure/conflict background-border-text token triads in `colors.less`. If you
need an alert banner, reuse `app-info-card` rather than composing a new one.

**Boxes: headers and sections.** A `.box` almost always starts with a `.box-name` header — a bold
line with a bottom border, either a plain `<div class="box-name">Title</div>` or, when the box is
the primary heading of its area (e.g. a whole page section, not one of several dialog sub-sections),
a semantic `<h2 class="box-name">Title</h2>` (see `user-home-page`). Content follows directly below
it — `.mb-3`-wrapped settings fields, `.config-input-row`s, a `<table>`, or a nested `app-*`
component — there's no extra wrapper needed between the header and the body.

When a box needs more visual weight than a plain title line — an icon, a distinct header bar, the
kind of prominence a workstep's expansion-panel header has — use `.box-header` instead of
`.box-name`. It's a flex row with a tinted background (`@color-gray-background`) that spans the
full width of the box (it cancels out `.box`'s own padding with negative margins, so its corners
line up with the box's rounded corners). Two optional modifiers, combinable with `.box-header` and
with each other, cover the common variations:

- `.box-header-expanded` sets `height: var(--mat-expansion-header-expanded-state-height)` — the
  same CSS var Material applies to an expanded `mat-expansion-panel-header` — so the box-header
  matches a workstep header's height exactly when the two need to line up visually.
- `.box-header-danger` swaps in the failure color triad (`@color-failure-background` at 40% alpha
  background, `@color-failure-text` text) for a destructive/warning section. Because it sets
  `color` on the header itself, a plain `<mat-icon>` inside inherits that red for free (Material
  icons use `currentColor`) — you don't need an `.icon-warning`/`.icon-enabled` wrapper on top of
  it for the color to be right; those classes set an explicit `color` that would override the
  inherited red instead of blending with it.

```html
<div class="box">
  <div class="box-header box-header-expanded">
    <span class="box-header-icon icon-enabled"><mat-icon>shield</mat-icon></span>
    <span>Section title</span>
  </div>
  <!-- body content -->
</div>

<div class="box">
  <div class="box-header box-header-expanded box-header-danger">
    <span class="box-header-icon"><mat-icon>warning</mat-icon></span>
    <span>Danger zone</span>
  </div>
  <!-- body content -->
</div>
```
`.box-header-icon` (`display: flex`) is just the layout wrapper for the icon slot, not a color
class. For a plain `.box-header`, still pick the color with the usual semantic wrapper
(`.icon-enabled`, `.icon-warning`, `.icon-disabled`); for `.box-header-danger`, leave color to the
inherited red as shown above. See `user-settings` for both variants side by side ("Account &
security" uses the plain `.icon-enabled` form, "Danger zone" the `-expanded -danger` combo). Use
`.box-name` by default; reach for `.box-header` specifically when the box is a major, icon-worthy
section rather than a plain grouped-fields container.

To group multiple `.box` sections on a page or in a dialog, wrap them in `.vertical-boxes`
(`display: flex; flex-direction: column; gap: 1rem;`) — this is the standard top-level layout for
any box-based page (`admin-page`, `project-settings`, `data-inspection-attribute-details`). Boxes
can also sit side by side: nest a plain `display: flex; flex-direction: row; gap: 0.5rem;` div
inside `.vertical-boxes` and put multiple `.box`es in it (see `data-inspection-attribute-details`,
which lays out an attribute-info box next to a metrics box this way). There's no dedicated
"horizontal boxes" class for this — it's always a one-off inline/`.less` flex row.

Most boxes have exactly one `.box-name` and represent one section; split into separate `.box`
elements (grouped by `.vertical-boxes`) by default. The exception is a set of tightly related
sub-sections that belong in one visual container — there, repeat `.box-name` *inside* a single
`.box` to introduce each sub-section (see `project-export`'s dialog, which has one `.box` with
three `.box-name` headers: "Configurations", "Datasets", "Evaluation"). Prefer separate boxes
unless you have a specific reason the sections must visually read as one card.

A reusable "section" component doesn't have to render its own `.box` wrapper — it can just render
`.box-name` + content and let the *parent* apply `class="box"` directly to the component's host
element (see `app-metric-configuration`, used as `<app-metric-configuration ... class="box">`
inside `project-settings`'s `.vertical-boxes`). Use this shape when the section is meant to be
dropped into an existing box-based layout rather than always owning its own box.

A few pages use their own specialized header row instead of `.box-name`, built from the same
"flex row, last child pushed right" idiom (`> :last-child { margin-left: auto; }`) rather than a
new one: the execution page's per-process boxes use `.box.step-wrapper` with a `.step-head` title
row (name + status + action icons) followed by a `.step-body`; the page-level action bar above them
uses `.stage-head` (the `.box` styling plus that same header-row layout, but as a toolbar of
buttons/status rather than a content box). Reach for these two specifically only if you're
extending the execution/stage-tree flow — elsewhere, `.box-name` is the right default.

`user-settings` is a good reference for combining the shared box pieces above with a page-specific
row layout that doesn't have a shared equivalent yet. Each section is an ordinary `.box` with a
`.box-header` (see above) for the title/icon, but the settings rows themselves use a local
`.setting-row` class (label + description on the left, one action button on the right — a plain
flex row, since `.config-input-row-label` deliberately forces the label onto its own line, which
isn't wanted here), wrapped in a local `.settings-divided` container that turns a plain list of
`.setting-row`s into a divided list: each row gets top/bottom padding and a `border-bottom` except
the last, and the first/last rows adjust their padding to offset the box's own 1rem padding so the
dividers sit flush against the box edges. Neither `.setting-row` nor `.settings-divided` is in
`layout.less` — they're local to `user-settings.component.less` — but they're a reasonable pair to
copy into another settings-style page that needs a divided list of label/action rows rather than
wizard-style `.config-input-row`s.

**Dialogs.** Never a separate dialog component class opened programmatically with a data model —
always an inline `<ng-template #someDialog>` in the host component's HTML, opened by injecting
`MatDialog` and calling `.open(someDialog, {...})`. Two shapes cover essentially every dialog in the
app; pick by what the dialog is doing, not by habit:

- **Confirm/change dialogs** (change a setting, export something, confirm a destructive action) —
  titled, with the body wrapped in `.box`:
  ```html
  <h2 mat-dialog-title>Title</h2>
  <mat-dialog-content class="mat-typography dialog-content">
    <div class="box">
      <!-- dialog body -->
    </div>
  </mat-dialog-content>
  <mat-dialog-actions align="end">
    <button mat-raised-button mat-dialog-close color="primary">Cancel</button>
    <button mat-raised-button color="primary" [disabled]="!form.valid">Confirm</button> <!-- color="warn" if destructive -->
  </mat-dialog-actions>
  ```
  The extra `dialog-content` class (background tint, top padding) is optional and tends to travel
  with the `.box` wrapper — see `user-settings`'s three dialogs and `project-export`.
- **Info/inline-editor popups** (read-only details, editing a list opened from an inline icon) —
  no title, no `.box`: just `<mat-dialog-content class="mat-typography">` and a single `Close`
  action (see `metric-info-table`, `configuration-input-array`).

Width is set per dialog, not from a shared token — `width: '60%'` is the dominant choice for
ordinary content dialogs (config editors, info popups, `app.component`'s generic dialog host); a
narrower fixed pixel width (`'300px'`–`'500px'`) plus explicit `autoFocus`/`disableClose`/
`hasBackdrop` shows up specifically for compact account-menu-style popovers anchored near their
trigger (`user-settings`, `user-center`). Match whichever your dialog's content resembles rather
than copying either set of numbers by default.

**Tables.** `mat-table` + `mat-paginator`, filters via `mat-form-field`/`mat-select`, the whole
thing wrapped in a `div.mat-elevation-z8`, header row `sticky: true`. Add custom row/column classes
(pattern: `errorRow`/`errorColumn` in the existing `data-table` component) for domain-specific
highlighting rather than inline styles.

**Expansion / step content.** `mat-expansion-panel` with `matExpansionPanelContent` + `ng-content`
projection is the standard for collapsible or step-by-step content (see `workstep-box`). For a
panel that should look expandable but not actually toggle, use `[hideToggle]` plus a
`.workstep-expansion-panel-static`-style class that disables pointer events on the header, rather
than removing the expansion panel entirely.

**Icons.** Default to `<mat-icon>` with a Material Design icon ligature name, e.g.
`<mat-icon>settings</mat-icon>`, `<mat-icon fontIcon="error"></mat-icon>`. Wrap icons in the
existing semantic classes for state coloring instead of inline styles:
`.icon-enabled` (`@color-primary-icon`), `.icon-disabled` (`@color-secondary-icon`),
`.icon-warning` (`@color-warning`). Bootstrap Icons (`bi-*`) are installed but essentially unused
in this app (one legacy CSS `content:` glyph) — don't reach for them for new UI.

**Bootstrap usage.** `ng-bootstrap` (`NgbModule`) is imported in `app.module.ts` but nothing in the
app actually uses `ngbModal`/`ngbNav`/`ngbTooltip`/etc. — don't introduce it. Bootstrap is used
only as raw utility classes for generic flex layout and spacing: `d-flex`, `flex-row`/`flex-column`,
`justify-content-*`, `align-items-*`, `w-100`, `mb-3`, `me-2`, etc. Bootstrap's 12-column grid
(`row`/`col-*`) is not used anywhere in the app — use flexbox (utility classes or hand-written
`display: flex` in the component's `.less`) for layout instead. CSS Grid appears only for small,
genuinely two-dimensional layouts like a key/value info table (`display: grid; grid-template-columns: min-content auto;`)
— don't reach for it as a general layout tool.

**Charts.** New charts should extend the shared `ChartComponent` base
(`src/app/shared/components/chart/chart.component.ts`) and reuse its template rather than wiring
up `ngx-echarts` from scratch — it already provides the standard grid/toolbox styling, a `simple`
input for compact/dashboard display, and zoom support. Pull colors from
`StatisticsService.colorDefinitions` / `getColorScheme(name)` rather than hardcoding a palette in
the new chart, unless the chart has a fixed real-vs-synthetic semantic (like `chart-calendar`,
which hardcodes its 2-color scale deliberately).

## Configuration rows and form inputs

Config forms and settings screens are a large part of this app (upload/data-configuration wizards,
algorithm parameters, project/user settings), and they're built almost entirely from a handful of
classes defined once in `layout.less` — `.config-input-row*`, `.settings-label*`, `.settings-info`,
`.cinnamon-error`/`.cinnamon-mat-error`, `.input-height`, `.hide-mat-error-gap`. Components reach
for these classes directly rather than through a shared wrapper component, so match them by class
name in new templates rather than inventing an equivalent layout by hand.

There are two established flavors, and which one to use depends on what the row is for:

**1. Config row (`.config-input-row`)** — for workstep/wizard steps and any backend-driven or
multi-part configuration (data source setup, algorithm parameters, dataset fetch actions). This is
what `app-configuration-input` (the generic renderer for backend-defined config schemas) itself is
built from, but plenty of feature templates hand-roll the same markup directly for a handful of
fixed fields (e.g. `upload-file`'s data-source and FHIR-server steps) — both are correct, reuse
`app-configuration-input` when rendering a dynamic/backend-defined schema, hand-roll the row when
it's a small fixed set of fields specific to one page.

```html
<div class="workstep-part">
  <div class="config-input-row">
    <div class="config-input-row-label">
      Question or label text for the row
    </div>

    <mat-form-field class="config-input-row-field">
      <mat-label>Field label</mat-label>
      <input matInput formControlName="fieldName">
      <mat-error>Validation message</mat-error>
    </mat-form-field>

    <!-- OR, instead of a field, an inline action: -->
    <!-- <div class="config-input-row-button"><button mat-raised-button color="primary">Action</button></div> -->

    <div class="config-input-row-info">
      <mat-icon class="icon-enabled" (click)="fieldInfo.handleClick($event)">info</mat-icon>
    </div>
  </div>
</div>
```

- `.config-input-row` is a wrapping flex row (`column-gap: 16px`, `row-gap: 4px`) — children lay
  out left to right and wrap onto new lines on narrow screens for free, don't add your own
  responsive handling.
- `.config-input-row-label` is full width (forces the field/button/info onto their own line below
  it) — use it for a question or instruction, not a short field caption (that's `.settings-label`,
  see below). It's optional: `app-configuration-input` instead uses a fixed-width side label
  (`d-flex flex-column input-height justify-content-center`, `width: 10%; min-width: 100px;`) when
  the row doesn't need a full sentence.
- `.config-input-row-field` (`flex: 1 1 auto`) goes directly on the `mat-form-field`, or on a
  wrapping `<span>`/custom component if the field isn't a single `mat-form-field`.
- `.config-input-row-button` (`flex: 0 1 auto`) is the alternative to a field when the row's action
  is a button rather than an input (e.g. "fetch dataset").
- `.config-input-row-info` is the trailing column — almost always either an info icon
  (`<mat-icon class="icon-enabled">info</mat-icon>`, opening a details dialog — see
  `configuration-input-info` for the `.info-table` two-column dialog layout) or the "reset to
  default" affordance (`<mat-icon class="icon-enabled">replay</mat-icon>` when enabled,
  `class="icon-disabled"` when not). `.input-height` (56px) keeps icon columns vertically aligned
  with the Material input next to them — it's applied automatically when the field can show an
  error subscript, but add it by hand on icon/button columns you write yourself.
- `.config-input-divider` (a subtle horizontal rule) is available for separating rows/sections but
  isn't currently used anywhere — most rows are separated by `.workstep-part`'s own bottom margin
  instead, so only reach for the divider if a workstep genuinely needs a rule inside one part.

**2. Settings field (`.settings-label` / `.settings-info`)** — for simple, static settings forms
(account/user settings, project settings) that aren't part of a multi-step wizard:

```html
<div class="mb-3">
  <div class="settings-label settings-label-required"
       [ngClass]="{'settings-label-error': form.get('name')!.hasError('required')}">
    Field caption
  </div>
  <mat-form-field class="w-100 hide-mat-error-gap">
    <input matInput formControlName="name">
    <mat-error *ngIf="form.get('name')!.hasError('required')">This field is required.</mat-error>
  </mat-form-field>
</div>
```

- `.settings-label` is a small bold caption placed *above* the field (unlike `<mat-label>`, which
  floats inside it) — use `.settings-label-required` to add a trailing `*`, and either toggle
  `.settings-label-error` yourself or rely on the automatic `:has(+ .mat-form-field-invalid)` color
  change when the label directly precedes an invalid field.
- `mat-form-field` gets `.w-100` (full width) and `.hide-mat-error-gap` (removes the space
  Material normally reserves for the error subscript when there's no error) — always pair the two
  in this pattern.
- `.settings-info` is for a longer explanatory/warning paragraph under a field or group of fields
  (e.g. "Deleting the project is irreversible…"), not a per-field caption.

**Error text outside a `mat-form-field`.** Angular Material's `<mat-error>` only works inside a
`mat-form-field`. When you need error-styled text somewhere else — a manual validation message for
something that isn't a form field (file upload, a custom control) — use `.cinnamon-error` for a
plain standalone message, or `.cinnamon-mat-error` when the text needs to sit pulled up directly
under a config row's field (it's the same styling with a negative top margin for that positioning).

## Page layout

The app shell (`app.component.html`) is hand-rolled flexbox + LESS, not `MatSidenav`/`MatToolbar`:
a fixed `25vw` dark sidenav (`.content-left`, `@color-side`) hosting `app-navigation`, and a `75vw`
`.content-right` column containing a title bar and a `.page-wrapper` > `.page-content` (40px
padding) that hosts the routed page via `<router-outlet>`. Don't add Material sidenav/toolbar to a
new page — everything routes into `.page-content` and inherits the shell for free.

Within that, pick whichever of these existing page patterns fits, rather than inventing new
page-level chrome:
- **Centered column** — for a single settings-style page: `width: min(100%, 840px); margin: 0 auto;`
  (see `user-settings`).
- **`.box` / `.vertical-boxes` stack** — for a page that's a vertical sequence of grouped content
  sections.
- **Workstep expansion chain** — for a multi-step wizard-style flow, chaining `mat-expansion-panel`
  "workstep" sections (see `layout.less` `.stage-tree`/`.step-*` classes).

## Quick checklist before finishing new UI

- [ ] Component is NgModule-based (`standalone: false`) and declared in the right module
- [ ] Stylesheet is `.less`, imports the relevant token file(s) by relative path
- [ ] No hardcoded hex/shadow/radius that has an existing token (check `references/tokens.md`)
- [ ] Buttons use `mat-raised-button` (or `mat-icon-button` for icon-only actions) with an explicit `color`
- [ ] Layout uses flexbox (Bootstrap utility classes or hand-written), not Bootstrap grid columns
- [ ] Icons are `<mat-icon>` with semantic color classes, not `bi-*` or inline `style=`
- [ ] Reused an existing pattern (card/dialog/table/expansion/chart) instead of a new bespoke one
- [ ] Config/settings fields use `.config-input-row*` (wizard/dynamic config) or
      `.settings-label`/`.settings-info` (static settings form) — not a one-off layout
