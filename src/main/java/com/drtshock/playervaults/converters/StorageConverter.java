package com.drtshock.playervaults.converters;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.storage.FileStorageProvider;
import com.drtshock.playervaults.storage.MySQLStorageProvider;
import com.drtshock.playervaults.storage.StorageProvider;
import com.drtshock.playervaults.storage.StorageException;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class StorageConverter implements Converter {

    private final PlayerVaults plugin;
    private final String to;

    public StorageConverter(PlayerVaults plugin, String to) {
        this.plugin = plugin;
        this.to = to;
    }

    @Override
    public Object run(CommandSender sender) {
        StorageProvider fromProvider;
        StorageProvider toProvider;

        if (to.equalsIgnoreCase("mysql")) {
            fromProvider = new FileStorageProvider();
            toProvider = new MySQLStorageProvider();
        } else if (to.equalsIgnoreCase("file")) {
            fromProvider = new MySQLStorageProvider();
            File tempDir = new File(plugin.getVaultData().getParentFile(), "vaults_new");
            if (tempDir.exists()) {
                // Clean up from previous failed conversions
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
                tempDir.delete();
            }
            tempDir.mkdirs();
            toProvider = new FileStorageProvider(tempDir);
        } else {
            sender.sendMessage("Invalid storage type. Use 'mysql' or 'file'.");
            return new HashMap<>();
        }

        fromProvider.initialize();
        toProvider.initialize();

        int count = 0;
        Map<UUID, Map<Integer, String>> vaultsToSave = new HashMap<>();

        if (fromProvider instanceof FileStorageProvider) {
            File vaultsFolder = plugin.getVaultData();
            File[] files = vaultsFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".yml")) {
                        String uuidString = file.getName().replace(".yml", "");
                        try {
                            UUID uuid = UUID.fromString(uuidString);
                            Map<Integer, String> playerVaults = new HashMap<>();
                            for (int vaultId : fromProvider.getVaultNumbers(uuid, "global")) {
                                try {
                                    String data = fromProvider.loadVault(uuid, vaultId, "global");
                                    playerVaults.put(vaultId, data);
                                    count++;
                                } catch (StorageException e) {
                                    plugin.getLogger().log(Level.SEVERE,
                                            "Failed to convert vault for player " + uuid + " vault " + vaultId, e);
                                    sender.sendMessage("Error converting vault for " + uuid + " vault " + vaultId + ": "
                                            + e.getMessage());
                                }
                            }
                            if (!playerVaults.isEmpty()) {
                                vaultsToSave.put(uuid, playerVaults);
                            }
                        } catch (IllegalArgumentException e) {
                            // Not a valid UUID file
                        }
                    }
                }
            }
        } else {
            for (UUID uuid : fromProvider.getAllPlayerUUIDs()) {
                Map<Integer, String> playerVaults = new HashMap<>();
                for (int vaultId : fromProvider.getVaultNumbers(uuid, "global")) {
                    try {
                        String data = fromProvider.loadVault(uuid, vaultId, "global");
                        playerVaults.put(vaultId, data);
                        count++;
                    } catch (StorageException e) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Failed to convert vault for player " + uuid + " vault " + vaultId, e);
                        sender.sendMessage(
                                "Error converting vault for " + uuid + " vault " + vaultId + ": " + e.getMessage());
                    }
                }
                if (!playerVaults.isEmpty()) {
                    vaultsToSave.put(uuid, playerVaults);
                }
            }
        }

        if (!vaultsToSave.isEmpty()) {
            try {
                toProvider.saveVaults(vaultsToSave, "global");

                if (toProvider instanceof FileStorageProvider) {
                    File originalDir = plugin.getVaultData();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss");
                    String timestamp = sdf.format(new java.util.Date());
                    File backupDir = new File(originalDir.getParentFile(), "vaults_backup_" + timestamp);
                    File tempDir = ((FileStorageProvider) toProvider).getDirectory();

                    // 1. Rename original to backup
                    if (originalDir.exists()) {
                        try {
                            Files.move(originalDir.toPath(), backupDir.toPath(), StandardCopyOption.ATOMIC_MOVE);
                        } catch (IOException e) {
                            // Fallback if atomic move not supported (e.g. cross filesystem)
                            try {
                                Files.move(originalDir.toPath(), backupDir.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException ex) {
                                throw new StorageException(
                                        "Failed to rename original vault directory to backup: " + ex.getMessage());
                            }
                        }
                    }

                    // 2. Rename new to original
                    try {
                        Files.move(tempDir.toPath(), originalDir.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        // Attempt to revert
                        try {
                            if (backupDir.exists()) {
                                Files.move(backupDir.toPath(), originalDir.toPath(), StandardCopyOption.ATOMIC_MOVE);
                            }
                        } catch (IOException ex) {
                            sender.sendMessage("CRITICAL: Failed to restore backup. Your vaults are at: "
                                    + backupDir.getAbsolutePath());
                        }
                        throw new StorageException(
                                "Failed to activate new vault directory. Conversion reverted: " + e.getMessage());
                    }

                    sender.sendMessage("Conversion successful. Old data is backed up in: " + backupDir.getName());
                }
            } catch (StorageException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save converted vaults in bulk", e);
                sender.sendMessage("Error saving converted vaults in bulk: " + e.getMessage());

                if (toProvider instanceof FileStorageProvider) {
                    // Clean up temp dir
                    File tempDir = ((FileStorageProvider) toProvider).getDirectory();
                    for (File file : tempDir.listFiles()) {
                        file.delete();
                    }
                    tempDir.delete();
                }
            }
        }

        fromProvider.shutdown();
        toProvider.shutdown();

        Map<String, Integer> result = new HashMap<>();
        result.put("convertedVaults", count);
        result.put("affectedPlayers", vaultsToSave.size());
        return result;
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
