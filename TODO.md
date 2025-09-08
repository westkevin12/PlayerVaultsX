# PlayerVaultsX MySQL Support TODO

**All tasks in this document are complete.**

This document outlines the plan to add MySQL database support as a storage backend for PlayerVaultsX.

## 1. Configuration (`config.conf`)

- [x] Add a `mysql` section under `storage` in `src/main/java/com/drtshock/playervaults/config/file/Config.java`.
  - This section should contain fields for `host`, `port`, `database`, `username`, `password`, and other relevant options like `useSSL`, `connectionTimeout`, etc.
- [x] Update `storage.storage-type` to accept `"mysql"` as a valid option.

## 2. Database Abstraction Layer

- [x] Create a new package `com.drtshock.playervaults.storage`.
- [x] Create a `StorageProvider` interface in the new package. This interface will define methods for all data operations:
  - `void saveVault(UUID playerUUID, int vaultId, String inventoryData)`
  - `String loadVault(UUID playerUUID, int vaultId)`
  - `void deleteVault(UUID playerUUID, int vaultId)`
  - `void deleteAllVaults(UUID playerUUID)`
  - `java.util.Set<Integer> getVaultNumbers(UUID playerUUID)`
  - `boolean vaultExists(UUID playerUUID, int vaultId)`
  - `void cleanup(long olderThanTimestamp)`
  - `void initialize()`
  - `void shutdown()`
- [x] Create a `FileStorageProvider` class that implements `StorageProvider`.
  - This class will encapsulate the existing flat-file logic from `VaultManager`.
- [x] Create a `MySQLStorageProvider` class that implements `StorageProvider`.
  - This class will handle all JDBC connections and queries to the MySQL database.
  - It should use a connection pool (e.g., HikariCP) for efficiency.

## 3. Refactor `VaultManager`

- [x] Modify `VaultManager` to hold an instance of `StorageProvider`.
- [x] In the `PlayerVaults` main class, instantiate the correct `StorageProvider` (`FileStorageProvider` or `MySQLStorageProvider`) based on the `storage.storage-type` config setting.
- [x] Refactor all data-handling methods in `VaultManager` (e.g., `saveVault`, `loadOwnVault`, `deleteVault`) to delegate calls to the `StorageProvider` instance.
  - `VaultManager` will still handle inventory creation and business logic, but not the raw data storage.
  - `CardboardBoxSerialization` will still be used to serialize/deserialize inventories before passing them to/from the storage provider.

## 4. MySQL Implementation Details

- [x] **Database Schema:**
  - Design the MySQL table schema. A single table should be sufficient.
    ```sql
    CREATE TABLE IF NOT EXISTS `player_vaults` (
      `player_uuid` VARCHAR(36) NOT NULL,
      `vault_id` INT NOT NULL,
      `inventory_data` LONGTEXT NOT NULL,
      `last_modified` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (`player_uuid`, `vault_id`)
    );
    ```
- [x] **JDBC and Connection Pooling:**
  - Add the MySQL JDBC driver dependency to the `pom.xml`. It should be shaded into the final plugin JAR.
  - Add HikariCP dependency to `pom.xml` and shade it as well.
  - The `MySQLStorageProvider` will manage the HikariCP `DataSource`.
- [x] **SQL Queries:**
  - Implement the `StorageProvider` methods using `PreparedStatement`s to prevent SQL injection.
    - `INSERT ... ON DUPLICATE KEY UPDATE ...` for `saveVault`.
    - `SELECT inventory_data FROM player_vaults WHERE ...` for `loadVault`.
    - `DELETE FROM player_vaults WHERE ...` for `deleteVault` and `deleteAllVaults`.
    - `SELECT vault_id FROM player_vaults WHERE ...` for `getVaultNumbers`.
    - `DELETE FROM player_vaults WHERE last_modified < ?` for `cleanup`.

## 5. Data Migration

- [x] Create a new command `/pv convert storage <mysql|file>`.
- [x] Implement the conversion logic.
  - **File to MySQL:** Iterate through all `.yml` files in the `newvaults` directory, read the data, and insert it into the MySQL database.
  - **MySQL to File:** (Optional but good to have) Query all data from the database and write it to the flat-file structure.
- [x] The conversion process should lock the vaults (`VaultOperations.setLocked(true)`) to prevent data corruption.

## 6. Cleanup Task

- [x] Modify `tasks/Cleanup.java` to use the `StorageProvider` interface.
- [x] The `cleanup` method in the `StorageProvider` will handle the implementation-specific logic for deleting old data.
