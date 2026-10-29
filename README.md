# TeamTP

Randomly balance online players into configurable LuckPerms teams, save a destination for each team, and teleport them through a bounded asynchronous queue. Four teams are in the config by default.

Made by **micahcode**. Licensed under the [MIT License](LICENSE).

## Commands

| Command | Action |
| --- | --- |
| `/team distribute` | Randomize and balance eligible online players across all configured teams |
| `/team setspawn <team>` | Save your world, exact position, yaw and pitch |
| `/team setspawn <team> <world> <x> <y> <z> [yaw pitch]` | Set a destination from console or in game (the world must be loaded) |
| `/team tp <team>` | Teleport one team's eligible online players |
| `/team tp all` | Teleport every assigned eligible online player |
| `/team list` | Show online team counts, groups, destinations and invalid membership count |
| `/team status` | Show queued, in-flight, completed, failed and cancelled work |
| `/team cancel` | Stop queued work (already-started requests can still complete) |
| `/team reload` | Validate settings and spawns, initialize groups, then apply the configuration |
