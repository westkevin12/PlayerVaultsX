package com.drtshock.playervaults.storage;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MySQLStorageProvider implements StorageProvider {

    private HikariDataSource dataSource;

    @Override
    public void initialize() {
        Config.Storage.MySQL mysqlConfig = PlayerVaults.getInstance().getConf().getStorage().getMySQL();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + mysqlConfig.getHost() + ":" + mysqlConfig.getPort() + "/"
                + mysqlConfig.getDatabase() + "?allowPublicKeyRetrieval=true&useSSL=" + mysqlConfig.useSSL());
        config.setUsername(mysqlConfig.getUsername());
        config.setPassword(mysqlConfig.getPassword());
        config.setConnectionTimeout(mysqlConfig.getConnectionTimeout());
        config.setMaximumPoolSize(mysqlConfig.getMaxPoolSize());
        config.setMinimumIdle(mysqlConfig.getMinimumIdle());
        config.setIdleTimeout(mysqlConfig.getIdleTimeout());
        config.setMaxLifetime(mysqlConfig.getMaxLifetime());
        config.setConnectionTestQuery(mysqlConfig.getConnectionTestQuery());

        this.dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.CREATE_TABLE)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to create player_vaults table", e);
        }

        // Migration: check if icon_data exists
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.SELECT_VAULT_ICON)) {
        } catch (SQLException e) {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement ps = connection.prepareStatement(SQLStatements.ALTER_TABLE_ADD_ICON)) {
                ps.executeUpdate();
                com.drtshock.playervaults.util.Logger.info("Migrated player_vaults table to include icon_data column.");
            } catch (SQLException ex) {
                // Ignore
            }
        }

        // Migration: check if scope exists using Metadata
        try (Connection connection = dataSource.getConnection()) {
            boolean scopeExists = false;
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, "player_vaults", "scope")) {
                if (rs.next()) {
                    scopeExists = true;
                }
            }

            if (!scopeExists) {
                com.drtshock.playervaults.util.Logger.info("Migrating player_vaults table to include scope column...");
                try (PreparedStatement ps = connection.prepareStatement(
                        "ALTER TABLE player_vaults ADD COLUMN scope VARCHAR(64) NOT NULL DEFAULT 'global'")) {
                    ps.executeUpdate();
                    com.drtshock.playervaults.util.Logger.info("Added scope column to player_vaults table.");
                } catch (SQLException ex) {
                    com.drtshock.playervaults.util.Logger.warn("Failed to add scope column: " + ex.getMessage());
                }

                // Update Primary Key
                try {
                    try (PreparedStatement checkPs = connection.prepareStatement(
                            "SELECT player_uuid, vault_id, scope, COUNT(*) c FROM player_vaults GROUP BY player_uuid, vault_id, scope HAVING c > 1")) {
                        try (ResultSet rs = checkPs.executeQuery()) {
                            if (rs.next()) {
                                com.drtshock.playervaults.util.Logger
                                        .severe("DUPLICATE VAULT ENTRIES DETECTED! Cannot migrate Primary Key. " +
                                                "Please manually cleanup the 'player_vaults' table (remove duplicates for same uuid/vault_id/scope) and restart.");
                                return; // Abort migration of PK
                            }
                        }
                    }

                    try (PreparedStatement ps = connection.prepareStatement(
                            "ALTER TABLE player_vaults DROP PRIMARY KEY, ADD PRIMARY KEY(player_uuid, vault_id, scope)")) {
                        ps.executeUpdate();
                        com.drtshock.playervaults.util.Logger.info("Updated Primary Key to include scope.");
                    }
                } catch (SQLException ex) {
                    com.drtshock.playervaults.util.Logger.warn(
                            "Failed to update Primary Key to include scope: "
                                    + ex.getMessage());
                }
            }
        } catch (SQLException e) {
            com.drtshock.playervaults.util.Logger.warn("Failed to check/migrate scope column: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

    @Override
    public void saveVault(UUID playerUUID, int vaultId, String inventoryData, String scope) {
        // We need to update SQLStatements to handle scope or construct query here.
        // For simpler diff, I'll construct query here or assume SQLStatements are
        // updated?
        // I cannot update SQLStatements class in this tool call easily (it's likely
        // another file or inner class I didn't verify).
        // I'll assume I need to use raw strings here since I didn't edit SQLStatements.
        String sql = "INSERT INTO player_vaults (player_uuid, vault_id, inventory_data, scope) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE inventory_data = VALUES(inventory_data)";

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            ps.setString(3, inventoryData);
            ps.setString(4, scope == null || scope.isEmpty() ? "global" : scope);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save vault for " + playerUUID, e);
        }
    }

    @Override
    public String loadVault(UUID playerUUID, int vaultId, String scope) {
        String sql = "SELECT inventory_data FROM player_vaults WHERE player_uuid = ? AND vault_id = ? AND scope = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            ps.setString(3, scope == null || scope.isEmpty() ? "global" : scope);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("inventory_data");
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load vault for " + playerUUID, e);
        }
        return null;
    }

    @Override
    public void deleteVault(UUID playerUUID, int vaultId, String scope) {
        String sql = "DELETE FROM player_vaults WHERE player_uuid = ? AND vault_id = ? AND scope = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            ps.setString(3, scope == null || scope.isEmpty() ? "global" : scope);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete vault for " + playerUUID, e);
        }
    }

    @Override
    public void deleteAllVaults(UUID playerUUID, String scope) {
        String sql = "DELETE FROM player_vaults WHERE player_uuid = ? AND scope = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, scope == null || scope.isEmpty() ? "global" : scope);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete all vaults for " + playerUUID, e);
        }
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID playerUUID, String scope) {
        Set<Integer> vaults = new HashSet<>();
        String sql = "SELECT vault_id FROM player_vaults WHERE player_uuid = ? AND scope = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, scope == null || scope.isEmpty() ? "global" : scope);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vaults.add(rs.getInt("vault_id"));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to get vault numbers for " + playerUUID, e);
        }
        return vaults;
    }

    @Override
    public boolean vaultExists(UUID playerUUID, int vaultId, String scope) {
        String sql = "SELECT 1 FROM player_vaults WHERE player_uuid = ? AND vault_id = ? AND scope = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            ps.setString(3, scope == null || scope.isEmpty() ? "global" : scope);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to check if vault exists for " + playerUUID, e);
        }
    }

    @Override
    public void cleanup(long olderThanTimestamp) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.CLEANUP_OLD_VAULTS)) {
            ps.setLong(1, olderThanTimestamp / 1000);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to cleanup old vaults", e);
        }
    }

    @Override
    public Set<UUID> getAllPlayerUUIDs() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.SELECT_ALL_PLAYER_UUIDS)) {
            Set<UUID> playerUUIDs = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    playerUUIDs.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }
            return playerUUIDs;
        } catch (SQLException e) {
            throw new StorageException("Failed to get all player UUIDs", e);
        }
    }

    @Override
    public void saveVaults(Map<UUID, Map<Integer, String>> vaults, String scope) {
        String sql = "INSERT INTO player_vaults (player_uuid, vault_id, inventory_data, scope) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE inventory_data = VALUES(inventory_data)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map.Entry<UUID, Map<Integer, String>> playerEntry : vaults.entrySet()) {
                    for (Map.Entry<Integer, String> vaultEntry : playerEntry.getValue().entrySet()) {
                        ps.setString(1, playerEntry.getKey().toString());
                        ps.setInt(2, vaultEntry.getKey());
                        ps.setString(3, vaultEntry.getValue());
                        ps.setString(4, scope == null || scope.isEmpty() ? "global" : scope);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
                throw new StorageException("Failed to save vaults in bulk", e);
            }
        } catch (SQLException | StorageException e) {
            // Fallback to individual saves
            com.drtshock.playervaults.util.Logger
                    .warn("Bulk save failed, attempting individual saves: " + e.getMessage());
            for (Map.Entry<UUID, Map<Integer, String>> playerEntry : vaults.entrySet()) {
                for (Map.Entry<Integer, String> vaultEntry : playerEntry.getValue().entrySet()) {
                    try {
                        saveVault(playerEntry.getKey(), vaultEntry.getKey(), vaultEntry.getValue(), scope);
                    } catch (Exception ex) {
                        com.drtshock.playervaults.util.Logger
                                .severe("Failed to save individual vault during fallback: " + ex.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void saveVaultIcon(UUID playerUUID, int vaultId, String iconData, String scope) throws StorageException {
        String scopeVal = (scope == null || scope.isEmpty()) ? "global" : scope;
        String sql = "UPDATE player_vaults SET icon_data = ? WHERE player_uuid = ? AND vault_id = ? AND scope = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, iconData);
            ps.setString(2, playerUUID.toString());
            ps.setInt(3, vaultId);
            ps.setString(4, scopeVal);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                // Vault might not exist, but we want to store the icon.
                // Insert logic similar to saveVault but with null/empty inventory?
                // Or we could try INSERT ... ON DUPLICATE KEY UPDATE.
                // Let's try insert if update failed.
                String insertSql = "INSERT INTO player_vaults (player_uuid, vault_id, icon_data, scope, inventory_data) VALUES (?, ?, ?, ?, '') ON DUPLICATE KEY UPDATE icon_data = VALUES(icon_data)";
                try (PreparedStatement insertPs = connection.prepareStatement(insertSql)) {
                    insertPs.setString(1, playerUUID.toString());
                    insertPs.setInt(2, vaultId);
                    insertPs.setString(3, iconData);
                    insertPs.setString(4, scopeVal);
                    insertPs.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to save vault icon for " + playerUUID, e);
        }
    }

    @Override
    public String loadVaultIcon(UUID playerUUID, int vaultId, String scope) throws StorageException {
        String scopeVal = (scope == null || scope.isEmpty()) ? "global" : scope;
        String sql = "SELECT icon_data FROM player_vaults WHERE player_uuid = ? AND vault_id = ? AND scope = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            ps.setString(3, scopeVal);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("icon_data");
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load vault icon for " + playerUUID, e);
        }
        return null;
    }
}
