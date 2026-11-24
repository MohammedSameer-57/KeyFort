# KeyFort Design Tokens

Specification of visual design tokens for KeyFort web and native client interfaces.

## 1. Color Palette

### Light Theme (Default)
- `--bg: #FCF9F2;`
- `--surface-lowest: #FFFFFF;`
- `--surface-low: #F6F3EC;`
- `--surface: #F0EEE7;`
- `--surface-high: #EBE8E1;`
- `--on-surface: #1C1C18;`
- `--on-surface-variant: #404945;`
- `--outline: #707974;`
- `--outline-variant: #C0C9C3;`
- `--primary: #003629;`
- `--primary-hover: #1B4D3E;`
- `--on-primary: #FFFFFF;`
- `--success-container: #D3E7DD;`
- `--on-success-container: #002117;`
- `--warning-container: #F7E7CE;`
- `--on-warning-container: #3B2404;`
- `--error: #BA1A1A;`
- `--error-container: #FFDAD6;`
- `--on-error-container: #93000A;`

### Dark Theme (`@media (prefers-color-scheme: dark)`)
- `--bg: #131410;`
- `--surface-lowest: #0E0E0B;`
- `--surface-low: #1C1C18;`
- `--surface: #20201C;`
- `--surface-high: #2A2A26;`
- `--on-surface: #E5E2DB;`
- `--on-surface-variant: #C0C9C3;`
- `--outline: #8A938E;`
- `--outline-variant: #404945;`
- `--primary: #9ED1BD;`
- `--primary-hover: #7CBFA7;`
- `--on-primary: #00382A;`
- `--success-container: #1B4D3E;`
- `--on-success-container: #C8FCE7;`
- `--warning-container: #673831;`
- `--on-warning-container: #FFDAD5;`
- `--error: #FFB4AB;`
- `--error-container: #93000A;`
- `--on-error-container: #FFDAD6;`

## 2. Geometry & Spacing
- Radii:
  - `--radius-xs: 4px;`
  - `--radius-sm: 8px;`
  - `--radius-md: 12px;`
  - `--radius-lg: 16px;`
  - `--radius-pill: 9999px;`
- Spacing Scale:
  - `--space-1: 4px;`
  - `--space-2: 8px;`
  - `--space-3: 16px;`
  - `--space-4: 24px;`
  - `--space-5: 32px;`
  - `--space-6: 48px;`

## 3. Typography
- Font Stacks:
  - Headings / Display: `ui-serif, Georgia, serif` (weight: 600)
  - Body / Title / Label: `system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`
  - Monospace: `ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace`
- Type Scale:
  - Display: 40px / line-height 46px (mobile: 30px / 36px), weight 600
  - Headline: 22px / line-height 28px, weight 600
  - Title LG: 18px / line-height 24px, weight 600
  - Title MD: 16px / line-height 22px, weight 600
  - Body LG: 16px / line-height 24px, weight 400
  - Body MD: 14px / line-height 21px, weight 400
  - Label: 12px / line-height 16px, weight 500
  - Mono: 13px / line-height 18px, weight 500
