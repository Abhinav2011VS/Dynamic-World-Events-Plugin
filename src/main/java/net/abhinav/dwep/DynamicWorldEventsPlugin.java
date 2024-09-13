package net.abhinav.dwep;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public class DynamicWorldEventsPlugin extends JavaPlugin implements Listener {

    private BukkitTask floodTask;
    private BukkitTask meteorShowerTask;
    private BukkitTask mobInvasionTask;
    private final double rescueNPCDistance = 1000; // Distance in blocks to teleport the NPC to the player

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        startFloodEvent();
        startMeteorShower();
        startMobInvasion();
    }

    @Override
    public void onDisable() {
        if (floodTask != null) floodTask.cancel();
        if (meteorShowerTask != null) meteorShowerTask.cancel();
        if (mobInvasionTask != null) mobInvasionTask.cancel();
    }

    private void startFloodEvent() {
        final int startLevel = getConfig().getInt("flood.start-water-level", 64);
        final int endLevel = getConfig().getInt("flood.end-water-level", 100);
        final long updateInterval = getConfig().getLong("flood.update-interval", 200);

        floodTask = new BukkitRunnable() {
            private int currentLevel = startLevel;

            @Override
            public void run() {
                if (currentLevel >= endLevel) {
                    cancel();
                    return;
                }

                for (int x = -1000; x < 1000; x++) {
                    for (int z = -1000; z < 1000; z++) {
                        Block block = getServer().getWorlds().get(0).getBlockAt(x, currentLevel, z);
                        if (block.getType() == Material.AIR) {
                            block.setType(Material.WATER);
                        }
                    }
                }

                currentLevel++;
                if (currentLevel == endLevel) {
                    spawnRescueNPC();
                }
            }
        }.runTaskTimer(this, 0L, updateInterval);
    }

    private void startMeteorShower() {
        meteorShowerTask = new BukkitRunnable() {
            @Override
            public void run() {
                int numberOfMeteors = getConfig().getInt("meteor-shower.number-of-meteors", 10);
                double explosionPower = getConfig().getDouble("meteor-shower.explosion-power", 4);

                for (int i = 0; i < numberOfMeteors; i++) {
                    Location spawnLocation = new Location(getServer().getWorlds().get(0),
                            Math.random() * 2000 - 1000,
                            100,
                            Math.random() * 2000 - 1000);

                    getServer().getWorlds().get(0).spawnEntity(spawnLocation, EntityType.FIREBALL);
                }
            }
        }.runTaskTimer(this, 0L, 12000); // Every 10 minutes
    }

    private void startMobInvasion() {
        mobInvasionTask = new BukkitRunnable() {
            @Override
            public void run() {
                int numberOfMobs = getConfig().getInt("mob-invasion.number-of-mobs", 200);
                EntityType mobType = EntityType.valueOf(getConfig().getString("mob-invasion.mob-type", "ZOMBIE"));

                for (int i = 0; i < numberOfMobs; i++) {
                    Location spawnLocation = new Location(getServer().getWorlds().get(0),
                            Math.random() * 2000 - 1000,
                            64,
                            Math.random() * 2000 - 1000);

                    getServer().getWorlds().get(0).spawnEntity(spawnLocation, mobType);
                }
            }
        }.runTaskTimer(this, 0L, 6000); // Every 5 minutes
    }

    private void spawnRescueNPC() {
        for (Player player : getServer().getOnlinePlayers()) {
            Location playerLocation = player.getLocation();
            Villager rescueNPC = (Villager) player.getWorld().spawnEntity(playerLocation, EntityType.VILLAGER);
            rescueNPC.setCustomName("Rescue NPC");
            rescueNPC.setCustomNameVisible(true);
            rescueNPC.setAI(true);

            // Give player a boat and Water Breathing potion
            ItemStack boat = new ItemStack(Material.OAK_BOAT);
            player.getInventory().addItem(boat);

            ItemStack waterBreathingPotion = new ItemStack(Material.POTION);
            PotionMeta meta = (PotionMeta) waterBreathingPotion.getItemMeta();
            if (meta != null) {
                meta.addCustomEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 6000, 1), true);
                waterBreathingPotion.setItemMeta(meta);
            }
            player.getInventory().addItem(waterBreathingPotion);

            // Rescue NPC tasks
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.getWorld().equals(rescueNPC.getWorld()) && playerLocation.distance(rescueNPC.getLocation()) > rescueNPCDistance) {
                        rescueNPC.teleport(playerLocation);
                    }

                    if (player.getHealth() < 10 || player.getFoodLevel() < 10) {
                        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 4)); // Heal player
                        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 5)); // Feed player
                    }

                    // Give boat if not holding one
                    if (!player.getInventory().contains(Material.OAK_BOAT)) {
                        player.getInventory().addItem(new ItemStack(Material.OAK_BOAT));
                    }
                }
            }.runTaskTimer(this, 0L, 200L); // Every 10 seconds

            // NPC drives boat
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Entity entity : player.getWorld().getEntitiesByClass(Villager.class)) {
                        if (entity.getCustomName() != null && entity.getCustomName().equals("Rescue NPC")) {
                            Location npcLocation = entity.getLocation();
                            // Move NPC to player if they are far
                            if (playerLocation.distance(npcLocation) > rescueNPCDistance) {
                                entity.teleport(playerLocation);
                            }
                        }
                    }
                }
            }.runTaskTimer(this, 0L, 200L); // Every 10 seconds
        }
    }
}
