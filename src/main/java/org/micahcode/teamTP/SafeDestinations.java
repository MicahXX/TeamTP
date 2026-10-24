package org.micahcode.teamTP;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class SafeDestinations implements AutoCloseable {
    private static final Set<Material> HAZARDS = EnumSet.of(Material.LAVA, Material.WATER, Material.FIRE,
            Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
            Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.NETHER_PORTAL,
            Material.END_PORTAL, Material.END_GATEWAY);
    private final JavaPlugin plugin;
    private final Executor mainThread;
    private final Set<Chunk> tickets = new HashSet<>();
    private boolean closed;

    public SafeDestinations(JavaPlugin plugin, Executor mainThread) {
        this.plugin = plugin;
        this.mainThread = mainThread;
    }

    public CompletableFuture<List<Location>> prepare(Location spawn, Settings settings) {
        SpawnStore.validate(spawn);
        int radius = settings.spreadRadius() + 2;
        World world = spawn.getWorld();
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (int x = (spawn.getBlockX() - radius) >> 4; x <= (spawn.getBlockX() + radius) >> 4; x++) {
            for (int z = (spawn.getBlockZ() - radius) >> 4; z <= (spawn.getBlockZ() + radius) >> 4; z++) {
                final int chunkX = x;
                final int chunkZ = z;
                future = future.thenComposeAsync(ignored -> {
                    if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Destination loading cancelled."));
                    return world.getChunkAtAsync(chunkX, chunkZ, settings.generateChunks()).thenAcceptAsync(chunk -> {
                        if (closed) throw new IllegalStateException("Destination loading cancelled.");
                        if (chunk == null) throw new IllegalArgumentException("Destination chunk is not generated; pre-generate it or enable teleport.generate-chunks.");
                        if (tickets.add(chunk)) chunk.addPluginChunkTicket(plugin);
                    }, mainThread);
                }, mainThread);
            }
        }
        return future.thenApplyAsync(ignored -> find(spawn, settings), mainThread);
    }

    private List<Location> find(Location spawn, Settings settings) {
        if (closed) throw new IllegalStateException("Destination loading cancelled.");
        List<Location> candidates = new ArrayList<>();
        int radius = settings.spreadRadius();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location base = radius == 0 ? spawn.clone() : new Location(spawn.getWorld(),
                        spawn.getBlockX() + x + 0.5, spawn.getBlockY(), spawn.getBlockZ() + z + 0.5,
                        spawn.getYaw(), spawn.getPitch());
                for (int offset = 0; offset <= settings.verticalSearch() * 2; offset++) {
                    int dy = offset == 0 ? 0 : ((offset + 1) / 2) * (offset % 2 == 1 ? 1 : -1);
                    Location candidate = base.clone().add(0, dy, 0);
                    if (valid(candidate, settings.safeLocations())) {
                        candidates.add(candidate);
                        break;
                    }
                }
            }
        }
        if (candidates.isEmpty()) throw new IllegalArgumentException("No safe landing positions near the spawn. Build a flat platform with two air blocks above it.");
        candidates.sort(Comparator.comparingDouble(location -> location.distanceSquared(spawn)));
        return candidates;
    }

    public static boolean valid(Location location, boolean checkBlocks) {
        try { SpawnStore.validate(location); }
        catch (IllegalArgumentException error) { return false; }
        World world = location.getWorld();
        for (double dx : new double[]{-0.31, 0.31}) {
            for (double dz : new double[]{-0.31, 0.31}) {
                Location corner = location.clone().add(dx, 0, dz);
                if (!world.getWorldBorder().isInside(corner)) return false;
                int x = corner.getBlockX();
                int z = corner.getBlockZ();
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                if (!checkBlocks) continue;
                int floorY = location.getBlockY() - 1;
                Block floor = world.getBlockAt(x, floorY, z);
                if (!floor.getType().isOccluding() || !floor.getType().isSolid() || HAZARDS.contains(floor.getType())) return false;
                for (int y = location.getBlockY(); y <= (int) Math.floor(location.getY() + 1.8); y++) {
                    if (!world.getBlockAt(x, y, z).isEmpty()) return false;
                }
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        if (!world.isChunkLoaded((x + ox) >> 4, (z + oz) >> 4)) return false;
                        for (int y = floorY; y <= floorY + 2; y++) {
                            if (HAZARDS.contains(world.getBlockAt(x + ox, y, z + oz).getType())) return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void close() {
        closed = true;
        tickets.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        tickets.clear();
    }
}
