# Ordinary AI

Adds extra AI patterns to mobs. "Passive" mobs fight back, monsters break light sources and doors, some mobs gain
random extra abilities, and villages remember how you treat them.

Ordinary AI is a port of [Special AI](https://github.com/FatherToast/SpecialAI) 1.1.0 by Father Toast to the
[Cleanroom](https://github.com/CleanroomMC/Cleanroom) loader for Minecraft 1.12.2. It keeps Special AI's features
and options, fixes a number of bugs and removes its heaviest per-tick work. See
[`changelog.txt`](src/main/resources/changelog.txt) for what differs.

Requires Cleanroom 0.7.0 or above. Only the server needs the mod; clients can join without it.

## Features

| Feature | Config category | What it does |
|---|---|---|
| Depacify | `_general` | Listed passive mobs strike back when hurt, and can be made hostile outright |
| Eat breeding items | `_general` | Animals walk to and eat their breeding food lying on the ground |
| Door breaking | `door_breaking` | Listed mobs break doors, gates and trapdoors that are in their way |
| Idle griefing | `idle_griefing` | Listed mobs break light sources, beds, crafting blocks and opened chests while idle |
| Idle fiddling | `idle_fiddling` | Listed mobs flip levers, press buttons, open doors, eat cake and set off TNT while idle |
| Reactions | `reaction_ai` | Mobs flee explosives, sidestep arrows, and call for help when hurt or killed |
| Jockeys | `jockeys` | Listed mobs look for a suitable mount and ride it |
| Elite AI | `elite_ai` | Listed mobs can gain one or more of ten special patterns, each with matching gear |
| Villages | `villages` | Reputation for breaking blocks and killing monsters, villagers that defend their village, villager names, and villages that survive being unloaded |

The elite patterns are charge, sprint, jump, leap, barrage, spawner, shaman, throw (an ally), throw player and
thief.

## Configuration

Options live in `config/specialai.cfg`, which is written with defaults and a description of every option on first
run. They can also be edited in game from the mod list. On a dedicated server, `/specialai reload` (operators
only) re-reads the file.

Default block lists are worked out from the blocks that are actually registered, so doors, chests and the like
from other mods are included when the file is first written.

**Coming from Special AI:** if `config/Special_AI.cfg` exists and `config/specialai.cfg` does not, the old file
is copied across unchanged. Mobs in existing worlds keep the AI they had. Ordinary AI keeps Special AI's mod id,
`specialai`, so remove the Special AI jar; the two cannot be installed together.

### Mob lists

    entity_id [chance ...]

One entry per line. An entry also covers mobs that extend the named one (`minecraft:zombie` covers husks and
zombie pigmen); put `~` in front to match that exact mob only. When several entries match, the most specific wins.

### Block lists

    mod_id:block_id
    mod_id:block_id[property=value,...]
    mod_id:*

### Per-mob overrides

What a mob gets is rolled once when it first spawns and saved in its NBT under `ForgeData.ordinaryai`. Setting
those tags yourself overrides the roll, for example:

    /summon zombie ~ ~ ~ {ForgeData:{ordinaryai:{EliteAI:{charge:0.5f},ForceInit:1b,Griefing:1b}}}

`ForceInit` hands out the gear and attribute boosts that go with the elite patterns. The tag names are listed in
[`MobData`](src/main/java/io/github/ndellagrotte/ordinaryai/ai/MobData.java).

## Commands

- `/villageinfo` tells a player about the village they are standing in and their reputation there.
- `/specialai reload` re-reads the config file. Operators only.

## Building

    ./gradlew build

Builds the mod jar into `build/libs`, and runs the unit tests, Checkstyle and the formatter check. Run
`./gradlew clearSkiesApply formatJavaApply` to fix formatting. Requires JDK 25.

`./gradlew runClient` and `./gradlew runServer` start the game with the mod.

### Checking a build in game

The unit tests cover the logic that does not need the game: list parsing, weighted choices, reputation rules and
arrow geometry. Everything else needs a running server.

    ./gradlew runServer -Dordinaryai.selftest=true

runs a short self-test once the server has started, then stops it. It checks that the config and its default lists
load, that a mob saved by Special AI is migrated and set up exactly once, and that rolls are saved. The process
exits with an error if a check fails. Look for `SELF-TEST PASSED` in the log.

## License and credits

GPL-3.0-only. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).

Special AI was created by Father Toast, with thanks to Mother Toast. The AI patterns, options and villager names
are theirs; this port is by Nicholas DellaGrotte.
