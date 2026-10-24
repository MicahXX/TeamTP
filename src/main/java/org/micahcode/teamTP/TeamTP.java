package org.micahcode.teamTP;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

public final class TeamTP extends JavaPlugin {
    private Settings settings;
    private Messages messages;
    private SpawnStore spawns;
    private JobRunner jobs;
    private LuckPermsTeams teams;
    private DistributionService distribution;
    private TeleportService teleports;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            LuckPerms api = Objects.requireNonNull(getServer().getServicesManager().load(LuckPerms.class), "LuckPerms API unavailable.");
            Executor mainThread = action -> {
                if (!isEnabled()) throw new RejectedExecutionException("TeamTP is disabled.");
                if (Bukkit.isPrimaryThread()) action.run();
                else getServer().getScheduler().runTask(this, action);
            };
            teams = new LuckPermsTeams(api);
            jobs = new JobRunner(this);
            distribution = new DistributionService(teams, jobs, mainThread);
            teleports = new TeleportService(this, teams, jobs, mainThread);
            TeamCommand handler = new TeamCommand(this);
            Objects.requireNonNull(getCommand("team")).setExecutor(handler);
            Objects.requireNonNull(getCommand("team")).setTabCompleter(handler);
            loadSettings(getServer().getConsoleSender());
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "TeamTP startup failed; fix configuration/dependencies and restart.", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public void loadSettings(CommandSender sender) throws Exception {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(new java.io.File(getDataFolder(), "config.yml"));
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(getResource("config.yml")), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            defaults.set("teams", null);
            candidate.setDefaults(defaults);
        }
        Settings nextSettings = Settings.parse(candidate);
        for (String key : Objects.requireNonNull(candidate.getConfigurationSection("messages")).getKeys(false)) {
            if (!candidate.isString("messages." + key)) throw new IllegalArgumentException("messages." + key + " must be text.");
        }
        Messages nextMessages = new Messages(candidate);
        SpawnStore nextSpawns = new SpawnStore(getDataFolder().toPath().resolve("spawns.yml"));
        if (messages == null) messages = nextMessages;
        jobs.start("configuration", sender, messages, nextSettings, List.of("LuckPerms groups"), 1,
                ignored -> teams.prepare(nextSettings).thenApply(result -> true), success -> {
                    if (success) {
                        settings = nextSettings;
                        messages = nextMessages;
                        spawns = nextSpawns;
                        messages.send(sender, "reloaded");
                    } else {
                        messages.send(sender, "error", "reason", "Group initialization failed or was cancelled. Previous settings retained; check the console and retry /team reload.");
                    }
                });
    }

    @Override
    public void onDisable() {
        if (jobs != null) jobs.close();
        if (teleports != null) teleports.close();
    }

    public Settings settings() { return settings; }

    public Messages messages() { return messages; }

    public SpawnStore spawns() { return spawns; }

    public JobRunner jobs() { return jobs; }

    public LuckPermsTeams teams() { return teams; }

    public DistributionService distribution() { return distribution; }

    public TeleportService teleports() { return teleports; }
}
