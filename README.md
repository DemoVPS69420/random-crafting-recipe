# Random Crafting Recipe

A Minecraft mod that **randomizes crafting table recipes** every 10 minutes, including modded recipes. In-game it shows up as **RandomCraft**.

> Each supported Minecraft version + mod loader lives on its **own branch** (files at the branch root). Pick the branch that matches your setup, or grab a prebuilt jar from [**Releases**](../../releases).

## Supported versions

| Minecraft | Loader | Branch |
|---|---|---|
| 1.16.5 | Forge | [`1.16.5-forge`](../../tree/1.16.5-forge) |
| 1.18.2 | Forge | [`1.18.2-forge`](../../tree/1.18.2-forge) |
| 1.20.1 | Forge | [`1.20.1-forge`](../../tree/1.20.1-forge) |
| 1.20.1 | Fabric | [`1.20.1-fabric`](../../tree/1.20.1-fabric) |
| 1.21.1 | Forge | [`1.21.1-forge`](../../tree/1.21.1-forge) |
| 1.21.1 | Fabric | [`1.21.1-fabric`](../../tree/1.21.1-fabric) |
| 1.21.1 | NeoForge | [`1.21.1-neoforge`](../../tree/1.21.1-neoforge) |
| 26.1.1 | Fabric | [`26.1.1-fabric`](../../tree/26.1.1-fabric) |
| 26.1.2 | Fabric | [`26.1.2-fabric`](../../tree/26.1.2-fabric) |
| 26.2-snapshot-7 | Fabric | [`26.2-snapshot-7-fabric`](../../tree/26.2-snapshot-7-fabric) |
| 26.2 | Fabric | [`26.2-fabric`](../../tree/26.2-fabric) |
| 26.3-snapshot-1 | Fabric | [`26.3-snapshot-1-fabric`](../../tree/26.3-snapshot-1-fabric) |

## Building from source

```bash
git checkout <branch>      # e.g. 26.2-fabric
./gradlew build            # jar appears in build/libs/
```

Required JDK: **17** for 1.16.5–1.20.1, **21** for 1.21.1, **25** for the 26.x branches. Set `JAVA_HOME` to the matching JDK before building.

## License

[MIT](LICENSE) © HachizuOrigami
