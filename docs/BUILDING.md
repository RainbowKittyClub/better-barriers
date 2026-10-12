# Building

```
./gradlew build     # jar lands in build/libs/betterbarriers-<version>.jar
```

The house build is documented once for every first-party mod in the workspace's `docs/STYLE.md`.
This file covers only where this mod departs from it.

- **`mavenLocal()` dependency.** kalic0re's `common/command`, `common/text` and `common/item`
  packages are relocated into the jar from `club.rainbowkitty.kalic0re:kalic0re:<kalic0re_version>`
  in `~/.m2`. A fresh clone needs `cd rkc/mods/kalic0re && ./gradlew publishToMavenLocal` at that
  version first; a stale copy shows up as a compile error naming a missing kalic0re class or method.
- **The jar to install** is `betterbarriers-<version>.jar`. `-unshaded` and `-sources` beside it
  are not.
- **No datagen.** Lang, tags and the mixin config are static resources.
- **Testing** beyond `./gradlew build` needs the dev server (`scripts/devserver.sh
  rkc/mods/better-barriers start`, port 25620) with ViaFabric + ViaBackwards and LuckPerms in
  `run/mods`, then `node scripts/loadtest/better-barriers.js` for the legs and
  `better-barriers-load.js` for the load test; both scripts say what they need at the top.
  The two Via jars crash the dev client's multiplayer screen (`run/client/mods` is the same
  folder), so park them in `run/mods-for-bots/` for a `runClient` session and move them back,
  restarting the server each way; the scripts refuse to run while they are parked.
