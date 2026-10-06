# Contributing to BlockBlend

BlockBlend welcomes reproducible bug reports, focused improvements, and original Minecraft art experiments. Read the [usage guide](MOD_USAGE_GUIDE.md) and [testing guide](TESTING_GUIDE.md) before changing behavior.

## Report a bug

Open a [GitHub issue](https://github.com/dfrer/BlockBlend/issues) with:

- BlockBlend, Minecraft, Java, Fabric Loader, and Fabric API versions.
- Graphics mode, GPU/driver details for rendering issues, and any renderer/resource/shader mods.
- Minimal reproduction steps, expected behavior, and actual behavior.
- A screenshot and relevant log excerpt, with private paths, account details, addresses, and secrets removed.

Start from Minecraft 1.21.1 with vanilla resources and Fabric/Indigo when possible. Clearly label issues that require an unqualified renderer or dedicated multiplayer environment. Back up worlds before testing save changes.

## Develop locally

Use Java 21 and the checked-in Gradle wrapper. Run `./gradlew build` or `.\gradlew.bat build`; see [TESTING_GUIDE.md](TESTING_GUIDE.md) for full commands and reports. The first build downloads dependencies. Build helpers do not install the mod.

| Area | Source |
| --- | --- |
| Server validation and networking | `src/main/java/com/example/blockblend/registry/`, `security/` |
| Saved state, math, codecs | `src/main/java/com/example/blockblend/data/` |
| Client controls/cache | `src/main/java/com/example/blockblend/client/` |
| Rendering and geometry hooks | `src/main/java/com/example/blockblend/client/render/`, `client/mixin/` |
| Mod resources, recipe, shaders | `src/main/resources/` |
| Unit tests | `src/test/java/` |
| Development-only Minecraft smoke | `src/smoke/` |
| Development-only art fixture | `src/showcase/` |

Keep client-only classes out of common/server initialization. Preserve saved data, validate incoming settings on the server, and avoid silently broadening supported block/renderer claims. Use the isolated development directories rather than personal launcher worlds.

## Submit a focused change

Explain the concrete problem, the resulting behavior, and the checks run. Include a regression test when it protects meaningful behavior. Rendering changes need fresh Fancy/Fabulous runtime evidence; persistence or protocol changes need compatibility evidence. Preserve unsupported legacy entries and future-format data.

Keep unrelated formatting and generated local files out of the change. Smoke/showcase code must remain outside the production JAR. Avoid dependency changes unless they are needed and explained. For art contributions, share original layouts, settings, and direct Minecraft captures; distinguish captured images from edited illustrations.

Do not include credentials, personal worlds, local logs, or account/profile data. If you discover an exploitable security issue, avoid posting exploit details or secrets in a public issue; use an available private repository security-reporting channel.

Contributions are distributed under the existing [MIT license](LICENSE). Preserve its copyright and permission notice. BlockBlend is an unofficial project; contributions should not imply Mojang or Microsoft endorsement.
