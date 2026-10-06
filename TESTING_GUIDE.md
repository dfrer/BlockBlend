# Testing BlockBlend 0.3.1

[README](README.md) · [Contributing](CONTRIBUTING.md) · [Art experiments](ART_EXPERIMENTS.md)

## Recorded 0.3.1 verification

On 6 October 2026, the release candidate passed **34 unit tests**, **216 Fancy runtime checks with 41 captures**, and **228 Fabulous runtime checks with 45 captures**. Both native runs checked connected-surface culling, zero-opacity gaps, erase/repaint, independent air-separated layers, foreground occlusion, save/reopen, resource reload and actual window resizing. The four new boundary pixel comparisons matched their references exactly in both modes. Fabulous also exercised missing/mismatched captured-depth guards and recovery.

The README gallery was recaptured from the same frozen production candidate: four native 1600 × 900 images, 321 painted positions, unchanged composition and cameras. The release JAR SHA-256 is `473c3f56a6f656fa8b99e05016ca63531fabbf2cb409db17ed6c454b884bec3f`; release assets include checksums. These local results have the compatibility boundaries below. GitHub's [Build runs](https://github.com/dfrer/BlockBlend/actions/workflows/build.yml) provide separate CI evidence for the published source commit.

## Build and unit tests

Use a **Java 21 JDK**. From the repository root:

```powershell
.\gradlew.bat build --no-daemon --console=plain
```

```sh
./gradlew build --no-daemon --console=plain
```

The build runs JUnit tests and produces `build/libs/blockblend-0.3.1.jar` plus its sources JAR. Reports are in `build/reports/tests/test/index.html`; machine-readable results are in `build/test-results/test/`. Run `test` alone for a focused unit gate. First use requires dependency downloads.

Tests cover position round trips (vanilla/custom heights and negative chunks), seven reference blend functions, strength and opacity endpoints, Color Dodge/Burn edge cases, malformed/nonfinite settings, packet round trips and bounds, dimension identifiers, chunk snapshot budgets, retry windows, persistence/erase, non-evicting limits, real distinct overlay pixels, legacy defaults, unsupported legacy entries, and preservation of future-format data.

Unit tests establish model/codec behavior. They do not prove that Minecraft draws the correct framebuffer.

Geometry tests accept all six complete outward cube faces and reject partial, malformed, crossed, inward-facing, or nonfinite geometry and every partial-alpha level.


## Real Minecraft smoke checks

The separate `src/smoke` source set launches a test-only mod with the production mod in an isolated integrated-server client:

```powershell
.\gradlew.bat runSmokeClient "-PsmokeGraphics=fancy"
.\gradlew.bat runSmokeClient "-PsmokeGraphics=fabulous"
```

Use `./gradlew` on macOS/Linux. The default graphics mode is Fabulous. A functioning graphical display/OpenGL environment is required; these are not headless unit tests.

Smoke captures use **FOV 70, ambient occlusion off, gamma 1**, and vanilla Fabric/Indigo. The harness asserts the expected player/camera pose and FOV before every capture, so appearance comparisons use matching viewpoints.

| Output | Default location |
| --- | --- |
| Fancy client/world/logs | `build/runtime/smoke/fancy/client/` |
| Fancy JSONL receipts | `build/runtime/smoke/fancy/evidence/` |
| Fancy screenshots | `build/runtime/smoke/fancy/client/screenshots/` |
| Fabulous client/world/logs | `build/runtime/smoke/fabulous/client/` |
| Fabulous JSONL receipts | `build/runtime/smoke/fabulous/evidence/` |
| Fabulous screenshots | `build/runtime/smoke/fabulous/client/screenshots/` |

Each run generates a UUID-named JSONL receipt and screenshots. The Gradle task requires **exactly one fresh receipt with a terminal PASS**; missing, stale, or failed receipts fail the task. Read the receipt and examine captures when diagnosing a failure. Screenshot callbacks can append records after the terminal event.

