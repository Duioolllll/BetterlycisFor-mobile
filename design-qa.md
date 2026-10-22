# Design QA — BetterLyrics Mobile 0.4.0

Reference: user-provided BetterLyrics README collage and upstream v1.3.527.0 std/narrow/fs2/fs3 screenshots. Examined original LyricsStyleSettings, LyricsEffectSettings and LayoutProfile. Implementation retains the app's existing playback/lyric features; mobile adaptation is not a pixel-exact WinUI clone.

Evidence inspected together: `build/ui-qa/reference-comparison.png` puts the cropped std window (source pixels 240,106–1712,910; resized to736×402) beside the1280×800 implementation (736×460). Content and aspect ratios differ and are explicitly labelled. Raw references remain outside this public repository. Published original fixture captures: docs/images/lyrics-phone.webp and lyrics-wide.webp.

## Comparison and fixes

- P1: artwork layout container extended through footer. Visible cover/metadata now reserve footer space; player test checks content bounds at five sizes.
- P1: late appearance CSS could obscure manually browsed lyrics. `.lyrics-viewport.exploring` overrides keep them sharp; regression passed.
- P1: modal preview's background could remain keyboard interactive. App subtree now inert while settings are open; dialog focus/return tested.
- P1: initial QA host lacked Korean glyphs. Installed QA-only Noto Sans KR and recaptured; shipped APK continues using device fonts.
- P2 remaining: font rasterization, reference artwork/language/aspect ratio, line lengths and platform effects differ. Original shader/animation timing was not measured dynamically; do not claim exact motion identity.

## Five visual surfaces

Typography: bold left-aligned verse, faded surrounding lines, adjustable weight/size/alignment; Korean glyphs visible in final captures. Spacing/layout: mobile compact cover, landscape album/lyric split, readable active verse and bounded footer; five viewport geometry checks pass. Colors: album-derived multicolor gradient and neutral controls, with configurable brightness/foreground; reference color differences follow different artwork. Assets: real runtime cover path preserved; public QA uses a synthetic sample cover, no copied upstream album. Copy: app title removed from player header, Korean control labels and synthetic original lyric text; translations documented as installation guides only.

Additional regression: auto/immersive/reverse layouts all retain usable album-view geometry at four sizes.

Interaction coverage: original/focus/album presets, align/blur/cover changes, reset, auto-hide/reveal, modal keyboard isolation, time/offset preservation and demo reload. Automated tests:29 UI +9 flow +7 player +5 appearance groups, zero page errors. Viewports360×740,412×915,812×375,1280×800,800×1280 (appearance first four).

Result: automated geometry/function gates passed; static comparison reviewed with explicit P2 differences. Manual cloud-browser session could not be completed (connection/policy block); physical Android UI remains unverified. This is a reviewed mobile adaptation, not an assertion of exact visual/animation equivalence.
