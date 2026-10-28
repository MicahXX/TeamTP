# TeamTP

Randomly balance online players into configurable LuckPerms teams, save a destination for each team, and teleport them through a bounded asynchronous queue. Four teams are included by default. No player-count cap is imposed.

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
