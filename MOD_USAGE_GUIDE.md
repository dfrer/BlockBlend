# Using BlockBlend 0.3.1

[README](README.md) · [Art experiments](ART_EXPERIMENTS.md) · [Testing](TESTING_GUIDE.md)

## Getting the tool

In a crafting table, put a feather in the top-center slot, a copper ingot in the center, and a stick in the bottom-center slot. Leave the other slots empty. The recipe returns one Blend Applicator. Find it under Creative **Tools**, or use `/give @s blockblend:blend_applicator` with command permission.

## Controls

Hold the applicator in your main hand. The HUD shows mode, pattern, pattern strength, opacity, and controls. Initial selections are **Multiply, Noise, 50% pattern strength, 50% opacity**.

| Control | Result |
| --- | --- |
| B | Next mode: Multiply, Screen, Overlay, Soft Light, Hard Light, Color Dodge, Color Burn |
| N | Next pattern: Noise, Stripes, Scratches |
| [ / ] | Decrease / increase pattern strength by 10%, bounded to 0–100% |
| , / . | Decrease / increase opacity by 10%, bounded to 0–100% |
| Right-click | Apply the current settings to a block |
| Shift + right-click | Remove that block's blend |

Rebind keys in **Options → Controls → BlockBlend**. Settings select the next stroke; they do not change existing strokes until you apply them again. A stroke paints the eligible faces of one block and is stored at its position.

The server confirms accepted apply/erase actions in the action bar. Survival and Creative players can edit where vanilla build permissions allow. Held-tool checks, normal raycast reach, spawn protection, spectator/adventure restrictions, loaded chunks, and world bounds apply.

## Understanding the result

Pattern strength first mixes the original texture with the chosen pattern. Strength **0%** leaves the original texture in the equation; strength **100%** uses the pattern. Opacity then mixes the actual background with the selected blend result:

```text
surface = mix(original texture, pattern, strength)
color   = mix(background, blend(background, surface), opacity)
```

Opacity **0%** makes the painted block visually absent. At **100%**, the selected blend equation contributes fully; Multiply and the other modes still depend on scenery. Erase removes the saved edit and restores normal rendering.

The renderer replaces painted vanilla geometry, restores neighboring faces and section visibility, and draws eligible front faces from back to front with foreground depth testing. Each layer samples the scene composed so far. Painting retains normal collision, mining, identity, light blocking, and shadows. Neighboring faces can remain darker than in a scene where the block is physically removed.

Face-adjacent eligible painted cubes with **nonzero opacity** form one exterior region. Their shared internal faces are hidden, including where materials, modes, patterns, strengths, or nonzero opacities differ. This prevents internal dark sheets through connected sculptures. A neighbor at **0% opacity** is a visual gap and exposes the visible cube's boundary. Missing, unsupported, or outdated neighbor state also leaves a valid boundary visible.

For independent layered screens, **leave air between the layers**. Directly touching painted cubes use the connected-region rule. Outer surfaces retain their normal lighting, and the physical blocks still cast shadows and block movement.

## Supported blocks and renderers

Use simple opaque full cubes with static textures and **one opaque surface covering each face**, including concrete, wool, stone, and planks. Layered models such as normal grass blocks, partial-face models, textures containing transparency, slabs, stairs, glass, fluids, block entities, and animated textures are unsupported. Unsupported targets receive a client message. Existing tags on unsupported targets remain saved and are skipped visually.

The scene pass uses Minecraft's resolved framebuffer. Ordinary glass, water, and translucent particles use earlier vanilla passes; their interleaving with painted surfaces is limited. Opaque foreground occlusion and ordering among painted blocks are the intended rendering scope.

Vanilla textures and Fabric/Indigo are the test baseline. A resource pack must retain the supported simple-cube model and fully opaque static textures; alternate packs, Sodium, Iris, and shader packs have not been qualified. Eligible loaded painted blocks in the camera frustum have no arbitrary count or distance cutoff. Each visible face copies its projected screen region for compositing, so dense scenes may be costly; no dense-world performance claim is made.

A chunk accepts at most **4096 editable entries**. At the limit, new entries are refused and existing ones retained; repainting an existing entry remains possible.

## Saving and upgrading

Back up your world before changing versions. BlockBlend stores per-dimension data in Minecraft persistent world data, independently of chunk loading. Server-validated edits synchronize to clients; chunk unloads and disconnects clear client caches, not saved edits. Packets identify their dimension.

Close Minecraft before replacing the mod JAR. Replace the old JAR and keep only the intended BlockBlend version in each client/server `mods` folder.

**0.3.1 writes save format 3**, including independent opacity. Readable 0.1/0.2 entries without opacity load at **100%**, preserving their full equation contribution; their mode now operates on the actual scene. New strokes default to 50%. Use the same 0.3.1 mod on client and server: versioned network channels prevent older packet layouts from being misread.

The signed position encoding supports every vanilla height from **−64 through 319** and custom heights within Minecraft's position encoding. Readable older entries retain their decoded positions. The old eight-bit height format may already have aliased or overwritten entries above y=191; missing original heights cannot be reconstructed.

Unsupported legacy overlay identifiers are archived without rendering and survive saves. Applying or erasing that position replaces or removes its archived entry. Data with a newer unknown format is preserved unchanged, with blending/editing disabled. This protection is not a general downgrade guarantee: use a pre-upgrade backup when returning to an older mod.

If a block at an edited position becomes another supported simple cube, the blend applies to the replacement texture. Erase the position to clear the edit permanently.

## Practical starting points

- **Color window:** pink concrete, Screen, strength 0%, opacity 70%, with a colorful wall behind it.
- **Patterned veil:** pink concrete, Multiply, Stripes 90%, opacity 60%.
- **Layered light:** light blue concrete, Screen, Scratches 80%, opacity 60%, behind a second painted sheet.

[ART_EXPERIMENTS.md](ART_EXPERIMENTS.md) provides exact layouts and cameras for three reproducible studies.
