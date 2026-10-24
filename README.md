# TeamTP

Randomly balance online players into configurable LuckPerms teams, save a destination for each team, and teleport them through a bounded asynchronous queue. Four teams are included by default. No player-count cap is imposed.

## Install and start

1. Install Paper and LuckPerms. Choose the TeamTP JAR for your server version from `build/releases/` when using the prepared build outputs.
2. Put **one** TeamTP JAR in `plugins/`, then restart the server.
3. Wait for `Settings loaded and LuckPerms groups ready.` Missing team groups are created automatically.
4. Stand at each destination and run `/team setspawn a`, then repeat for `b`, `c`, and `d`.
5. Run `/team distribute` and wait for its completion message.
6. Run `/team tp all`.

Use `/teamtp` if `/team` resolves to Minecraft's vanilla command or another plugin. The fully qualified command is `/teamtp:team`.

## LuckPerms: groups versus permissions

Team membership is a **direct, permanent, global parent group**, not a permission. The default groups are `team_a`, `team_b`, `team_c`, and `team_d`. You do not need to add any permissions to those groups for teleporting to work.

`/team distribute` removes the configured team parents from eligible players, adds exactly one new team parent, and saves each player through LuckPerms. It preserves unrelated ranks, permissions, prefixes, and the player's primary group. It balances the selected online snapshot; successful assignment of 300 players produces 75 per team.

To assign a player manually:

```text
/lp user PlayerName parent remove team_a
/lp user PlayerName parent add team_b
```

Remove any other old team parents as well. Avoid `parent set`, which can remove unrelated ranks. Multiple team parents, temporary parents, negated parents, and contextual team parents produce an error during teleport preflight. Distribution replaces such direct team nodes with a permanent global parent. Team groups inherited indirectly through another group are not used for membership.

The default groups can be replaced with your existing **dedicated event groups** in `config.yml`. Do not map team groups to `default`, admin, or rank groups: distribution removes configured team parents. Do not change LuckPerms membership concurrently with a running TeamTP job. If changing or removing a group mapping, remove obsolete team parents first; groups no longer in the configuration are deliberately not touched.

Permissions:

| Permission | Default | Purpose |
| --- | --- | --- |
| `team.admin` | Operators | All TeamTP commands |
| `team.exempt` | Nobody | Exclude from distribution and teleport snapshots |

```text
/lp user Organizer permission set team.admin true
/lp user Spectator permission set team.exempt true
```

Operators are included in distribution unless exempt. A wildcard permission may grant the exemption too. Set `distribution.exempt-permission` to `''` to disable this filter. Spectators are excluded by default.

## Commands

| Command | Action |
| --- | --- |
| `/team distribute` | Randomize and balance eligible online players across all configured teams |
| `/team setspawn <team>` | Save your world, exact position, yaw, and pitch |
| `/team setspawn <team> <world> <x> <y> <z> [yaw pitch]` | Set a destination from console or in game; the world must be loaded |
| `/team tp <team>` | Teleport one team's eligible online players |
| `/team tp all` | Teleport every assigned eligible online player |
| `/team list` | Show online team counts, groups, destinations, and invalid membership count |
| `/team status` | Show queued, in-flight, completed, failed, and cancelled work |
| `/team cancel` | Stop queued work; already-started requests can still complete |
| `/team reload` | Validate settings and spawns, initialize groups, then apply the configuration |

Commands support tab completion. Settings, spawn changes, distribution, and teleportation cannot overlap another active job. Status and cancellation remain available. A failed reload retains the last working configuration. A first-start configuration failure is logged; fix the file and restart if the plugin was disabled.

## Configuration

`plugins/TeamTP/config.yml` controls teams, group names, display names, participation, queue limits, destination searches, and messages. Teams can be added, removed, or renamed; IDs must use lowercase letters, numbers, `_`, or `-`. Between 1 and 32 teams are supported; `all` is reserved. Each group must be unique. Display names and messages support `&` color codes. Set a message to `''` to hide it. Detailed error reasons and logs are English.

Spawns are saved separately in `plugins/TeamTP/spawns.yml` so settings edits do not erase destinations. Save operations replace that file atomically. Back up both files and your LuckPerms data. Manual spawn edits require `/team reload`. A saved world UUID is preferred, with its name used as a fallback if the UUID is unavailable.

