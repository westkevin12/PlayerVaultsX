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
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class MySQLStorageProvider implements StorageProvider {

    private HikariDataSource dataSource;

    @Override
    public void initialize() {
        Config.Storage.MySQL mysqlConfig = PlayerVaults.getInstance().getConf().getStorage().getMySQL();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + mysqlConfig.getHost() + ":" + mysqlConfig.getPort() + "/" + mysqlConfig.getDatabase() + "?allowPublicKeyRetrieval=true&useSSL=" + mysqlConfig.useSSL());
        config.setUsername(mysqlConfig.getUsername());
        config.setPassword(mysqlConfig.getPassword());
        config.setConnectionTimeout(mysqlConfig.getConnectionTimeout());
        config.setMaximumPoolSize(mysqlConfig.getMaxPoolSize());

        this.dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS player_vaults ( "
                     + "player_uuid VARCHAR(36) NOT NULL, "
                     + "vault_id INT NOT NULL, "
                     + "inventory_data LONGTEXT NOT NULL, "
                     + "last_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                     + "PRIMARY KEY (player_uuid, vault_id)" + ");")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to create player_vaults table", e);
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
             PreparedStatement ps = connection.prepareStatement("INSERT INTO player_vaults (player_uuid, vault_id, inventory_data) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE inventory_data = ?")) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            ps.setString(3, inventoryData);
            ps.setString(4, inventoryData);
            ps.executeUpdate();
        } catch (SQLException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to save vault for " + playerUUID, e);
        }
    }

    @Override
    public String loadVault(UUID playerUUID, int vaultId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT inventory_data FROM player_vaults WHERE player_uuid = ? AND vault_id = ?")) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("inventory_data");
                }
            }
        } catch (SQLException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to load vault for " + playerUUID, e);
        }
        return null;
    }

    @Override
    public void deleteVault(UUID playerUUID, int vaultId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM player_vaults WHERE player_uuid = ? AND vault_id = ?")) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            ps.executeUpdate();
        } catch (SQLException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to delete vault for " + playerUUID, e);
        }
    }

    @Override
    public void deleteAllVaults(UUID playerUUID) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM player_vaults WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to delete all vaults for " + playerUUID, e);
        }
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID playerUUID) {
        Set<Integer> vaults = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT vault_id FROM player_vaults WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vaults.add(rs.getInt("vault_id"));
                }
            }
        } catch (SQLException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to get vault numbers for " + playerUUID, e);
        }
        return vaults;
    }

    @Override
    public boolean vaultExists(UUID playerUUID, int vaultId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM player_vaults WHERE player_uuid = ? AND vault_id = ?")) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, vaultId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to check if vault exists for " + playerUUID, e);
        }
        return false;
    }

    @Override
    public void cleanup(long olderThanTimestamp) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM player_vaults WHERE last_modified < FROM_UNIXTIME(?)")) {
            ps.setLong(1, olderThanTimestamp / 1000);
            ps.executeUpdate();
        } catch (SQLException e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to cleanup old vaults", e);
        }
    }
}
