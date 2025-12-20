package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.util.S3Service;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class RestoreCommand implements CommandExecutor {

    private final PlayerVaults plugin;

    public RestoreCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Permission.ADMIN)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
            return true;
        }

        S3Service service = plugin.getS3Service();
        if (service == null || !service.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "S3 service is not available (check config).");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /pv restore <list|download> [filename]");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("list")) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                List<String> backups = service.listBackups();
                sender.sendMessage(ChatColor.YELLOW + "--- S3 Backups (Found " + backups.size() + ") ---");
                // Show top 10
                backups.stream().limit(10)
                        .forEach(key -> sender.sendMessage(ChatColor.GRAY + "- " + key.replace("backups/", "")));
            });
            return true;
        }

        if (sub.equals("download")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /pv restore download <filename>");
                return true;
            }
            String filename = args[1];
            // Security check: simple path traversal prevention
            if (filename.contains("..") || filename.contains("/")) {
                sender.sendMessage(ChatColor.RED + "Invalid filename.");
                return true;
            }

            String key = "backups/" + filename;

            sender.sendMessage(ChatColor.YELLOW + "Downloading " + filename + "...");
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    File restoreDir = new File(plugin.getDataFolder(), "restored_backups");
                    if (!restoreDir.exists())
                        restoreDir.mkdirs();

                    Path dest = restoreDir.toPath().resolve(filename);
                    service.downloadBackup(key, dest.toFile());

                    sender.sendMessage(ChatColor.GREEN + "Download complete!");
                    sender.sendMessage(ChatColor.GREEN + "File saved to: " + dest.toString());
                    sender.sendMessage(ChatColor.RED
                            + "NOTE: You must manually unzip this and replace files while the server is OFF to restore data.");

                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Download failed: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use list or download.");
        return true;
    }
}
