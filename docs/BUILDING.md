# Building

```
./gradlew build     # versions/<v>/build/libs/betterbarriers-<version>+<v>.jar, one per version
```

The house build is documented once for every first-party mod in the workspace's `docs/STYLE.md`.
This file covers only where this mod departs from it.

- **Multi-version build** (Stonecutter): 26.2 and 26.3, with 26.2 committed. Each version's keys
  are in `versions/<v>/gradle.properties`.
  - 26.3 builds against Polymer 0.18.4 from Maven.
  - Dev servers are on ports 25620/25622 (26.2) and 25816/25818 (26.3), each with its own
    `versions/<v>/run`.
- **`mavenLocal()` dependency.** kalic0re's `common/command`, `common/text` and `common/item`
  packages are relocated into the jar from `club.rainbowkitty.kalic0re:kalic0re:<kalic0re_version>`
  in `~/.m2`: `0.26.0` for 26.2, `0.31.0+26.3` for 26.3.
  - A fresh clone first needs `./gradlew publishToMavenLocal` in kalic0re at those versions.
  - A stale copy shows up as a compile error naming a missing kalic0re class or method.
- **The jar to install** is `betterbarriers-<version>+<v>.jar` for the server's version.
  `-unshaded` and `-sources` beside it are not.
- **No datagen.** Lang, tags and the mixin config are static resources.
- **Testing** beyond `./gradlew build` needs the dev server (`scripts/devserver.sh
  rkc/mods/better-barriers start`, 26.2) with ViaFabric + ViaBackwards and LuckPerms in
  `versions/26.2/run/mods`, then `node scripts/loadtest/better-barriers.js` for the legs and
  `better-barriers-load.js` for the load test; both scripts say what they need at the top.
  - The bots join as 26.1, and ViaFabric has no 26.3 server module, so the legs run on 26.2 only.
  - The two Via jars crash the dev client's multiplayer screen (`run/client/mods` is the same
    folder), so park them in `run/mods-for-bots/` for a `runClient` session and move them back,
    restarting the server each way; the scripts refuse to run while they are parked.
