# BlockBlend

**Turn solid Minecraft blocks into windows, layered screens, and color sculptures.** BlockBlend is a Fabric mod for Minecraft Java **1.21.1** that blends a block's surface with the actual scenery behind it. Choose one of seven image-style blend modes, add a pattern, and set independent opacity with a handheld Blend Applicator.

![Seven BlockBlend windows transform a colorful Minecraft backdrop](docs/images/seven-mode-windows.png)

*Multiply · Screen · Overlay · Soft Light · Hard Light · Color Dodge · Color Burn, left to right. Pink concrete, 0% pattern strength, 70% opacity. Captured directly in Minecraft.*

[Download releases](https://github.com/dfrer/BlockBlend/releases) · [Usage guide](MOD_USAGE_GUIDE.md) · [Art experiments](ART_EXPERIMENTS.md) · [Testing](TESTING_GUIDE.md) · [Contributing](CONTRIBUTING.md)

## Install

Version **0.3.1** targets this tested setup:

| Component | Tested version |
| --- | --- |
| Minecraft Java Edition | **1.21.1** |
| Java | **21** |
| Fabric Loader | **0.16.14** |
| Fabric API | **0.102.0+1.21.1** |

1. Install a Fabric profile for Minecraft **1.21.1** using the [Fabric installer](https://fabricmc.net/use/installer/).
2. Download `blockblend-0.3.1.jar` from the [release page](https://github.com/dfrer/BlockBlend/releases). Install the regular JAR; the sources JAR is for developers.
3. Close Minecraft. Put BlockBlend and the matching Fabric API JAR in that profile's `mods` folder. When upgrading, replace the old BlockBlend JAR and keep **only the intended BlockBlend version** in the folder. Then launch the Fabric profile. Fabric's [installing mods guide](https://docs.fabricmc.net/1.21.1/players/installing-mods) explains where to find the folder.
4. For multiplayer, install **the same BlockBlend version and Fabric API on the server and every participating client**. The server stores and validates edits; the client renders them.

Metadata permits newer Loader/API versions, but those versions have not all been verified. Other Minecraft versions, Forge, and NeoForge are unsupported. Back up worlds before upgrading or downgrading; see [save compatibility](MOD_USAGE_GUIDE.md#saving-and-upgrading).

## Make your first blend

Craft one Blend Applicator in a crafting table:

| | | |
| --- | --- | --- |
| Empty | Feather | Empty |
| Empty | Copper ingot | Empty |
| Empty | Stick | Empty |

It is also in the Creative **Tools** tab, or use `/give @s blockblend:blend_applicator` with command permission.

Painting activates the effect on that block immediately after server acceptance. The applicator is the in-game control; there is no separate enable switch.

Place pink concrete a few blocks in front of a colorful wall. Hold the applicator in your **main hand**, reduce pattern strength to **0%**, choose **Screen**, and right-click the concrete. Move around it to see the backdrop through the blended surface. Try Multiply next, then add Stripes. New strokes start at **50% opacity**.

| Default control | Action |
| --- | --- |
| **B** | Cycle the seven blend modes |
| **N** | Cycle Noise, Stripes, Scratches |
| **[ / ]** | Decrease / increase pattern strength by 10% |
| **, / .** | Decrease / increase opacity by 10% |
| **Right-click** | Apply settings to a block |
| **Shift + right-click** | Erase its blend |

The HUD shows your selection. Rebind keys under **Options → Controls → BlockBlend**. Changing settings affects the next application; right-click an existing painted block to repaint it. One application affects the eligible surfaces of that block, rather than only the clicked face.

## Pattern strength and opacity

**Pattern strength** mixes Noise, Stripes, or Scratches into the original block texture. At 0%, the original texture supplies the surface color. **Opacity** controls how much the scene blend contributes: 0% shows the background, 50% mixes it with the result, and 100% applies the full blend equation.

```text
surface = mix(original block texture, pattern, pattern strength)
result  = mix(background, blend(background, surface), opacity)
```

At 100%, a Multiply surface still depends on the background; this is not ordinary opaque paint. Painted layers combine from back to front, and opaque foreground blocks occlude them.

**Touching painted cubes form one exterior region.** Eligible cubes that share a face and both have nonzero opacity hide their shared internal face, even when their materials or paint settings differ. A 0% opacity cube exposes the visible neighbor's boundary. Leave air between surfaces when you want independently blended layers.

| Mode | Typical visual effect |
| --- | --- |
| Multiply | Darkens and tints the backdrop through the surface color |
| Screen | Brightens the backdrop; useful for luminous color screens |
| Overlay | Darkens darker background channels and brightens lighter ones |
| Soft Light | Adds gentler contrast and color shifts |
| Hard Light | Strong contrast controlled by the surface color |
| Color Dodge | Brightens strongly toward clipped highlights |
| Color Burn | Darkens strongly toward clipped shadows |

**Transparency is visual.** The original block keeps its collision, mining behavior, light blocking, and shadows, including at 0% opacity. Erase restores normal rendering. You cannot walk through a painted wall, and its neighbors may remain darker than if it were physically removed.

## Original Minecraft art

These three studies use the production renderer in a disposable Minecraft world. All four gallery images are native **1600 × 900** framebuffer captures; no AI generation, recoloring, or image compositing was used. The gallery contains **321 painted block positions**.

### Seven mode windows

The hero above compares all seven modes over the same concrete color quilt. Every pink-concrete window uses **0% pattern strength and 70% opacity**, making the equation itself visible. [Layout and camera settings](ART_EXPERIMENTS.md#seven-mode-windows).

### Layered screens

![Overlapping patterned BlockBlend screens in Minecraft](docs/images/layered-screens.png)

*Front: pink concrete, Multiply, Stripes 90%, opacity 60%. Rear: light blue concrete, Screen, Scratches 80%, opacity 60%. Their overlap composes in depth order.* [Layout and settings](ART_EXPERIMENTS.md#layered-screens).

### Chromatic ring sculpture

![Three intersecting colored BlockBlend rings](docs/images/chromatic-sculpture.png)

*Orange: Screen, Noise 30%, opacity 70%. Blue: Multiply, Stripes 70%, opacity 60%. Magenta: Soft Light, Scratches 60%, opacity 70%.* [Layout and settings](ART_EXPERIMENTS.md#chromatic-ring-sculpture).

![Overview of the three BlockBlend art studies](docs/images/gallery-overview.png)

Reproduce the gallery with `runShowcaseClient`; [the art guide](ART_EXPERIMENTS.md#reproduce-the-gallery) records coordinates, cameras, graphics settings, and output locations. This development fixture is excluded from the installed mod and creates an isolated world.

## Scope and troubleshooting

Use **simple opaque full cubes with static textures and one opaque surface per face**: concrete, wool, stone, and planks are good starting points. Layered models such as normal grass blocks, partial-face models, textures containing transparency, slabs, stairs, glass, fluids, block entities, and animated textures are unsupported. The client gives a message for unsupported targets; saved edits remain preserved if their targets become unsupported.

| Symptom | Check |
| --- | --- |
| Keys do nothing | Hold the applicator in the main hand, close menus, and check key conflicts in Controls |
| Paint still looks opaque | Confirm 0.3.1 is loaded, lower opacity, and repaint: legacy strokes start at 100%. Use a simple cube such as concrete with vanilla Fabric/Indigo |
| “This server needs BlockBlend installed” | Install matching BlockBlend and Fabric API on the server |
| Cannot edit a block | Aim within normal interaction reach; check build permission, spawn protection, world bounds, and loaded chunks |
| Painted wall still casts a shadow | Expected: opacity does not change world lighting or collision |
| 0% block is still solid | Expected: erase restores normal rendering; mine the block to remove it |
| Chunk blend limit reached | Erase an edit in that chunk; the limit is 4096 entries and existing edits are retained |
| Newer save format message | Editing is disabled to preserve unknown-format data; restore a compatible backup/version |

General interleaving with glass, water, and translucent particles is limited. Sodium, Iris, shader packs, alternate resource packs, dense-world performance, and remote dedicated-server multiplayer remain **unqualified**. Verification targets vanilla Fabric/Indigo, opaque scene occlusion, and painted-layer ordering. Small-gallery render timing is not a performance benchmark. See [detailed behavior](MOD_USAGE_GUIDE.md) and [verification boundaries](TESTING_GUIDE.md).

## Build and experiment

Install a **Java 21 JDK**, set `JAVA_HOME` to it, and run from this repository:

```powershell
.\gradlew.bat build --no-daemon --console=plain
```

```sh
./gradlew build --no-daemon --console=plain
```

Output: **`build/libs/blockblend-0.3.1.jar`** and a sources JAR. The build runs unit tests. Gradle **8.8**, Fabric Loom **1.6.12**, and Minecraft/Fabric dependency versions are pinned. First use downloads dependencies from their official repositories.

On Windows, `powershell -File .\build.ps1 -JavaHome "path-to-jdk-21"` offers a checked Java selection. `./build.sh` is also available. These helpers build only; they do not install the mod or change launcher profiles.

```powershell
# Real Minecraft runtime checks; requires a working graphical display.
.\gradlew.bat runSmokeClient "-PsmokeGraphics=fancy"
.\gradlew.bat runSmokeClient "-PsmokeGraphics=fabulous"

# Build and capture the isolated art gallery.
.\gradlew.bat runShowcaseClient
```

Use `./gradlew` for the same tasks on macOS/Linux. Runtime smoke and showcase code are separate development source sets, excluded from the distributable JAR. [TESTING_GUIDE.md](TESTING_GUIDE.md) explains receipts, manual checks, and what each gate establishes.

## Contribute and license

Bug reports with exact versions, graphics mode, reproduction steps, and a screenshot are welcome in [GitHub Issues](https://github.com/dfrer/BlockBlend/issues). See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidance, [CHANGELOG.md](CHANGELOG.md) for changes, and [RELEASE_NOTES.md](RELEASE_NOTES.md) for upgrade notes.

BlockBlend is [MIT licensed](LICENSE), copyright © 2025 BlockBlend. It is an unofficial Minecraft mod and is not affiliated with, endorsed by, or approved by Mojang or Microsoft. Minecraft is a trademark of its respective owners.