| Setting | Default | Meaning |
| --- | --- | --- |
| `performance.starts-per-tick` | `4` | Maximum work starts per tick, including immediately completed requests; range 1–20 |
| `performance.concurrent-teleports` | `8` | Maximum outstanding player teleport requests; range 1–64 |
| `performance.concurrent-saves` | `4` | Maximum outstanding LuckPerms assignment saves; range 1–32 |
| `performance.stall-timeout-seconds` | `60` | Stop launching work when a request exceeds this age; range 10–600 |
| `teleport.spread-radius` | `5` | Search an 11×11 square around the spawn; range 0–8 |
| `teleport.vertical-search` | `2` | Search saved height, then above/below; range 0–4 |
| `teleport.safe-locations` | `true` | Require a full opaque solid floor, standing headroom, and no nearby listed hazards |
| `teleport.generate-chunks` | `true` | Permit asynchronous generation of missing destination chunks |
| `distribution.skip-spectators` | `true` | Exclude spectators from both operations |
| `distribution.notify-players` | `true` | Announce a player's team after its save succeeds |

Set both `spread-radius` and `vertical-search` to `0` to use the exact saved coordinates. With spreading enabled, landing positions are block-centered and yaw/pitch are retained. Players cycle through safe positions; if positions run out, some share a spot and the administrator is notified. Build roomy platforms to avoid crowding, pushing, and entity cramming. The safety check deliberately rejects slabs, glass, fences, water, and other nonopaque/partial floors. It does not modify terrain.

## Large events and failure behavior

- All selected teams must have valid, loaded-world destinations before anyone is moved. Safe destination preparation loads chunks asynchronously, at most two team preparations at once and one chunk request at a time per team. Those chunks remain ticketed until the operation ends.
- The default queue launches at most four players per tick with eight unresolved requests. For 300 players, dispatch alone takes at least 75 ticks (3.75 seconds at 20 TPS), plus preparation and chunk/teleport latency. This is a minimum, not a completion-time promise.
- Main-thread code never waits on a teleport future. Failures, event cancellations, disconnected sessions, dead players, passengers, and mounted players are counted and logged. Players who reconnect or join after the snapshot are not added. Queued players whose team or eligibility changed are not moved.
- Terrain and border checks run again just before dispatch. Other plugins can still cancel or redirect a teleport; the plugin respects the result. The safety check is conservative, but cannot predict later terrain changes, player movement, hostile mobs, or every other plugin's behavior.
- Cancellation discards only work that has not started. It does not undo successful team changes or teleports. A stall also discards queued work but keeps the operation locked while any underlying request remains unresolved. It intentionally does not release permits on a timeout because the original request could still finish later. Investigate stalled chunk/storage requests; a controlled server restart may be needed if they never settle.
- Distribution is not a database transaction across all players. Disconnects, cancellation, or failed saves can produce partial/unbalanced results. Failed saves restore the previous in-memory team nodes, but a storage error can leave persistence uncertain. Resolve the cause and run distribution again. No success/balance claim is made for partial jobs.
- Team membership persists through LuckPerms. Queued jobs do not resume after a restart, and disabling the plugin does not undo requests already accepted by Paper or LuckPerms.

The tests simulate 300 player teleports and enforce queue/routing behavior. They are not a benchmark of 300 real connected clients. Before an event, pre-generate destination areas and rehearse on your actual server with its protection plugins and hardware. Use `/team status` to monitor progress and adjust queue limits gradually if needed.

## Versions and builds

| Local branch | Paper API dependency | Server target | Java |
| --- | --- | --- | --- |
| `1.21.11` | `1.21.11-R0.1-SNAPSHOT` | 1.21.11 | 21 |
| `26.1` | `26.1.2.build.74-stable` | 26.1.2 | 25 |
| `26.2` | `26.2.build.134-stable` | 26.2 | 25 |
| `26.3` | `26.3.build.218-stable` | 26.3 | 25 |

The branch named `26.1` targets the published Paper 26.1.2 patch release. Version-specific values live in `gradle.properties`; the build generates the matching plugin API declaration. These branches target Paper, not Folia. LuckPerms 5.4 or newer is required.

```bash
cd /tmp/TeamTP
git switch 1.21.11
./gradlew build
```

JARs are written to `build/libs/`. Gradle uses a Java toolchain matching the branch. For a local Java installation not detected automatically:

```bash
./gradlew -Dorg.gradle.java.installations.paths=/path/to/jdk-25 build
```

`./gradlew test` runs balancing, configuration, persistence, membership, safety, and queue/workflow tests. `./gradlew runServer` provides an optional local development server; install LuckPerms in its `run/plugins/` folder and review the Minecraft EULA yourself before starting it. Do not point that task at production world data.

API references: [Paper teleportation](https://docs.papermc.io/paper/dev/entity-teleport/), [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/), [LuckPerms UserManager](https://github.com/LuckPerms/LuckPerms/blob/master/api/src/main/java/net/luckperms/api/model/user/UserManager.java).
