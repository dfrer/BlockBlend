# BlockBlend 0.3.1

BlockBlend turns simple opaque Minecraft cubes into scene-blended color surfaces with seven modes, three patterns, and independent opacity. This release includes original [Minecraft art experiments](https://github.com/dfrer/BlockBlend/blob/v0.3.1/ART_EXPERIMENTS.md), a portable source build, and development-only runtime verification fixtures.

## Install

Target: **Minecraft Java 1.21.1**, **Java 21**, tested with **Fabric Loader 0.16.14** and **Fabric API 0.102.0+1.21.1**.

Download `blockblend-0.3.1.jar` from [GitHub Releases](https://github.com/dfrer/BlockBlend/releases), then place it and the matching Fabric API JAR in your Fabric profile's `mods` folder. Install the same mod version on server and clients for multiplayer. The sources JAR is a developer artifact; checksum files accompany release assets for integrity checking. [Full installation and controls](https://github.com/dfrer/BlockBlend/blob/v0.3.1/README.md#install).

## What to try

Hold a Blend Applicator in your main hand. **B** cycles mode, **N** cycles pattern, **[ / ]** changes pattern strength, and **comma / period** changes opacity. Right-click applies; Shift + right-click erases. New strokes start at **50% opacity**.

Place pink concrete in front of a colorful wall; try Screen at 0% pattern strength and 70% opacity, then compare Multiply. Explore layered screens or reproduce the [seven mode windows, layered screens, and ring sculpture](https://github.com/dfrer/BlockBlend/blob/v0.3.1/ART_EXPERIMENTS.md).

Opacity changes the contribution of the scene equation. At 100%, the result still depends on the background. At 0%, the block is visually absent but keeps collision, mining, light blocking, and shadows.

Touching eligible painted cubes with nonzero opacity form one exterior region, hiding shared internal faces even when materials or paint settings differ. A 0% opacity neighbor exposes the visible cube's boundary. Leave air between surfaces for independent layered blending.

## Upgrade notes

**Back up worlds before upgrading or downgrading.** Close Minecraft, replace the old JAR, and keep only the intended BlockBlend version in each client/server `mods` folder.

Version 0.3.1 writes save format 3. Readable legacy 0.1/0.2 strokes without opacity load at **100%**; their mode now blends with the actual scene. Unsupported legacy entries are retained without rendering. Unknown future formats are preserved unchanged with editing disabled.

The legacy eight-bit height encoding could alias or overwrite edits above y=191. Current signed encoding supports vanilla heights, but cannot recover original information already lost. Restore an older backup for downgrade recovery. See [saving and upgrading](https://github.com/dfrer/BlockBlend/blob/v0.3.1/MOD_USAGE_GUIDE.md#saving-and-upgrading).

## Supported scope

Release verification passed **34 unit tests**, **216 Fancy runtime checks / 41 captures**, and **228 Fabulous runtime checks / 45 captures**, including connected surfaces, zero-opacity gaps, save/reopen, reload, resizing and foreground depth. All four gallery images were freshly captured from this candidate. See the [recorded results and boundaries](https://github.com/dfrer/BlockBlend/blob/v0.3.1/TESTING_GUIDE.md#recorded-031-verification).

Use simple opaque full cubes with static textures and one opaque surface per face, such as concrete, wool, stone, and planks. Layered models such as normal grass blocks, partial-face models, textures containing transparency, slabs, stairs, glass, fluids, block entities, and animated textures are unsupported.

Verification targets vanilla Fabric/Indigo, painted-layer ordering, and opaque foreground occlusion. General glass/water/particle interleaving is limited. Sodium, Iris, shaders, alternate resource packs, dense-world performance, and remote dedicated-server multiplayer remain unqualified. Small-gallery timing is not a benchmark.

See [TESTING_GUIDE.md](https://github.com/dfrer/BlockBlend/blob/v0.3.1/TESTING_GUIDE.md) for reproducible build/runtime gates and their boundaries, and [CHANGELOG.md](https://github.com/dfrer/BlockBlend/blob/v0.3.1/CHANGELOG.md) for repository changes. The production JAR excludes smoke/showcase fixtures.

BlockBlend is [MIT licensed](https://github.com/dfrer/BlockBlend/blob/v0.3.1/LICENSE) and is an unofficial Minecraft mod, unaffiliated with Mojang or Microsoft.
