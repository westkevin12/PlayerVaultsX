package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.tasks.S3Backup;
import com.drtshock.playervaults.util.Permission;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BackupCommand implements CommandExecutor {

    private final PlayerVaults plugin;

    public BackupCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Permission.ADMIN)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
            return true;
        }

        if (!plugin.getConf().getStorage().getS3().isEnabled()) {
            sender.sendMessage(ChatColor.RED + "S3 backups are not enabled in the config.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Starting asynchronous S3 backup...");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new S3Backup(plugin));

        return true;
    }
}
