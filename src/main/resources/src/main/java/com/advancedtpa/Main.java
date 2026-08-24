package com.advancedtpa;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        getLogger().info("AdvancedTPA has been enabled!");
        getCommand("tpa").setExecutor(this);
        getCommand("tpaccept").setExecutor(this);
    }

    @Override
    public void onDisable() {
        getLogger().info("AdvancedTPA has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("tpa")) {
            player.sendMessage(ChatColor.GREEN + "TPA command working successfully!");
            return true;
        }
        return false;
    }
}
