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

        // Migration: check if icon_data exists, if not, add it
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.SELECT_VAULT_ICON)) {
            // Just checking if column exists by preparing statement
            // If validation fails (column missing), it throws SQLException
        } catch (SQLException e) {
            // Column likely missing, attempt migration
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement ps = connection.prepareStatement(SQLStatements.ALTER_TABLE_ADD_ICON)) {
                ps.executeUpdate();
                com.drtshock.playervaults.util.Logger.info("Migrated player_vaults table to include icon_data column.");
            } catch (SQLException ex) {
                com.drtshock.playervaults.util.Logger
                        .warn("Failed to migrate player_vaults table (maybe already exists?): " + ex.getMessage());
            }
        }
    }

    @Override
    public void shutdown() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

    @Override
    public void saveVault(UUID playerUUID, int vaultId, String inventoryData) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.INSERT_OR_UPDATE_VAULT)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            ps.setString(3, inventoryData);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save vault for " + playerUUID, e);
        }
    }

    @Override
    public String loadVault(UUID playerUUID, int vaultId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.SELECT_VAULT)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
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
    public void deleteVault(UUID playerUUID, int vaultId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.DELETE_VAULT)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete vault for " + playerUUID, e);
        }
    }

    @Override
    public void deleteAllVaults(UUID playerUUID) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.DELETE_ALL_VAULTS)) {
            ps.setString(1, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete all vaults for " + playerUUID, e);
        }
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID playerUUID) {
        Set<Integer> vaults = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.SELECT_VAULT_NUMBERS)) {
            ps.setString(1, playerUUID.toString());
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
    public boolean vaultExists(UUID playerUUID, int vaultId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.CHECK_VAULT_EXISTS)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
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
    public void saveVaults(Map<UUID, Map<Integer, String>> vaults) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(SQLStatements.INSERT_OR_UPDATE_VAULT)) {
                for (Map.Entry<UUID, Map<Integer, String>> playerEntry : vaults.entrySet()) {
                    for (Map.Entry<Integer, String> vaultEntry : playerEntry.getValue().entrySet()) {
                        ps.setString(1, playerEntry.getKey().toString());
                        ps.setInt(2, vaultEntry.getKey());
                        ps.setString(3, vaultEntry.getValue());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex); // Add rollback exception to original exception
                }
                throw new StorageException("Failed to save vaults in bulk", e);
            }
        } catch (SQLException e) {
            // This catches exceptions from getConnection() and setAutoCommit()
            throw new StorageException("Failed to set up transaction for bulk save", e);
        }
    }

    @Override
    public void saveVaultIcon(UUID playerUUID, int vaultId, String iconData) throws StorageException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.UPDATE_VAULT_ICON)) {
            ps.setString(1, iconData);
            ps.setString(2, playerUUID.toString());
            ps.setInt(3, vaultId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save vault icon for " + playerUUID, e);
        }
    }

    @Override
    public String loadVaultIcon(UUID playerUUID, int vaultId) throws StorageException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SQLStatements.SELECT_VAULT_ICON)) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("icon_data");
                }
            }
        } catch (SQLException e) {
            // If column doesn't exist (shouldn't happen if init ran), or other error
            throw new StorageException("Failed to load vault icon for " + playerUUID, e);
        }
        return null;
    }
}
