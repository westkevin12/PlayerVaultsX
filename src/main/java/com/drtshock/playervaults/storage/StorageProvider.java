package com.drtshock.playervaults.storage;

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
}
