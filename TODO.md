
### Refactor: Offline Player Lookup

- **Added `VaultOperations.getTargetPlayer(String name)`:** A new, centralized method for looking up online and offline players by name or UUID. This method uses the deprecated `Bukkit.getOfflinePlayer(String name)` as a fallback to support legacy name-based lookups, ensuring that administrators can still manage offline players by their last known username.

- **Refactored `VaultCommand` and `DeleteCommand`:** Both commands now use the new `getTargetPlayer` method. This simplifies the code, removes duplication, and ensures consistent and reliable player lookup across the plugin.

### Fix: Resource Leak in Update Checker

- **Patched `PlayerVaults.java`:** The `BufferedReader` in the asynchronous update checker is now wrapped in a `try-with-resources` block. This guarantees that the reader is always closed, preventing a potential resource leak that could occur over time.

### Fix: Inefficient I/O in `EconomyOperations`

- **Optimized `refundOnDelete`:** The `refundOnDelete` method in `EconomyOperations` no longer reads player data files directly. Instead, it now uses `VaultManager.getInstance().vaultExists()`, which improves performance and properly respects the storage provider abstraction, making it compatible with both Flatfile and MySQL storage.

### Chore: Update API Version

- **Synced `plugin.yml`:** The `api-version` in `plugin.yml` has been updated from `1.21.6` to `1.21.8` to match the Spigot API version specified in the `pom.xml`. This ensures consistency and clarifies the plugin's intended target version.