The harness exercises real scene equations, 0/50/100% opacity, all seven modes, patterned surfaces, erase restoration, touching neighbors, opaque foreground occlusion, multiple painted depths, persistence/restart, chunk/dimension behavior, resource reload, fog, framebuffer resizing, and captured-depth safety guards. Glass comparisons are diagnostics, not a general translucent-ordering qualification. See [OpacitySmoke.java](src/smoke/java/com/example/blockblend/smoke/OpacitySmoke.java) for exact assertions.

Native smoke regressions check connected painted-region boundaries across matching and differing paint/material, zero-opacity gaps, erase/repaint, and chunk boundaries. Independently composited layers use air separation.

Ordinary `runClient` and `runServer` use `build/runtime/client/` and `build/runtime/server/`. Development smoke/showcase classes and test-mod metadata must be absent from the distributable JAR.

## Reproduce the art gallery

```powershell
.\gradlew.bat runShowcaseClient
```

The separate showcase fixture builds a new disposable flat world, validates 321 painted positions against production state/rendering, and captures four native Minecraft frames. Default client data lives in `build/showcase/client/`; each invocation writes a unique receipt/capture folder under `build/showcase/evidence/`.

[ART_EXPERIMENTS.md](ART_EXPERIMENTS.md#reproduce-the-gallery) records exact settings, camera positions, layout coordinates, and optional output/size properties. The fixture rejects ordinary launcher directories and is excluded from the installed mod. Its bounded render-interval sample is specific to a small scene and display scheduling, not a dense-world benchmark.

## Manual acceptance

Use a separate backed-up world/profile and the exact intended release JAR.

1. Craft an applicator and obtain another from Creative Tools. Test main-hand controls, rebinding, the HUD, all modes/patterns, strength, opacity, repainting, and erase.
2. Paint stone, wool, planks, and concrete in front of a colorful backdrop. Compare ordinary solid rendering, 0%, 50%, and 100%; verify that collision and lighting remain normal.
3. Test connected painted cubes from an oblique angle, including different materials/settings and contacts across a chunk boundary. Shared internal faces should be absent when both cubes have nonzero opacity; a 0% neighbor should expose the visible cube's boundary. Check erase/repaint boundaries, air-separated overlapping layers, and opaque foreground occlusion. A transparent overlay drawn over a surviving opaque cube fails acceptance.
4. Unload/reload chunks, change dimensions, save/rejoin, and reload resources. Check edits and neighboring geometry recover.
5. Confirm layered models such as normal grass blocks, partial-face models, textures containing transparency, and other unsupported targets receive a clear message and retain ordinary rendering. Check reach/build-permission restrictions and missing server support.
6. For dedicated multiplayer qualification, run the same version on a server and two clients; verify updates, joins, disconnects, dimensions, and permissions on that deployment.

Record Minecraft/Java/Loader/API versions, mod artifact hash, graphics mode, relevant GPU/driver details, logs, screenshots, and actual outcomes. Redact personal paths, account data, server addresses, and secrets before sharing.

## Verification boundaries

The documented baseline is **Minecraft 1.21.1, Java 21, Loader 0.16.14, Fabric API 0.102.0+1.21.1, vanilla Fabric/Indigo**. Integrated-server smoke success does not qualify remote dedicated-server multiplayer, authenticated launcher installation, every GPU, Sodium/Iris/shaders, alternate packs, general glass/water/particle sorting, or dense-world performance.

Supported targets are simple opaque full cubes with static textures and one opaque surface per face. Layered models such as normal grass blocks, partial-face models, and textures containing transparency are outside this scope, alongside slabs, stairs, glass, fluids, block entities, and animated textures.

The checked-in [build workflow](.github/workflows/build.yml) provides a Linux build/unit gate when run by GitHub Actions. A configured workflow is not evidence of a completed CI run. Release validation must refer to the exact artifact/ref and fresh runtime results.
