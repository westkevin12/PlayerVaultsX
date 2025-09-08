package com.drtshock.playervaults.storage;

import com.drtshock.playervaults.PlayerVaults;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class FileStorageProvider implements StorageProvider {

    private final File directory;

    public FileStorageProvider() {
        this.directory = PlayerVaults.getInstance().getVaultData();
    }

    @Override
    public void initialize() {
        if (!this.directory.exists()) {
            this.directory.mkdirs();
        }
    }

    @Override
    public void shutdown() {
        // No-op for file storage
    }

    @Override
    public void saveVault(UUID playerUUID, int vaultId, String inventoryData) {
        File playerFile = new File(directory, playerUUID.toString() + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerFile);
        yaml.set("vault" + vaultId, inventoryData);
        try {
            yaml.save(playerFile);
        } catch (IOException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to save vault for " + playerUUID, e);
        }
    }

    @Override
    public String loadVault(UUID playerUUID, int vaultId) {
        File playerFile = new File(directory, playerUUID.toString() + ".yml");
        if (!playerFile.exists()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerFile);
        return yaml.getString("vault" + vaultId);
    }

    @Override
    public void deleteVault(UUID playerUUID, int vaultId) {
        File playerFile = new File(directory, playerUUID.toString() + ".yml");
        if (playerFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerFile);
            yaml.set("vault" + vaultId, null);
            try {
                yaml.save(playerFile);
            } catch (IOException e) {
                PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to delete vault for " + playerUUID, e);
            }
        }
    }

    @Override
    public void deleteAllVaults(UUID playerUUID) {
        File playerFile = new File(directory, playerUUID.toString() + ".yml");
        if (playerFile.exists()) {
            playerFile.delete();
        }
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID playerUUID) {
        Set<Integer> vaults = new HashSet<>();
        File playerFile = new File(directory, playerUUID.toString() + ".yml");
        if (playerFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerFile);
            for (String key : yaml.getKeys(false)) {
                if (key.startsWith("vault")) {
                    try {
                        vaults.add(Integer.parseInt(key.substring(5)));
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
        }
        return vaults;
    }

    @Override
    public boolean vaultExists(UUID playerUUID, int vaultId) {
        File playerFile = new File(directory, playerUUID.toString() + ".yml");
        if (!playerFile.exists()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerFile);
        return yaml.contains("vault" + vaultId);
    }

    @Override
    public void cleanup(long olderThanTimestamp) {
        for (File file : directory.listFiles()) {
            if (file.isFile() && file.lastModified() < olderThanTimestamp) {
                PlayerVaults.getInstance().getLogger().info("Deleting old vault file: " + file.getName());
                file.delete();
            }
        }
    }
}
