# Changelog

This log describes repository changes without assigning unverified historical release dates.

## 0.3.1

- Independent 0–100% opacity in 10% steps, with new strokes starting at 50%; pattern strength remains separate.
- Seven modes blend with the actual scene behind eligible blocks, with painted-layer composition and opaque foreground depth testing.
- Rendering refinements for neighboring faces, visibility, fog, framebuffer resizing, resource reload, and invalid captured-depth handling.
- Face-adjacent eligible painted cubes with nonzero opacity form one exterior region: shared internal faces are hidden across different materials/settings; zero-opacity neighbors expose boundaries. Independent layers require air space.
- Conservative model eligibility: simple full cubes with one opaque static surface per face; layered models such as normal grass blocks, partial-face models, and textures containing transparency are rejected.
- Format 3 persistence includes opacity; readable legacy strokes without opacity default to 100%.
- Signed height encoding, retained unsupported legacy entries, and read-only preservation of unknown future formats.
- Server-validated edits, dimension-aware synchronization, bounded chunk snapshots, and a non-evicting 4096-entry chunk limit.
- Portable Java 21 builds and isolated Fancy/Fabulous Minecraft smoke tasks with fresh terminal PASS requirements.
- Three original Minecraft art studies, four native gallery captures, and an isolated reproducible showcase fixture.
- Expanded installation, usage, compatibility, testing, contribution, and release documentation.

## Earlier 0.1 / 0.2 data

The current reader supports readable legacy entries. Older data without opacity loads at 100%. Legacy height aliasing above y=191 may have already lost information; upgrade cannot reconstruct missing original heights. See [upgrade guidance](MOD_USAGE_GUIDE.md#saving-and-upgrading). This compatibility note does not establish historical public release contents.
