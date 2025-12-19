package com.drtshock.playervaults.storage;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StorageProvider {
    void saveVault(UUID playerUUID, int vaultId, String inventoryData);

    String loadVault(UUID playerUUID, int vaultId);

    void deleteVault(UUID playerUUID, int vaultId);

    void deleteAllVaults(UUID playerUUID);

    Set<Integer> getVaultNumbers(UUID playerUUID);

    boolean vaultExists(UUID playerUUID, int vaultId);

    void cleanup(long olderThanTimestamp);

    void initialize();

    void shutdown();

    Set<UUID> getAllPlayerUUIDs();

    void saveVaults(Map<UUID, Map<Integer, String>> vaults);

    void saveVaultIcon(UUID playerUUID, int vaultId, String iconData) throws StorageException;

    String loadVaultIcon(UUID playerUUID, int vaultId) throws StorageException;

    /**
     * Attempts to acquire a lock on the specific vault.
     * 
     * @param playerUUID The UUID of the vault owner
     * @param vaultId    The id of the vault
     * @return true if lock was acquired, false otherwise
     */
    default boolean attemptLock(UUID playerUUID, int vaultId) {
        return true;
    }

    /**
     * Releases the lock on the specific vault.
     * 
     * @param playerUUID The UUID of the vault owner
     * @param vaultId    The id of the vault
     */
    default void unlock(UUID playerUUID, int vaultId) {
        // no-op by default
    }
}
