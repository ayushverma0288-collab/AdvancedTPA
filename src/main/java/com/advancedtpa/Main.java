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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener, CommandExecutor {

    private static class TpaData {
        UUID senderUuid;
        boolean isTpaHere;

        public TpaData(UUID senderUuid, boolean isTpaHere) {
            this.senderUuid = senderUuid;
            this.isTpaHere = isTpaHere;
        }
    }

    private final HashMap<UUID, TpaData> tpaRequests = new HashMap<>();
    private final HashMap<UUID, UUID> sentRequests = new HashMap<>();
    private final HashSet<UUID> tpaDisabled = new HashSet<>();
    private final HashMap<UUID, BukkitTask> activeTeleports = new HashMap<>();
    private final HashMap<UUID, Location> startLocations = new HashMap<>();
    private static Economy economy = null;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        getLogger().info("AdvancedTPA Enabled Successfully!");
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("tpa") != null) getCommand("tpa").setExecutor(this);
        if (getCommand("tpahere") != null) getCommand("tpahere").setExecutor(this);
        if (getCommand("tpaccept") != null) getCommand("tpaccept").setExecutor(this);
        if (getCommand("tpdeny") != null) getCommand("tpdeny").setExecutor(this);
        if (getCommand("tpacancel") != null) getCommand("tpacancel").setExecutor(this);
        if (getCommand("tpagui") != null) getCommand("tpagui").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("tpagui")) {
            openTpaGui(player);
            return true;
        }

        if (cmdName.equals("tpa")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
                if (tpaDisabled.contains(player.getUniqueId())) {
                    tpaDisabled.remove(player.getUniqueId());
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
                    player.sendMessage(ChatColor.GREEN + "TPA requests are now ENABLED.");
                } else {
                    tpaDisabled.add(player.getUniqueId());
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                    player.sendMessage(ChatColor.RED + "TPA requests are now DISABLED.");
                }
                return true;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("cancel") || args[0].equalsIgnoreCase("cannal"))) {
                cancelTpaRequest(player);
                return true;
            }

            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /tpa <player> | /tpa toggle | /tpa cancel");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            sendTpa(player, target, false);
            return true;
        }

        if (cmdName.equals("tpacancel") || cmdName.equals("tpacannal")) {
            cancelTpaRequest(player);
            return true;
        }

        if (cmdName.equals("tpahere")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /tpahere <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            sendTpa(player, target, true);
            return true;
        }

        if (cmdName.equals("tpaccept")) {
            TpaData data = tpaRequests.remove(player.getUniqueId());
            if (data != null) {
                sentRequests.remove(data.senderUuid);
                Player reqSender = Bukkit.getPlayer(data.senderUuid);
                if (reqSender != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    reqSender.playSound(reqSender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

                    if (!data.isTpaHere) {
                        reqSender.sendMessage(ChatColor.GREEN + "Your TPA request was accepted!");
                        player.sendMessage(ChatColor.GREEN + "You accepted the TPA request!");
                        startCountdown(reqSender, player.getLocation());
                    } else {
                        player.sendMessage(ChatColor.GREEN + "You accepted the TPAHere request!");
                        reqSender.sendMessage(ChatColor.GREEN + "Your TPAHere request was accepted!");
                        startCountdown(player, reqSender.getLocation());
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "The player who sent the request is no longer online.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "No pending TPA request!");
            }
            return true;
        }

        if (cmdName.equals("tpdeny")) {
            TpaData data = tpaRequests.remove(player.getUniqueId());
            if (data != null) {
                sentRequests.remove(data.senderUuid);
                Player reqSender = Bukkit.getPlayer(data.senderUuid);
                if (reqSender != null) {
                    reqSender.sendMessage(ChatColor.RED + "Your TPA request was denied.");
                }
                player.sendMessage(ChatColor.YELLOW + "TPA request denied.");
            } else {
                player.sendMessage(ChatColor.RED + "No pending TPA request!");
            }
            return true;
        }
        return false;
    }

    private void sendTpa(Player sender, Player target, boolean isTpaHere) {
        if (target == null || target.equals(sender)) {
            sender.sendMessage(ChatColor.RED + "Player not found or invalid!");
            return;
        }
        if (tpaDisabled.contains(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "That player has disabled TPA requests.");
            return;
        }

        tpaRequests.put(target.getUniqueId(), new TpaData(sender.getUniqueId(), isTpaHere));
        sentRequests.put(sender.getUniqueId(), target.getUniqueId());

        target.playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);
        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        String text = isTpaHere ? sender.getName() + " has requested that you teleport to them." : sender.getName() + " has requested to teleport to you.";
        sendTpaRequestMessage(target, text);
        sender.sendMessage(ChatColor.YELLOW + "Request sent to " + target.getName() + ". Type /tpa cancel to cancel.");
    }

    private void cancelTpaRequest(Player sender) {
        UUID targetUuid = sentRequests.remove(sender.getUniqueId());
        if (targetUuid != null) {
            tpaRequests.remove(targetUuid);
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) {
                target.sendMessage(ChatColor.RED + sender.getName() + " cancelled the TPA request.");
            }
            sender.sendMessage(ChatColor.YELLOW + "You cancelled your TPA request.");
            sender.playSound(sender.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        } else {
            sender.sendMessage(ChatColor.RED + "You have no outgoing TPA requests to cancel.");
        }
    }

    private void sendTpaRequestMessage(Player target, String mainText) {
        Component acceptCmd = Component.text("[ACCEPT]")
                .color(TextColor.color(0, 255, 0))
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept")));

        Component denyCmd = Component.text("[DENY]")
                .color(TextColor.color(255, 0, 0))
                .clickEvent(ClickEvent.runCommand("/tpdeny"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to deny")));

        Component msg = Component.text(mainText + "\n")
                .color(TextColor.color(255, 215, 0))
                .append(acceptCmd)
                .append(Component.text("   "))
                .append(denyCmd);

        target.sendMessage(msg);
    }

    private void openTpaGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Online Players (TPA GUI)");
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(online);
                meta.setDisplayName(ChatColor.GREEN + online.getName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.YELLOW + "Click to send TPA request!");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            gui.addItem(head);
        }
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Online Players (TPA GUI)")) {
            e.setCancelled(true); // Head inventory mein kabhi nahi aayega, fully protected!
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                Player p = (Player) e.getWhoClicked();
                SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
                if (meta != null && meta.getOwningPlayer() != null) {
                    Player target = meta.getOwningPlayer().getPlayer();
                    p.closeInventory();
                    
                    // Acha sa sound head click par
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                    
                    if (target != null) {
                        sendTpa(p, target, false);
                    } else {
                        p.sendMessage(ChatColor.RED + "Player is offline!");
                    }
                }
            }
        }
    }

    private void startCountdown(Player player, Location targetLoc) {
        startLocations.put(player.getUniqueId(), player.getLocation().clone());
        final int[] count = {5};

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
        String rank = p.isOp() ? "Admin" : "Member";

        String hoverStr = "§6§lPlayer Stats\n§eMoney: §a$" + money + "\n§eKills: §c" + kills + "\n§eDeaths: §b" + deaths + "\n§eRank: §d" + rank;
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
