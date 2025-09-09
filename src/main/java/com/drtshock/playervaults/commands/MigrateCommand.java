package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.Conversion;
import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.storage.FileStorageProvider;
import com.drtshock.playervaults.storage.MySQLStorageProvider;
import com.drtshock.playervaults.storage.StorageProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class MigrateCommand implements CommandExecutor {

    private final PlayerVaults plugin;

    public MigrateCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("playervaults.migrate")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("Usage: /pvxmigrate <source_storage_type> <destination_storage_type>");
            sender.sendMessage("Available storage types: file, mysql");
            return true;
        }

        String sourceStorageType = args[0].toLowerCase();
        String destinationStorageType = args[1].toLowerCase();

        StorageProvider sourceProvider = getStorageProvider(sourceStorageType);
        StorageProvider destinationProvider = getStorageProvider(destinationStorageType);

        if (sourceProvider == null || destinationProvider == null) {
            sender.sendMessage("Invalid storage type specified. Available types: file, mysql");
            return true;
        }

        sender.sendMessage("Starting vault migration from " + sourceStorageType + " to " + destinationStorageType + "...");
        sourceProvider.initialize();
        destinationProvider.initialize();
        Conversion.migrateVaults(plugin, sourceProvider, destinationProvider);
        sourceProvider.shutdown();
        destinationProvider.shutdown();
        sender.sendMessage("Vault migration complete.");
        return true;
    }

    private StorageProvider getStorageProvider(String type) {
        switch (type) {
            case "file":
                return new FileStorageProvider();
            case "mysql":
                return new MySQLStorageProvider();
            default:
                return null;
        }
    }
}
