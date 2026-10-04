# RandomCraft — Minecraft 26.3 / Fabric

Randomizes crafting recipe outputs every 10 minutes, including supported modded recipes. This branch targets **Minecraft Java 26.3 stable**, mod **1.1.0**.

Requires Java 25, Fabric Loader 0.19.5+, and Fabric API 0.161.0+26.3. Put the mod JAR and Fabric API in `mods`. The mod logic runs on the server (including the integrated server in singleplayer).

Commands: `/randomcraft shuffle`, `/randomcraft next`, `/randomcraft timer on`, `/randomcraft timer off`. Operator permission level 2 is required. Configuration: `config/randomcraft.json`; crafting tables and chests are excluded by default.

## Build

Set `JAVA_HOME` to JDK 25, then run `./gradlew build` (`.\gradlew.bat build` on Windows). The JAR is `build/libs/randomcraft-fabric-26.3-1.1.0.jar`. Tests run with Fabric Loader against actual Minecraft shaped and shapeless recipes, checking output permutation, deterministic seeds, blacklists, mod namespaces, and stack limits.

The port uses the unobfuscated Fabric Loom plugin 1.17.21 and Gradle 9.6.0. See [Fabric's 26.3 porting notes](https://www.fabricmc.net/2026/09/15/263.html).

## Windows Release Studio

Open `release-tool/dist/RandomCraft-Release-Studio.exe` after packaging, or run `release-tool/Start-Release-Studio.bat` with Python 3.12+ installed. The EXE includes Python/Tk and does not require a separate Python installation. Java and Git are still needed for building and releasing.

1. Choose the RandomCraft project directory and matching JDK. The app detects `.tools/jdk-*` in parent directories and `JAVA_HOME`.
2. Click **Build JAR**. Build output streams into the log without freezing the window.
3. Commit the source changes and push the version branch to its GitHub origin. Release Studio does not commit, push, overwrite tags, or rewrite existing releases.
4. Enter `owner/repo` and a GitHub personal access token with **Contents: write** for that repository. Commits that change workflow files may also need **Workflows: write**. `GH_TOKEN` or `GITHUB_TOKEN` can supply the token; it is never saved to disk or passed to Gradle.
5. Review tag, title and notes. **Bản nháp (draft)** is selected initially. Click **Xem trước & tạo release**, check the preview and confirm. Uncheck draft to publish after the JAR upload succeeds.

The app checks the artifact SHA-256 and source fingerprint, requires a clean Git checkout and a pushed commit in the matching repository, and rejects existing tags/drafts. Upload failures leave the draft URL in the log for recovery. Private repositories are supported through the token. GitHub.com is supported; enterprise hosts are not currently supported.

For this checkout, the source branch is `26.3-fabric`. Example preparation (review changes first):

```powershell
git add .
git commit -m "Port RandomCraft to Minecraft 26.3 and add Release Studio"
git push -u origin 26.3-fabric
```

If Windows reports `Unable to establish loopback connection` under Java 25, use a short existing directory for Java Unix sockets, for example `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=E:\ModMCRandom\.tools\tmp`. Release Studio configures a short project-local socket directory automatically when possible.

## Package and test the GUI

```powershell
python -m unittest discover -s release-tool -v
powershell -ExecutionPolicy Bypass -File release-tool/package.ps1
```

Packaging dependencies are isolated in `release-tool/.venv`. The source app otherwise uses only the Python standard library. Automated release tests simulate GitHub responses; they do not publish anything. End-to-end publishing requires a real token and pushed source.

## GitHub Actions

Pushes and pull requests build/test the JAR and package the Windows GUI as downloadable workflow artifacts. Only **Run workflow** creates a GitHub release, defaulting to a draft. The GUI can create releases independently from the workflow. Existing tags/releases are never overwritten.

## Compatibility limits

This preserves the previous mod's output-field reflection approach. Standard shaped/shapeless recipes are covered by tests. Custom recipes without an `ItemStackTemplate`/`ItemStack` output field are skipped. Multiplayer recipe-book refresh and third-party modpacks still need an in-game check; automated tests do not simulate connected players.

MIT License — see [LICENSE](LICENSE).
