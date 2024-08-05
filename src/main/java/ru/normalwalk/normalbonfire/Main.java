package ru.normalwalk.normalbonfire;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener {

    private Set<Location> campfireLocations = new HashSet<>();
    private final NamespacedKey customKey = new NamespacedKey(this, "customUUID");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadCampfires();
    }
    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        saveCampfires();
        this.getLogger().info("§c╔══════════════════════════════════");
        this.getLogger().info("§c╠ NormalBonfire 1.0");
        this.getLogger().info("§c╠ §fПлагин отключился!");
        this.getLogger().info("§c╠ §fАвтор плагина: §3vk.com/normalwalk");
        this.getLogger().info("§c╚══════════════════════════════════");
    }
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE) {
            Location location = block.getLocation();
            for (Location campfireLocation : campfireLocations) {
                if (campfireLocation.getWorld().equals(location.getWorld()) && campfireLocation.distance(location) <= 15) {
                    event.getPlayer().sendMessage(Color.color(getConfig().getString("message.too-close")));
                    event.setCancelled(true);
                    return;
                }
            }
            campfireLocations.add(location);
            ItemStack itemInHand = event.getItemInHand();
            ItemMeta meta = itemInHand.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(customKey, PersistentDataType.STRING)) {
                startCampfireEffect(location, block);
            }
        }
    }
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();

        if ((block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE) && campfireLocations.contains(location)) {
            campfireLocations.remove(location);
            saveCampfires();
            ItemStack campfireItem = new ItemStack(block.getType());
            ItemMeta meta = campfireItem.getItemMeta();
            if (meta != null) {
                UUID uniqueID = UUID.randomUUID();
                meta.getPersistentDataContainer().set(customKey, PersistentDataType.STRING, uniqueID.toString());

                String customName = Color.color(getConfig().getString("custom-name"));
                if (customName != null && customName.isEmpty()) {
                    meta.setDisplayName(customName);
                }

                campfireItem.setItemMeta(meta);
            }
            block.getWorld().dropItemNaturally(location, campfireItem);
        }
    }

    private void startCampfireEffect(Location location, Block block) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (block.getType() != Material.CAMPFIRE && block.getType() != Material.SOUL_CAMPFIRE) {
                    cancel();
                    return;
                }
                FileConfiguration config = getConfig();
                int radius = config.getInt("radius");
                boolean effectsEnabled = config.getBoolean("effect.enabled");
                boolean damageEnabled = config.getBoolean("damage.enabled");
                double regenerationHealth = config.getDouble("regeneration.health");
                double damageAmount = config.getDouble("damage.amount");
                if (effectsEnabled) {
                    String effectType = config.getString("effect.type").toUpperCase();
                    int effectDuration = 40;
                    int effectAmplifier = config.getInt("effect.amplifier");
                    PotionEffectType potionEffectType = PotionEffectType.getByName(effectType);
                    block.getWorld().spawnParticle(Particle.HEART, location.clone().add(0.5, 1, 0.5), 5);
                    // люблю настю
                    for (double angle = 0; angle < 360; angle += 2) {
                        double radians = Math.toRadians(angle);
                        double x = radius * Math.cos(radians);
                        double z = radius * Math.sin(radians);
                        Location particleLocation = location.clone().add(x, 1, z);
                        block.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, particleLocation, 10);
                    }
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getLocation().distance(location) <= radius) {
                            player.setHealth(Math.min(player.getHealth() + regenerationHealth, player.getMaxHealth()));
                            if (potionEffectType != null) {
                                player.addPotionEffect(new PotionEffect(potionEffectType, effectDuration, effectAmplifier, true, false, false));
                            }
                        }
                    }
                }
                if (damageEnabled) {
                    for (Entity entity : block.getWorld().getNearbyEntities(location, radius, radius, radius)) {
                        if (entity instanceof Monster) {
                            LivingEntity monster = (LivingEntity) entity;
                            monster.damage(damageAmount);
                            block.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, monster.getLocation(), 10);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }
    private void saveCampfires() {
        File file = new File(getDataFolder(), "campfires.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("campfires", null);

        int index = 0;
        for (Location loc : campfireLocations) {
            config.set("campfires." + index + ".world", loc.getWorld().getName());
            config.set("campfires." + index + ".x", loc.getX());
            config.set("campfires." + index + ".y", loc.getY());
            config.set("campfires." + index + ".z", loc.getZ());
            index++;
        }
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void loadCampfires() {
        File file = new File(getDataFolder(), "campfires.yml");
        if (!file.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("campfires")) {
            for (String key : config.getConfigurationSection("campfires").getKeys(false)) {
                String worldName = config.getString("campfires." + key + ".world");
                double x = config.getDouble("campfires." + key + ".x");
                double y = config.getDouble("campfires." + key + ".y");
                double z = config.getDouble("campfires." + key + ".z");
                Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                campfireLocations.add(loc);
                Block block = loc.getBlock();
                startCampfireEffect(loc, block);
            }
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("normalbonfire")) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
                String playerName = args[1];
                Player target = Bukkit.getPlayer(playerName);

                if (target != null) {
                    int amount = 1;
                    if (args.length == 3) {
                        try {
                            amount = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Color.color(getConfig().getString("message.error-format")));
                            return false;
                        }
                    }
                    ItemStack campfireItem = new ItemStack(Material.CAMPFIRE, amount);
                    ItemMeta meta = campfireItem.getItemMeta();
                    if (meta != null) {
                        UUID uniqueID = UUID.randomUUID();
                        meta.getPersistentDataContainer().set(customKey, PersistentDataType.STRING, uniqueID.toString());

                        String customName = Color.color(getConfig().getString("custom-name"));
                        if (customName != null && !customName.isEmpty()) {
                            meta.setDisplayName(customName);
                        }
                        campfireItem.setItemMeta(meta);
                    }
                    if (sender.hasPermission("normalbonfire.give") || sender instanceof ConsoleCommandSender) {
                        target.getInventory().addItem(campfireItem);
                        sender.sendMessage(Color.color(getConfig().getString("message.succesfull-give")));
                    } else {
                        sender.sendMessage(Color.color(getConfig().getString("message.no-permission")));
                    }
                } else {
                    sender.sendMessage(Color.color(getConfig().getString("message.no-player")));
                }
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("normalbonfire.reload")) {
                    reloadConfig();
                    sender.sendMessage(Color.color(getConfig().getString("message.reload")));
                } else {
                    sender.sendMessage(Color.color(getConfig().getString("message.no-permission-reload")));
                }
                return true;
            }
        }
        return false;
    }
}
