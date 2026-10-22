# Player design comparison — 0.3.0

Date: 2026-10-22
Target: Existing Android BetterLyrics Mobile player, using the official Apple Music references listed in docs/APPLE-UI-REFERENCE.md.

Reference and rendered player screenshots were inspected directly. Phone comparisons use the app content inside the pictured phone; wide comparisons use the full-screen player content rather than browser chrome. Comparison fixtures use original sample lyrics and cover art, so this is a structural/typographic comparison rather than a pixel match of a copyrighted song screenshot.

## Checked

- Phone: small artwork and compact metadata, no app name, top handle, large active lyric, softened previous/upcoming lyrics, seek/remaining-time row, double-triangle transport icons, volume and bottom action row.
- Wide: larger artwork and controls on the left, lyrics on the right, aligned metadata, volume and bottom actions. Short landscape uses a smaller cover without overlap.
- Both lyric and album states: tested at 360×740, 412×915, 812×375, 1280×800 and 800×1280. No horizontal overflow or metadata/control overlap.
- Actual menu, volume, source, transport, sound and view-toggle interactions are connected to the existing Android bridge. No decorative fake playback controls were added.

## Resolved

P1: Utility metadata and search/overlay/import actions competed with the reference layout. Moved them to the menu while retaining access.
P1: The old player lacked the reference volume row and remaining-time display. Added a real native-volume bridge and a preview-aware remaining-time label.
P2: Previous/next icons and cover/player proportions differed. Replaced selected controls with licensed filled icons and resized spacing/artwork.
P2: Returning from nested menu search could focus a hidden button. Focus now returns to a visible control.

## Expected differences

P3: Android glyph shapes and open-source icons differ from Apple fonts/SF Symbols. The sound connection button and source selector reflect this app's capabilities. Apple-specific routing, favorites and vocal-removal controls are not faked. These differences are documented rather than described as exact copies.

Browser/native-bridge verification passes. Hardware playback and volume remain unverified on an Android device. This report concerns the bundled production WebView frontend and does not claim a deployed Product Design prototype or a frame-for-frame Apple animation reproduction.

final result: passed
