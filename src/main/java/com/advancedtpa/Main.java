package com.advancedtpa;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
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
        if (getCommand("tpa") != null) getCommand("tpa").setExecutor(this);
        if (getCommand("tpaccept") != null) getCommand("tpaccept").setExecutor(this);
        if (getCommand("tpdeny") != null) getCommand("tpdeny").setExecutor(this);
        if (getCommand("tpagui") != null) getCommand("tpagui").setExecutor(this);
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
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /tpa <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null && target != player) {
                tpaRequests.put(target.getUniqueId(), player.getUniqueId());
                target.playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                // Clickable Text Components for TPA Request
                Component acceptCmd = Component.text("/tpaccept")
                        .color(TextColor.color(0, 255, 0))
                        .clickEvent(ClickEvent.runCommand("/tpaccept"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to run /tpaccept")));

                Component denyCmd = Component.text("/tpdeny")
                        .color(TextColor.color(255, 0, 0))
                        .clickEvent(ClickEvent.runCommand("/tpdeny"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to run /tpdeny")));

                Component tpaMsg = Component.text("TPA request from " + player.getName() + ". Do ")
                        .color(TextColor.color(255, 215, 0))
                        .append(acceptCmd)
                        .append(Component.text(" or ").color(TextColor.color(255, 255, 255)))
                        .append(denyCmd);

                target.sendMessage(tpaMsg);
                player.sendMessage(ChatColor.YELLOW + "Request sent to " + target.getName());
            } else {
                player.sendMessage(ChatColor.RED + "Player not found!");
            }
            return true;
        }
        if (command.getName().equalsIgnoreCase("tpaccept")) {
            if (tpaRequests.containsKey(player.getUniqueId())) {
                Player req = Bukkit.getPlayer(tpaRequests.remove(player.getUniqueId()));
                if (req != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    req.sendMessage(ChatColor.GREEN + "Request accepted!");
                    player.sendMessage(ChatColor.GREEN + "You accepted the TPA request!");
                    startCountdown(req, player.getLocation());
                }
            } else {
                player.sendMessage(ChatColor.RED + "No pending TPA request!");
            }
            return true;
        }
        if (command.getName().equalsIgnoreCase("tpdeny")) {
            if (tpaRequests.containsKey(player.getUniqueId())) {
                Player req = Bukkit.getPlayer(tpaRequests.remove(player.getUniqueId()));
                if (req != null) {
                    req.sendMessage(ChatColor.RED + "Your TPA request was denied.");
                }
                player.sendMessage(ChatColor.YELLOW + "TPA request denied.");
            } else {
                player.sendMessage(ChatColor.RED + "No pending TPA request!");
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
            if (meta != null) {
                meta.setOwningPlayer(online);
                meta.setDisplayName(ChatColor.GREEN + online.getName());
                head.setItemMeta(meta);
            }
            gui.addItem(head);
        }
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Online Players")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                Player p = (Player) e.getWhoClicked();
                SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
                if (meta != null && meta.getOwningPlayer() != null) {
                    Player target = meta.getOwningPlayer().getPlayer();
                    p.closeInventory();
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                    if (target != null) {
                        tpaRequests.put(target.getUniqueId(), p.getUniqueId());
                        target.playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);

                        Component acceptCmd = Component.text("/tpaccept")
                                .color(TextColor.color(0, 255, 0))
                                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to run /tpaccept")));

                        Component denyCmd = Component.text("/tpdeny")
                                .color(TextColor.color(255, 0, 0))
                                .clickEvent(ClickEvent.runCommand("/tpdeny"))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to run /tpdeny")));

                        Component tpaMsg = Component.text("TPA request from " + p.getName() + ". Do ")
                                .color(TextColor.color(255, 215, 0))
                                .append(acceptCmd)
                                .append(Component.text(" or ").color(TextColor.color(255, 255, 255)))
                                .append(denyCmd);

                        target.sendMessage(tpaMsg);
                        p.sendMessage(ChatColor.YELLOW + "Request sent!");
                    }
                }
            }
        }
    }

    private void startCountdown(Player player, Location targetLoc) {
        startLocations.put(player.getUniqueId(), player.getLocation().clone());
        final int[] count = {5};
        Random rand = new Random();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Location start = startLocations.get(player.getUniqueId());
                if (start != null && player.getLocation().distanceSquared(start) > 1) {
                    player.sendTitle(ChatColor.RED + "Teleport Cancelled!", "You moved!", 0, 30, 10);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    startLocations.remove(player.getUniqueId());
                    activeTeleports.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }
                if (count[0] > 0) {
                    player.sendTitle(ChatColor.AQUA + "Teleporting in " + count[0] + "s", ChatColor.YELLOW + "Don't move!", 0, 25, 0);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.1);
                    count[0]--;
                } else {
                    player.teleport(targetLoc);
                    player.sendTitle(ChatColor.GREEN + "Teleported!", "", 0, 30, 10);
                    player.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    startLocations.remove(player.getUniqueId());
                    activeTeleports.remove(player.getUniqueId());
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        activeTeleports.put(player.getUniqueId(), task);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        double money = (economy != null) ? economy.getBalance(p) : 0.0;
        int kills = p.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = p.getStatistic(Statistic.DEATHS);
        String rank = p.isOp() ? "Admin" : "Member"; // Custom rank placeholder

        // Hover text for chat name
        String hoverStr = "§6&lPlayer Stats\n§eMoney: §a$" + money + "\n§eKills: §c" + kills + "\n§eDeaths: §b" + deaths + "\n§eRank: §d" + rank;
        Component hoverComp = LegacyComponentSerializer.legacySection().deserialize(hoverStr);

        Component playerNameComp = Component.text(p.getName())
                .color(TextColor.color(255, 255, 0))
                .hoverEvent(HoverEvent.showText(hoverComp));

        Component finalMsg = Component.text()
                .append(playerNameComp)
                .append(Component.text(": ").color(TextColor.color(255, 255, 255)))
                .append(event.message())
                .build();

        event.renderer((source, sourceDisplayName, message, viewer) -> finalMsg);
    }
}
