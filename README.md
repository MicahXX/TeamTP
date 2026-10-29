# TeamTP

Randomly balance online players into configurable LuckPerms teams, save a destination for each team, and teleport them through a bounded asynchronous queue. Four teams are included by default. No player-count cap is imposed.

## Death respawning

Players respawn after death at their team's saved `/team setspawn` location by default, including its direction. This overrides beds, respawn anchors, and the normal world spawn. Set `respawn.at-team-spawn: false` in `config.yml` and run `/team reload` to disable it. Existing configs that omit the setting inherit the enabled default.

Players without a team, or whose team spawn is missing or invalid (including an unloaded world), keep their normal respawn location. Returning from the End is unaffected. Respawning uses the exact saved location; the command's spread and safe-location search apply only to `/team tp`. Keep team spawns safe. Distribution exemptions do not disable team respawning.

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
