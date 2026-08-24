package com.advancedtpa;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener, CommandExecutor {

    private final HashMap<UUID, UUID> tpaRequests = new HashMap<>();
    private final HashMap<UUID, BukkitTask> activeTeleports = new HashMap<>();
    private final HashMap<UUID, Location> startLocations = new HashMap<>();
    private static Economy economy = null;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        getLogger().info("AdvancedTPA Enabled!");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("tpa").setExecutor(this);
        getCommand("tpaccept").setExecutor(this);
        getCommand("tpagui").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("tpagui")) {
            openTpaGui(player);
            return true;
        }
        if (command.getName().equalsIgnoreCase("tpa")) {
            if (args.length < 0) return true;
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null && target != player) {
                tpaRequests.put(target.getUniqueId(), player.getUniqueId());
                target.sendMessage(ChatColor.GOLD + "[TPA] " + ChatColor.YELLOW + player.getName() + " sent a TPA request!");
                player.sendMessage(ChatColor.YELLOW + "Request sent to " + target.getName());
            }
            return true;
        }
        if (command.getName().equalsIgnoreCase("tpaccept")) {
            if (tpaRequests.containsKey(player.getUniqueId())) {
                Player req = Bukkit.getPlayer(tpaRequests.remove(player.getUniqueId()));
                if (req != null) {
                    req.sendMessage(ChatColor.GREEN + "Request accepted!");
                    startCountdown(req, player.getLocation());
                }
            }
            return true;
        }
        return false;
    }

    private void openTpaGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Online Players");
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            meta.setDisplayName(ChatColor.GREEN + online.getName());
            head.setItemMeta(meta);
            gui.addItem(head);
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Online Players")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                Player p = (Player) e.getWhoClicked();
                SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
                Player target = meta.getOwningPlayer().getPlayer();
                p.closeInventory();
                if (target != null) {
                    tpaRequests.put(target.getUniqueId(), p.getUniqueId());
                    target.sendMessage(ChatColor.YELLOW + p.getName() + " sent TPA via GUI!");
                    p.sendMessage(ChatColor.YELLOW + "Request sent!");
                }
            }
        }
    }

    private void startCountdown(Player player, Location targetLoc) {
        startLocations.put(player.getUniqueId(), player.getLocation().clone());
        final int[] count = {5};
        Random rand = new Random();

        new BukkitRunnable() {
            @Override
            public void run() {
                Location start = startLocations.get(player.getUniqueId());
                if (start != null && player.getLocation().distanceSquared(start) > 1) {
                    player.sendTitle(ChatColor.RED + "Cancelled!", "You moved!", 0, 30, 10);
                    startLocations.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }
                if (count[0] > 0) {
                    player.sendTitle(ChatColor.AQUA + "Teleporting in " + count[0], "Don't move!", 0, 25, 0);
                    Particle[] particles = {Particle.PORTAL, Particle.FLAME, Particle.HEART, Particle.ELECTRIC_SPARK, Particle.SPELL_WITCH, Particle.TOTEM, Particle.CRIT_MAGIC};
                    player.spawnParticle(particles[rand.nextInt(particles.length)], player.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.1);
                    count[0]--;
                } else {
                    player.teleport(targetLoc);
                    player.sendTitle(ChatColor.GREEN + "Teleported!", "", 0, 30, 10);
                    startLocations.remove(player.getUniqueId());
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        double money = (economy != null) ? economy.getBalance(p) : 0.0;
        String hoverStr = "§6Stats\n§eMoney: §a$" + money + "\n§eKills: §c" + p.getStatistic(Statistic.PLAYER_KILLS) + "\n§eDeaths: §b" + p.getStatistic(Statistic.DEATHS);
        Component msg = LegacyComponentSerializer.legacySection().deserialize("§e" + p.getName() + "§7: ")
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(hoverStr)))
                .append(event.message());
        event.renderer((s, d, m, v) -> msg);
    }
}
