---
name: Wisma
colors:
  surface: '#101415'
  surface-dim: '#101415'
  surface-bright: '#363a3b'
  surface-container-lowest: '#0b0f10'
  surface-container-low: '#191c1e'
  surface-container: '#1d2022'
  surface-container-high: '#272a2c'
  surface-container-highest: '#323537'
  on-surface: '#e0e3e5'
  on-surface-variant: '#c3c5d9'
  inverse-surface: '#e0e3e5'
  inverse-on-surface: '#2d3133'
  outline: '#8d90a2'
  outline-variant: '#434656'
  surface-tint: '#b7c4ff'
  primary: '#b7c4ff'
  on-primary: '#002682'
  primary-container: '#0052ff'
  on-primary-container: '#dfe3ff'
  inverse-primary: '#004ced'
  secondary: '#b9c7e4'
  on-secondary: '#233148'
  secondary-container: '#3c4962'
  on-secondary-container: '#abb9d6'
  tertiary: '#b6c6ed'
  on-tertiary: '#20304f'
  tertiary-container: '#566688'
  on-tertiary-container: '#dbe4ff'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#dde1ff'
  primary-fixed-dim: '#b7c4ff'
  on-primary-fixed: '#001452'
  on-primary-fixed-variant: '#0038b6'
  secondary-fixed: '#d6e3ff'
  secondary-fixed-dim: '#b9c7e4'
  on-secondary-fixed: '#0d1c32'
  on-secondary-fixed-variant: '#39475f'
  tertiary-fixed: '#d8e2ff'
  tertiary-fixed-dim: '#b6c6ed'
  on-tertiary-fixed: '#091b39'
  on-tertiary-fixed-variant: '#374767'
  background: '#101415'
  on-background: '#e0e3e5'
  surface-variant: '#323537'
typography:
  display-xl:
    fontFamily: Sora
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Sora
    fontSize: 32px
    fontWeight: '600'
    lineHeight: '1.2'
  headline-md:
    fontFamily: Sora
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Geist
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  label-sm:
    fontFamily: Geist Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: '1.4'
    letterSpacing: 0.05em
  headline-lg-mobile:
    fontFamily: Sora
    fontSize: 28px
    fontWeight: '600'
    lineHeight: '1.2'
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 40px
  gutter: 20px
  margin-mobile: 16px
  margin-desktop: 64px
---

## Brand & Style

This design system is engineered for **Wisma**, a high-end fintech or tech-forward platform that demands a balance between technical precision and premium aesthetics. The brand personality is **Futuristic, Reliable, and Precise**. 

The design style utilizes **Experimental Dark Mode Minimalism** with heavy influences from **Glassmorphism**. It leverages the deep, atmospheric gradients of a midnight sky, punctuated by "electric" blue highlights. This creates a sense of infinite depth and high-speed data processing. The UI should feel like a high-performance instrument—uncluttered, responsive, and authoritative. Space is used intentionally to allow complex data to breathe, while the typeface choices provide a mix of geometric personality and engineering clarity.

## Colors

The palette is derived from deep-sea and astronomical photography, focusing on the interplay between shadow and light.

- **Primary Electric Blue (#0052FF):** Used for primary actions and active states. It provides a high-vibrancy "glow" against the dark background.
- **Midnight Canvas (#020617):** The base background color, providing total darkness for maximum contrast with light elements.
- **Atmospheric Gradients:** Surfaces are rarely flat. They utilize the `secondary` and `tertiary` shades to create subtle depth and container differentiation.
- **Technical White (#F8FAFC):** Pure, high-legibility typography and iconography, ensuring critical financial or technical data remains readable even at small sizes.

## Typography

This design system uses a dual-font strategy to balance character with utility. 

**Sora** is the display typeface. Its wide stance and unique geometric curves provide the "futuristic" identity. It should be used for large headings, balances, and key brand moments.

**Geist** is the technical workhorse. Designed for clarity, it handles all body copy, data tables, and input text. Its monospaced cousin, **Geist Mono** (Label Font), is reserved for secondary metadata, transaction IDs, and timestamps, reinforcing the "built for experts" feel.

Scale headlines aggressively on desktop, but utilize the mobile-specific overrides to prevent line-wrapping issues on smaller devices.

## Layout & Spacing

The layout philosophy follows a **systematic 4px grid**, ensuring all elements align with mathematical precision. 

- **Grid Model:** Use a 12-column fluid grid for desktop and a 4-column grid for mobile. 
- **Rhythm:** Vertical spacing should lean towards the generous side (`lg` and `xl`) to create a premium, uncrowded feel.
- **Containers:** Content is grouped into logical modules. Use the `gutter` spacing consistently between cards to maintain a structured visual flow.
- **Safe Zones:** Always maintain a minimum margin of `16px` on mobile devices to prevent content from hitting the screen edge.

## Elevation & Depth

Depth in this design system is achieved through **Luminance and Blurs** rather than traditional drop shadows.

1.  **Base Layer:** The deepest layer is the `#020617` background with a soft radial glow in the top-left corner.
2.  **Surface Layer:** Cards and containers use a semi-transparent `#112240` with a `backdrop-filter: blur(12px)`. This creates a glass-like effect that allows the background glow to peek through.
3.  **Active Layer:** Elements that are being interacted with or require immediate attention use a subtle `0px 0px 20px rgba(0, 82, 255, 0.3)` outer glow to signify "electric" energy.
4.  **Outlines:** Use 1px borders with 10% white opacity to define edges without adding visual weight.

## Shapes

The shape language is **Pill-shaped (Level 3)**. This softens the technical nature of the typography and colors, making the interface feel more organic and approachable.

- **Primary Buttons & Inputs:** Always use fully rounded (pill) ends.
- **Cards:** Utilize the `rounded-xl` (3rem) setting for a distinct, modern container look.
- **Icons:** Should be encased in circular or pill-shaped backgrounds to maintain consistency with the overall geometry.

## Components

### Buttons
- **Primary:** Pill-shaped, `#0052FF` background, white text. No shadow, but a subtle glow on hover.
- **Secondary:** Transparent background with a 1px white border (20% opacity). Glass-blur effect enabled.

### Cards
- Large corner radius (3rem). 
- Background: `#112240` at 60% opacity.
- Border: 1px top-down linear gradient border (White 10% to White 0%) to simulate a "rim light" effect.

### Input Fields
- Pill-shaped. 
- Background: Deep navy (`#0A192F`).
- Focus State: Border transitions to Primary Electric Blue with a faint glow.

### Chips & Badges
- Used for categories or status.
- Backgrounds should be low-opacity versions of the status color (e.g., Success is Green at 10% opacity) with Geist Mono text.

### Navigation Bar
- A floating pill-shaped bar at the bottom of the screen.
- Intense backdrop blur (20px+) to separate it from the content scrolling behind it.