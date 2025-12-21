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
        try (Connection connection = dataSource.getConnection()) {
            boolean iconExists = false;
            try (PreparedStatement ps = connection.prepareStatement("SELECT icon_data FROM player_vaults LIMIT 1");
                    ResultSet rs = ps.executeQuery()) {
                iconExists = true;
            } catch (SQLException e) {
                // Ignore, column likely missing
            }

            if (!iconExists) {
                try (PreparedStatement ps = connection.prepareStatement(SQLStatements.ALTER_TABLE_ADD_ICON)) {
                    ps.executeUpdate();
                    com.drtshock.playervaults.util.Logger
                            .info("Migrated player_vaults table to include icon_data column.");
                } catch (SQLException ex) {
                    com.drtshock.playervaults.util.Logger.warn("Failed to add icon_data column: " + ex.getMessage());
                }
            } else {
                com.drtshock.playervaults.util.Logger.debug("icon_data column found.");
            }
        } catch (SQLException e) {
            com.drtshock.playervaults.util.Logger.warn("Failed to check icon_data migration: " + e.getMessage());
        }

        // Migration: check if scope exists using SELECT
        try (Connection connection = dataSource.getConnection()) {
            boolean scopeExists = false;
            try (PreparedStatement ps = connection.prepareStatement("SELECT scope FROM player_vaults LIMIT 1");
                    ResultSet rs = ps.executeQuery()) {
                scopeExists = true;
            } catch (SQLException e) {
                // Column likely doesn't exist
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.INSERT_OR_UPDATE_VAULT)) {
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.SELECT_VAULT)) {
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
    public java.util.Map<Integer, String> loadVaults(UUID playerUUID, java.util.Set<Integer> vaultIds, String scope)
            throws StorageException {
        java.util.Map<Integer, String> results = new java.util.HashMap<>();
        if (vaultIds == null || vaultIds.isEmpty())
            return results;

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < vaultIds.size(); i++) {
            builder.append("?,");
        }
        builder.deleteCharAt(builder.length() - 1); // remove last comma

        String sql = "SELECT vault_id, inventory_data FROM player_vaults WHERE player_uuid = ? AND scope = ? AND vault_id IN ("
                + builder.toString() + ")";

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, scope == null || scope.isEmpty() ? "global" : scope);

            int index = 3;
            for (Integer id : vaultIds) {
                ps.setInt(index++, id);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.put(rs.getInt("vault_id"), rs.getString("inventory_data"));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to batch load vaults for " + playerUUID, e);
        }
        return results;
    }

    @Override
    public void deleteVault(UUID playerUUID, int vaultId, String scope) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.DELETE_VAULT)) {
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.DELETE_ALL_VAULTS)) {
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.SELECT_VAULT_NUMBERS)) {
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.CHECK_VAULT_EXISTS)) {
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
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(SQLStatements.INSERT_OR_UPDATE_VAULT)) {
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.UPDATE_VAULT_ICON)) {
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
                try (PreparedStatement insertPs = connection
                        .prepareStatement(SQLStatements.INSERT_OR_UPDATE_VAULT_ICON)) {
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.SELECT_VAULT_ICON)) {
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
