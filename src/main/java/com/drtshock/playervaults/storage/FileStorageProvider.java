package com.drtshock.playervaults.storage;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level; // Added import for Level

public class FileStorageProvider implements StorageProvider {

    private final File directory;
    private final FileOperations fileOperations;

    public FileStorageProvider() {
        this(PlayerVaults.getInstance().getVaultData(), new DefaultFileOperations());
    }

    public FileStorageProvider(File directory) {
        this(directory, new DefaultFileOperations());
    }

    public FileStorageProvider(File directory, FileOperations fileOperations) {
        this.directory = directory;
        this.fileOperations = fileOperations;
    }

    public File getDirectory() {
        return directory;
    }

    @Override
    public void initialize() {
        if (!this.fileOperations.exists(this.directory)) {
            if (!this.directory.mkdirs()) { // Check return value of mkdirs()
                Logger.warn("Failed to create directory: " + this.directory.getAbsolutePath());
            }
        }
    }

    @Override
    public void shutdown() {
        // No-op for file storage
    }

    @Override
    public void saveVault(UUID playerUUID, int vaultId, String inventoryData) {
        File playerFile = null;
        File tempFile = null;
        File backupFile = null;

        try {
            playerFile = new File(directory, playerUUID + ".yml");
            tempFile = new File(directory, playerUUID + ".yml.tmp");
            backupFile = new File(directory, playerUUID + ".yml.bak");
            YamlConfiguration yaml = fileOperations.load(playerFile);
            yaml.set("vault" + vaultId, inventoryData);
            tempFile.getParentFile().mkdirs(); // Ensure parent directory for tempFile exists
            fileOperations.save(yaml, tempFile);

            // 2. Rename the original file to a backup file.
            // This is the atomic part of the operation. If this fails, the original file is still intact.
            if (fileOperations.exists(playerFile)) {
                backupFile.getParentFile().mkdirs(); // Ensure parent directory for backupFile exists
                // If the rename fails, it might be because of file permissions or other issues.
                // We'll log a warning but proceed. The critical part is writing the new file.
                // If the new file write succeeds, the old data is preserved in memory for the session
                // and will be overwritten on the next successful save. If it fails, we can still
                // try to recover.
                if (!fileOperations.renameTo(playerFile, backupFile)) {
                    PlayerVaults.getInstance().getLogger().warning("Could not rename " + playerFile.getName() + " to " + backupFile.getName() + " for backup.");
                }
            }

            // 3. Rename the temporary file to the original file.
            // This is the point of no return. If this fails, we will attempt to revert to the backup.
            if (!fileOperations.renameTo(tempFile, playerFile)) {
                // Attempt to revert to the backup if the rename fails.
                if (fileOperations.exists(backupFile)) {
                    if (!fileOperations.renameTo(backupFile, playerFile)) {
                        PlayerVaults.getInstance().getLogger().severe("CRITICAL: Failed to restore backup " + backupFile.getName() + ". The vault may be corrupted!");
                    }
                }
                throw new IOException("Failed to rename temporary file to original.");
            }

            // 4. Delete the backup file.
            // The backup file is no longer needed after a successful save.
            if (fileOperations.exists(backupFile)) {
                if (!fileOperations.delete(backupFile)) { // Check return value of delete()
                    // If the backup deletion fails, it's not a critical error, but we should log it.
                    Logger.warn("Failed to delete backup file: " + backupFile.getName());
                }
            }
        } catch (IOException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "An IOException occurred while saving " + (playerFile != null ? playerFile.getName() : "null") + " to a temporary file.", e);
            // Attempt to restore the backup if it exists.
            if (backupFile != null && fileOperations.exists(backupFile)) {
                if (playerFile != null && fileOperations.exists(playerFile) && !fileOperations.delete(playerFile)) { // Check return value of delete()
                    PlayerVaults.getInstance().getLogger().severe("Failed to delete corrupted vault file " + playerFile.getName() + " during recovery.");
                }
                if (playerFile != null) {
                    try {
                        if (!fileOperations.renameTo(backupFile, playerFile)) {
                            PlayerVaults.getInstance().getLogger().severe("CRITICAL: Failed to restore backup " + backupFile.getName() + ". The vault may be corrupted!");
                        }
                    } catch (IOException restoreException) {
                        PlayerVaults.getInstance().getLogger().severe("CRITICAL: Failed to restore backup " + backupFile.getName() + " due to an IOException: " + restoreException.getMessage());
                    }
                }
            }
            throw new StorageException("Failed to save vault for player " + playerUUID, e);
        } finally {
            if (tempFile != null && fileOperations.exists(tempFile)) {
                if (!fileOperations.delete(tempFile)) { // Check return value of delete()
                    Logger.warn("Failed to delete temporary file: " + tempFile.getName());
                }
            }
        }
    }

    @Override
    public String loadVault(UUID playerUUID, int vaultId) {
        File playerFile = new File(directory, playerUUID + ".yml");
        if (!fileOperations.exists(playerFile)) {
            return null;
        }
        try {
            YamlConfiguration yaml = fileOperations.load(playerFile);
            return yaml.getString("vault" + vaultId);
        } catch (Exception e) {
            throw new StorageException("Failed to load vault for " + playerUUID, e);
        }
    }

    @Override
    public void deleteVault(UUID playerUUID, int vaultId) {
        File playerFile = new File(directory, playerUUID + ".yml");
        if (fileOperations.exists(playerFile)) {
            YamlConfiguration yaml = fileOperations.load(playerFile);
            yaml.set("vault" + vaultId, null);
            try {
                fileOperations.save(yaml, playerFile);
            } catch (IOException e) {
                throw new StorageException("Failed to delete vault for " + playerUUID, e);
            }
        }
    }

    @Override
    public void deleteAllVaults(UUID playerUUID) {
        File playerFile = new File(directory, playerUUID + ".yml");
        if (fileOperations.exists(playerFile)) {
            if (!fileOperations.delete(playerFile)) {
                throw new StorageException("Failed to delete all vaults for " + playerUUID);
            }
        }
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID playerUUID) {
        Set<Integer> vaults = new HashSet<>();
        File playerFile = new File(directory, playerUUID + ".yml");
        if (fileOperations.exists(playerFile)) {
            try {
                YamlConfiguration yaml = fileOperations.load(playerFile);
                for (String key : yaml.getKeys(false)) {
                    if (key.startsWith("vault")) {
                        try {
                            vaults.add(Integer.parseInt(key.substring(5)));
                        } catch (NumberFormatException e) {
                            // Ignore, but log for debugging
                            Logger.warn("Invalid vault key found in " + playerFile.getName() + ": " + key);
                        }
                    }
                }
            } catch (Exception e) {
                throw new StorageException("Failed to get vault numbers for " + playerUUID, e);
            }
        }
        return vaults;
    }

    @Override
    public boolean vaultExists(UUID playerUUID, int vaultId) {
        File playerFile = new File(directory, playerUUID + ".yml");
        if (!fileOperations.exists(playerFile)) {
            return false;
        }
        try {
            YamlConfiguration yaml = fileOperations.load(playerFile);
            return yaml.contains("vault" + vaultId);
        } catch (Exception e) {
            throw new StorageException("Failed to check if vault exists for " + playerUUID, e);
        }
    }

    @Override
    public void cleanup(long olderThanTimestamp) {
        File[] files = directory.listFiles(); // Get files from the directory
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.lastModified() < olderThanTimestamp) {
                    Logger.info("Deleting old vault file: " + file.getName());
                    if (!fileOperations.delete(file)) {
                        throw new StorageException("Failed to delete old vault file: " + file.getName());
                    }
                }
            }
        }
    }

    @Override
    public Set<UUID> getAllPlayerUUIDs() {
        Set<UUID> playerUUIDs = new HashSet<>();
        File[] files = fileOperations.listFiles(directory, (dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String uuidString = fileName.substring(0, fileName.length() - 4); // Remove .yml
                try {
                    playerUUIDs.add(UUID.fromString(uuidString));
                } catch (IllegalArgumentException e) {
                    Logger.warn("Invalid UUID filename found: " + fileName);
                }
            }
        }
        return playerUUIDs;
    }

    @Override
    public void saveVaults(Map<UUID, Map<Integer, String>> vaults) {
        for (Map.Entry<UUID, Map<Integer, String>> playerEntry : vaults.entrySet()) {
            UUID playerUUID = playerEntry.getKey();
            Map<Integer, String> playerVaults = playerEntry.getValue();
            if (playerVaults.isEmpty()) {
                continue;
            }

            File playerFile = null;
            File tempFile = null;
            File backupFile = null;

            try {
                playerFile = new File(directory, playerUUID + ".yml");
                tempFile = new File(directory, playerUUID + ".yml.tmp");
                backupFile = new File(directory, playerUUID + ".yml.bak");
                // 1. Load existing data if any, and merge with new data.
                YamlConfiguration yaml = fileOperations.load(playerFile);
                for (Map.Entry<Integer, String> vaultEntry : playerVaults.entrySet()) {
                    yaml.set("vault" + vaultEntry.getKey(), vaultEntry.getValue());
                }
                tempFile.getParentFile().mkdirs(); // Ensure parent directory for tempFile exists
                fileOperations.save(yaml, tempFile);

                // 2. Atomic rename operations for safety.
                if (fileOperations.exists(playerFile)) {
                    backupFile.getParentFile().mkdirs(); // Ensure parent directory for backupFile exists
                    if (!fileOperations.renameTo(playerFile, backupFile)) {
                        throw new IOException("Failed to rename original file to backup.");
                    }
                }

                if (!fileOperations.renameTo(tempFile, playerFile)) {
                    if (fileOperations.exists(backupFile)) {
                        if (!fileOperations.renameTo(backupFile, playerFile)) {
                            PlayerVaults.getInstance().getLogger().severe("CRITICAL: Failed to restore backup " + backupFile.getName() + " during batch save. The vault may be corrupted!");
                        }
                    }
                    throw new IOException("Failed to rename temporary file to original.");
                }

                if (fileOperations.exists(backupFile)) {
                    if (!fileOperations.delete(backupFile)) { // Check return value of delete()
                        Logger.warn("Failed to delete backup file: " + backupFile.getName());
                    }
                }
            } catch (IOException e) {
                if (backupFile != null && fileOperations.exists(backupFile) && (playerFile == null || !fileOperations.exists(playerFile))) {
                    if (playerFile != null) {
                        try {
                            if (!fileOperations.renameTo(backupFile, playerFile)) {
                                PlayerVaults.getInstance().getLogger().severe("CRITICAL: Failed to restore backup " + backupFile.getName() + " during batch save. The vault may be corrupted!");
                            }
                        } catch (IOException restoreException) {
                            PlayerVaults.getInstance().getLogger().severe("CRITICAL: Failed to restore backup " + backupFile.getName() + " during batch save due to an IOException: " + restoreException.getMessage());
                        }
                    }
                }
                throw new StorageException("Failed to save vaults for player " + playerUUID, e);
            } finally {
                if (tempFile != null && fileOperations.exists(tempFile)) {
                    if (!fileOperations.delete(tempFile)) { // Check return value of delete()
                        Logger.warn("Failed to delete temporary file: " + tempFile.getName());
                    }
                }
            }
        }
    }
}
