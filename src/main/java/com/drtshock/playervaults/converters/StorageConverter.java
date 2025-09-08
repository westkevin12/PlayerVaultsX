package com.drtshock.playervaults.converters;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.storage.FileStorageProvider;
import com.drtshock.playervaults.storage.MySQLStorageProvider;
import com.drtshock.playervaults.storage.StorageProvider;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.UUID;

public class StorageConverter implements Converter {

    private final PlayerVaults plugin;
    private final String to;

    public StorageConverter(PlayerVaults plugin, String to) {
        this.plugin = plugin;
        this.to = to;
    }

    @Override
    public int run(CommandSender sender) {
        StorageProvider fromProvider;
        StorageProvider toProvider;

        if (to.equalsIgnoreCase("mysql")) {
            fromProvider = new FileStorageProvider();
            toProvider = new MySQLStorageProvider();
        } else if (to.equalsIgnoreCase("file")) {
            fromProvider = new MySQLStorageProvider();
            toProvider = new FileStorageProvider();
        } else {
            sender.sendMessage("Invalid storage type. Use 'mysql' or 'file'.");
            return 0;
        }

        fromProvider.initialize();
        toProvider.initialize();

        int count = 0;
        if (fromProvider instanceof FileStorageProvider) {
            File vaultsFolder = plugin.getVaultData();
            for (File file : vaultsFolder.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".yml")) {
                    String uuidString = file.getName().replace(".yml", "");
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        for (int vaultId : fromProvider.getVaultNumbers(uuid)) {
                            String data = fromProvider.loadVault(uuid, vaultId);
                            toProvider.saveVault(uuid, vaultId, data);
                            count++;
                        }
                    } catch (IllegalArgumentException e) {
                        // Not a valid UUID file
                    }
                }
            }
        } else {
            // This is tricky without a list of all players. We will have to assume that the server has seen all players.
            // A better approach would be to query the database for all distinct player UUIDs.
            // For now, we will just convert the vaults of all online players.
            plugin.getServer().getOnlinePlayers().forEach(player -> {
                for (int vaultId : fromProvider.getVaultNumbers(player.getUniqueId())) {
                    String data = fromProvider.loadVault(player.getUniqueId(), vaultId);
                    toProvider.saveVault(player.getUniqueId(), vaultId, data);
                }
            });
        }

        fromProvider.shutdown();
        toProvider.shutdown();

        return count;
    }

    @Override
    public boolean canConvert() {
        return true;
    }

    @Override
    public String getName() {
        return "storage";
    }
}
