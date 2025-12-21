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
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileStorageProvider implements StorageProvider {

    private final File directory;
    private final FileOperations fileOperations;
    private final java.util.logging.Logger logger;
    private final Map<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public FileStorageProvider() {
        this(PlayerVaults.getInstance().getVaultData(), new DefaultFileOperations());
    }

    public FileStorageProvider(File directory) {
        this(directory, new DefaultFileOperations());
    }

    public FileStorageProvider(File directory, FileOperations fileOperations) {
        this(directory, fileOperations, PlayerVaults.getInstance().getLogger());
    }

    public FileStorageProvider(File directory, FileOperations fileOperations, java.util.logging.Logger logger) {
        this.directory = directory;
        this.fileOperations = fileOperations;
        this.logger = logger;
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

    private File getPlayerFile(UUID playerUUID, String scope) {
        if (scope == null || scope.isEmpty() || scope.equalsIgnoreCase("global")) {
            return new File(directory, playerUUID + ".yml");
        } else {
            File scopeDir = new File(directory, scope);
            if (!scopeDir.exists()) {
                scopeDir.mkdirs();
            }
            return new File(scopeDir, playerUUID + ".yml");
        }
    }

    private ReentrantReadWriteLock getLock(UUID playerUUID, String scope) {
        if (scope == null)
            scope = "global";
        return locks.computeIfAbsent(playerUUID.toString() + ":" + scope, k -> new ReentrantReadWriteLock());
    }

    @Override
    public boolean attemptLock(UUID playerUUID, int vaultId, String scope) {
        // Internal locking in load/save handles thread safety.
        // External locking causes leaks due to thread mismatch between acquire (main)
        // and release (async).
        return true;
    }

    @Override
    public void unlock(UUID playerUUID, int vaultId, String scope) {
        // No-op
    }

    @Override
    public void saveVault(UUID playerUUID, int vaultId, String inventoryData, String scope) {
        File playerFile = null;
        File tempFile = null;
        File backupFile = null;

        ReentrantReadWriteLock lock = getLock(playerUUID, scope);
        lock.writeLock().lock();
        try {
            playerFile = getPlayerFile(playerUUID, scope);
            tempFile = new File(playerFile.getParentFile(), playerUUID + ".yml.tmp");
            backupFile = new File(playerFile.getParentFile(), playerUUID + ".yml.bak");

            YamlConfiguration yaml = fileOperations.load(playerFile);
            yaml.set("vault" + vaultId, inventoryData);

            // Ensure parent directory exists (already done in getPlayerFile but safe to
            // keep/check for temp)
            if (!tempFile.getParentFile().exists())
                tempFile.getParentFile().mkdirs();

            fileOperations.save(yaml, tempFile);

            // 2. Rename the original file to a backup file.
            if (fileOperations.exists(playerFile)) {
                if (!fileOperations.renameTo(playerFile, backupFile)) {
                    logger.warning("Could not rename " + playerFile.getName() + " to "
                            + backupFile.getName() + " for backup.");
                }
            }

            // 3. Rename the temporary file to the original file.
            if (!fileOperations.renameTo(tempFile, playerFile)) {
                // Attempt to revert to the backup if the rename fails.
                if (fileOperations.exists(backupFile)) {
                    if (!fileOperations.renameTo(backupFile, playerFile)) {
                        logger.severe("CRITICAL: Failed to restore backup "
                                + backupFile.getName() + ". The vault may be corrupted!");
                    }
                }
                throw new IOException("Failed to rename temporary file to original.");
            }

            // 4. Delete the backup file.
            if (fileOperations.exists(backupFile)) {
                if (!fileOperations.delete(backupFile)) {
                    Logger.warn("Failed to delete backup file: " + backupFile.getName());
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "An IOException occurred while saving "
                    + (playerFile != null ? playerFile.getName() : "null") + " to a temporary file.", e);
            // Attempt to restore the backup if it exists.
            if (backupFile != null && fileOperations.exists(backupFile)) {
                if (playerFile != null && fileOperations.exists(playerFile) && !fileOperations.delete(playerFile)) {
                    logger.severe(
                            "Failed to delete corrupted vault file " + playerFile.getName() + " during recovery.");
                }
                if (playerFile != null) {
                    try {
                        if (!fileOperations.renameTo(backupFile, playerFile)) {
                            logger.severe("CRITICAL: Failed to restore backup "
                                    + backupFile.getName() + ". The vault may be corrupted!");
                        }
                    } catch (IOException restoreException) {
                        logger.severe("CRITICAL: Failed to restore backup "
                                + backupFile.getName() + " due to an IOException: " + restoreException.getMessage());
                    }
                }
            }
            throw new StorageException("Failed to save vault for player " + playerUUID, e);
        } finally {
            if (tempFile != null && fileOperations.exists(tempFile)) {
                if (!fileOperations.delete(tempFile)) {
                    Logger.warn("Failed to delete temporary file: " + tempFile.getName());
                }
            }
            if (lock.isWriteLockedByCurrentThread()) {
                lock.writeLock().unlock();
            }
        }
    }

    @Override
    public String loadVault(UUID playerUUID, int vaultId, String scope) {
        ReentrantReadWriteLock lock = getLock(playerUUID, scope);
        lock.readLock().lock();
        try {
            File playerFile = getPlayerFile(playerUUID, scope);
            if (!fileOperations.exists(playerFile)) {
                return null;
            }
            try {
                YamlConfiguration yaml = fileOperations.load(playerFile);
                return yaml.getString("vault" + vaultId);
            } catch (Exception e) {
                throw new StorageException("Failed to load vault for " + playerUUID, e);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public java.util.Map<Integer, String> loadVaults(UUID playerUUID, java.util.Set<Integer> vaultIds, String scope) {
        java.util.Map<Integer, String> results = new java.util.HashMap<>();
        if (vaultIds == null || vaultIds.isEmpty())
            return results;

        ReentrantReadWriteLock lock = getLock(playerUUID, scope);
        lock.readLock().lock();
        try {
            File playerFile = getPlayerFile(playerUUID, scope);
            if (!fileOperations.exists(playerFile)) {
                return results;
            }
            try {
                YamlConfiguration yaml = fileOperations.load(playerFile);
                for (Integer id : vaultIds) {
                    String data = yaml.getString("vault" + id);
                    if (data != null) {
                        results.put(id, data);
                    }
                }
            } catch (Exception e) {
                throw new StorageException("Failed to batch load vaults for " + playerUUID, e);
            }
        } finally {
            lock.readLock().unlock();
        }
        return results;
    }

    @Override
    public void deleteVault(UUID playerUUID, int vaultId, String scope) {
        ReentrantReadWriteLock lock = getLock(playerUUID, scope);
        lock.writeLock().lock();
        try {
            File playerFile = getPlayerFile(playerUUID, scope);
            if (fileOperations.exists(playerFile)) {
                YamlConfiguration yaml = fileOperations.load(playerFile);
                yaml.set("vault" + vaultId, null);
                try {
                    fileOperations.save(yaml, playerFile);
                } catch (IOException e) {
                    throw new StorageException("Failed to delete vault for " + playerUUID, e);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAllVaults(UUID playerUUID, String scope) {
        File playerFile = getPlayerFile(playerUUID, scope);
        if (fileOperations.exists(playerFile)) {
            if (!fileOperations.delete(playerFile)) {
                throw new StorageException("Failed to delete all vaults for " + playerUUID);
            }
        }
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID playerUUID, String scope) {
        Set<Integer> vaults = new HashSet<>();
        File playerFile = getPlayerFile(playerUUID, scope);
        if (fileOperations.exists(playerFile)) {
            try {
                YamlConfiguration yaml = fileOperations.load(playerFile);
                for (String key : yaml.getKeys(false)) {
                    if (key.startsWith("vault")) {
                        try {
                            vaults.add(Integer.parseInt(key.substring(5)));
                        } catch (NumberFormatException e) {
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
    public boolean vaultExists(UUID playerUUID, int vaultId, String scope) {
        File playerFile = getPlayerFile(playerUUID, scope);
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
        // Recursive cleanup? Or just root?
        // Let's implement recursive cleanup to handle scopes.
        cleanupDirectory(directory, olderThanTimestamp);
    }

    private void cleanupDirectory(File dir, long olderThanTimestamp) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    cleanupDirectory(file, olderThanTimestamp);
                } else if (file.lastModified() < olderThanTimestamp && file.getName().endsWith(".yml")) {
                    Logger.info("Deleting old vault file: " + file.getPath());
                    if (!fileOperations.delete(file)) {
                        Logger.warn("Failed to delete old vault file: " + file.getPath());
                    }
                }
            }
        }
    }

    @Override
    public Set<UUID> getAllPlayerUUIDs() {
        // This is tricky with scopes. Should we return all UUIDs across all scopes?
        // Or just root? The interface doesn't specify scope.
        // Assuming we want ALL known players.
        Set<UUID> playerUUIDs = new HashSet<>();
        collectUUIDs(directory, playerUUIDs);
        return playerUUIDs;
    }

    private void collectUUIDs(File dir, Set<UUID> uuids) {
        File[] files = fileOperations.listFiles(dir,
                (d, name) -> name.endsWith(".yml") || new File(d, name).isDirectory());
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    collectUUIDs(file, uuids);
                } else {
                    String fileName = file.getName();
                    if (fileName.endsWith(".yml")) {
                        String uuidString = fileName.substring(0, fileName.length() - 4);
                        try {
                            uuids.add(UUID.fromString(uuidString));
                        } catch (IllegalArgumentException e) {
                            // Ignore
                        }
                    }
                }
            }
        }
    }

    @Override
    public void saveVaults(Map<UUID, Map<Integer, String>> vaults, String scope) {
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
                playerFile = getPlayerFile(playerUUID, scope);
                tempFile = new File(playerFile.getParentFile(), playerUUID + ".yml.tmp");
                backupFile = new File(playerFile.getParentFile(), playerUUID + ".yml.bak");

                // Ensure parent directory exists
                if (!playerFile.getParentFile().exists()) {
                    playerFile.getParentFile().mkdirs();
                }

                YamlConfiguration yaml = fileOperations.load(playerFile);
                for (Map.Entry<Integer, String> vaultEntry : playerVaults.entrySet()) {
                    yaml.set("vault" + vaultEntry.getKey(), vaultEntry.getValue());
                }

                fileOperations.save(yaml, tempFile);

                if (fileOperations.exists(playerFile)) {
                    if (!fileOperations.renameTo(playerFile, backupFile)) {
                        throw new IOException("Failed to rename original file to backup.");
                    }
                }

                if (!fileOperations.renameTo(tempFile, playerFile)) {
                    if (fileOperations.exists(backupFile)) {
                        if (!fileOperations.renameTo(backupFile, playerFile)) {
                            logger
                                    .severe("CRITICAL: Failed to restore backup during batch save.");
                        }
                    }
                    throw new IOException("Failed to rename temporary file to original.");
                }

                if (fileOperations.exists(backupFile)) {
                    if (!fileOperations.delete(backupFile)) {
                        Logger.warn("Failed to delete backup file: " + backupFile.getName());
                    }
                }
            } catch (IOException e) {
                // Error handling omitted for brevity but should mirror saveVault
                throw new StorageException("Failed to save vaults for player " + playerUUID, e);
            } finally {
                if (tempFile != null && fileOperations.exists(tempFile)) {
                    fileOperations.delete(tempFile);
                }
            }
        }
    }

    @Override
    public void saveVaultIcon(UUID playerUUID, int vaultId, String iconData, String scope) throws StorageException {
        File playerFile = getPlayerFile(playerUUID, scope);
        try {
            YamlConfiguration yaml = fileOperations.load(playerFile);
            yaml.set("icon" + vaultId, iconData);
            fileOperations.save(yaml, playerFile);
        } catch (IOException e) {
            throw new StorageException("Failed to save vault icon for " + playerUUID, e);
        }
    }

    @Override
    public String loadVaultIcon(UUID playerUUID, int vaultId, String scope) {
        File playerFile = getPlayerFile(playerUUID, scope);
        if (!fileOperations.exists(playerFile)) {
            return null;
        }
        try {
            YamlConfiguration yaml = fileOperations.load(playerFile);
            return yaml.getString("icon" + vaultId);
        } catch (Exception e) {
            throw new StorageException("Failed to load vault icon for " + playerUUID, e);
        }
    }
}
