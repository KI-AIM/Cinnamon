# Design tokens

Source of truth: `cinnamon-platform/cinnamon-frontend/src/app/styles/{colors,variables,sizes,material-colors}.less`.
These are LESS variables, not CSS custom properties — import the file with `@import` and use the
`@name` directly; there's no runtime/JS access to them.

## Semantic colors (`colors.less`)

```less
@color-primary: #3b7dd8;        // also the Material theme primary (shade 700), kept in sync manually
@color-secondary: #728ca3;
@color-highlight: #627d98;
@color-background: #ebeff2;     // page canvas (.page-content background)
@color-foreground: #ffffff;     // card/content surface background
@color-unhighlighted: #394867;
@color-unimportant: #b0b8c1;
@color-links: #1d72b8;
@color-disabled: #F0F0F0;

@color-side: #212A3E;           // sidenav background
@color-side-complete: #74C69D;

@color-error: #e63946;
@color-warning: #ffa726;
@color-success: #007e33;
@color-info: #9cb1e7;

@color-text: #333f55;
@color-shadows: #1f2a3c;
@color-border: #d0d5db;

@color-primary-icon: #506d85;   // default mat-icon color (.icon-enabled)
@color-secondary-icon: #7d92a5; // muted/disabled icon color (.icon-disabled)

@color-white: #fff;
@color-black: #3E3E3B;

@color-gray-background: #f5f5f5; // tinted bar background, e.g. .box-header
```

Grayscale (mirrors Bootstrap's classic grayscale, derived programmatically):
```less
@color-gray-darker: #222;
@color-gray-dark:   #333;
@color-gray:        #555;
@color-gray-light:  #777;
@color-gray-lighter: #eee;
```

Status background/border/text triads, built from the full Material Design palette
(`material-colors.less`, `@mdc-<hue>-<50..900>`):
```less
@color-success-background: @mdc-light-green-100;
@color-success-border:     @mdc-light-green-300;
@color-success-text:       @mdc-light-green-900;

@color-failure-background: @mdc-red-100;
@color-failure-border:     @mdc-red-300;
@color-failure-text:       @mdc-red-900;

@color-conflict-background: @mdc-yellow-100;
@color-conflict-border:     @mdc-yellow-300;
@color-conflict-text:       @mdc-yellow-900;
```
Use these triads (not `@color-success`/`@color-error` directly) for anything with a colored
background + border + text combination, e.g. status banners, diff-style highlighting. `app-info-card`
already wires `typeClass="card-success"|"card-failure"|"card-warn"` to these — prefer reusing it
over reapplying the triad by hand.

## Spacing, radius, shadow (`variables.less`)

```less
@transition-time: 0.25s;
@transition-value: @transition-time ease-in-out; // full transition shorthand

@box-shadow-material-sm: 0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.24);
@box-shadow-material-md: 0 3px 6px rgba(0,0,0,0.16), 0 3px 6px rgba(0,0,0,0.23);
@box-shadow-material-lg: 0 10px 20px rgba(0,0,0,0.19), 0 6px 6px rgba(0,0,0,0.23);
@box-shadow-material-xl: 0 14px 28px rgba(0,0,0,0.25), 0 10px 10px rgba(0,0,0,0.22);
@box-shadow-material-xxl: 0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22);

@shadow-box: rgba(100, 100, 111, 0.2) 0 1px 5px 0;  // the standard flat card/box shadow

@bs-border-radius: 0.375rem;  // Bootstrap's own default, present for components that use it
@border-radius: 10px;         // content cards / background containers
@border-radius-sm: 5px;       // interactive elements (buttons, inputs, chips)
@border-radius-pill: 500000px;
@border-width: 0;

@element-breakpoint: @b5-screen-md-min;  // 768px — the app's general "get smaller" breakpoint
@phone-breakpoint: 500px;                // narrower breakpoint for phone-specific overlap fixes

@ui-font-size: 150%;
@ui-font-size-sm: 110%;
```

`@box-shadow-material-*` are the Material elevation shadows (used e.g. on the sidenav);
`@shadow-box` is the flatter, more common shadow used on ordinary `.box`/card containers —
default to `@shadow-box` unless you're specifically trying to evoke Material elevation.

## Breakpoints (`sizes.less`)

Mirrors Bootstrap 5's breakpoint values as LESS variables, so hand-written `@media` queries in this
app line up with Bootstrap's own responsive classes:
```less
@b5-screen-xs: 0px;    @b5-screen-sm: 576px;  @b5-screen-md: 768px;
@b5-screen-lg: 992px;  @b5-screen-xl: 1200px; @b5-screen-xxl: 1400px;
```
Each also has `-min`/`-max` variants (e.g. `@b5-screen-md-min` = `768px`, `@b5-screen-md-max` = `991px`).
Prefer `@element-breakpoint`/`@phone-breakpoint` from `variables.less` in new component media queries
rather than picking a raw breakpoint value directly — they're the two breakpoints the rest of the
app actually uses.

## Material theme

Defined once in `src/app/styles/angular-material.scss` — a custom M2 (legacy Material Design 2)
theme, light, density 0. Primary palette shade 700 (`#3b7dd8`) is manually kept in sync with
`@color-primary` (there's a code comment saying so — if `@color-primary` ever changes, this file
needs a matching edit). Accent is Material's stock pink (`A200`/`A100`/`A400`). Warn shade 600 is
`#e63946`, matching `@color-error`.

Don't edit this file for ordinary component work — use `color="primary"` / `color="accent"` /
`color="warn"` on Material components and the theme applies automatically. The stock
`indigo-pink` prebuilt theme is loaded first in `angular.json` and then overridden by this file, so
if a Material component looks like the wrong (indigo) color, it's almost always because
`angular-material.scss` isn't loaded yet in the style pipeline, not because a value is wrong.
