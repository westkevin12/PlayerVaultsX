package com.drtshock.playervaults.storage;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StorageProvider {
    void saveVault(UUID playerUUID, int vaultId, String inventoryData, String scope) throws StorageException;

    String loadVault(UUID playerUUID, int vaultId, String scope) throws StorageException;

    void deleteVault(UUID playerUUID, int vaultId, String scope) throws StorageException;

    void deleteAllVaults(UUID playerUUID, String scope) throws StorageException;

    Set<Integer> getVaultNumbers(UUID playerUUID, String scope) throws StorageException;

    boolean vaultExists(UUID playerUUID, int vaultId, String scope) throws StorageException;

    void cleanup(long olderThanTimestamp);

    void initialize();

    void shutdown();

    Set<UUID> getAllPlayerUUIDs();

    void saveVaults(Map<UUID, Map<Integer, String>> vaults, String scope);

    void saveVaultIcon(UUID playerUUID, int vaultId, String iconData) throws StorageException;

    String loadVaultIcon(UUID playerUUID, int vaultId) throws StorageException;

    /**
     * Attempts to acquire a lock on the specific vault.
     * 
     * @param playerUUID The UUID of the vault owner
     * @param vaultId    The id of the vault
     * @param scope      The scope (group/world)
     * @return true if lock was acquired, false otherwise
     */
    default boolean attemptLock(UUID playerUUID, int vaultId, String scope) {
        return true;
    }

    /**
     * Releases the lock on the specific vault.
     * 
     * @param playerUUID The UUID of the vault owner
     * @param vaultId    The id of the vault
     * @param scope      The scope (group/world)
     */
    default void unlock(UUID playerUUID, int vaultId, String scope) {
        // no-op by default
    }
}
