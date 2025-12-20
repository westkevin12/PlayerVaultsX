package com.drtshock.playervaults.storage;

public class SQLStatements {
    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS player_vaults ( "
            + "player_uuid VARCHAR(36) NOT NULL, "
            + "vault_id INT NOT NULL, "
            + "inventory_data LONGTEXT NOT NULL, "
            + "icon_data LONGTEXT, "
            + "last_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
            + "PRIMARY KEY (player_uuid, vault_id))"
            + ";";

    public static final String ALTER_TABLE_ADD_ICON = "ALTER TABLE player_vaults ADD COLUMN icon_data LONGTEXT";

    public static final String INSERT_OR_UPDATE_VAULT = "INSERT INTO player_vaults (player_uuid, vault_id, inventory_data) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE inventory_data = VALUES(inventory_data)";
    public static final String UPDATE_VAULT_ICON = "UPDATE player_vaults SET icon_data = ? WHERE player_uuid = ? AND vault_id = ?";
    public static final String SELECT_VAULT_ICON = "SELECT icon_data FROM player_vaults WHERE player_uuid = ? AND vault_id = ?";

    public static final String SELECT_VAULT = "SELECT inventory_data FROM player_vaults WHERE player_uuid = ? AND vault_id = ?";
    public static final String DELETE_VAULT = "DELETE FROM player_vaults WHERE player_uuid = ? AND vault_id = ?";
    public static final String DELETE_ALL_VAULTS = "DELETE FROM player_vaults WHERE player_uuid = ?";
    public static final String SELECT_VAULT_NUMBERS = "SELECT vault_id FROM player_vaults WHERE player_uuid = ?";
    public static final String CHECK_VAULT_EXISTS = "SELECT 1 FROM player_vaults WHERE player_uuid = ? AND vault_id = ?";
    public static final String CLEANUP_OLD_VAULTS = "DELETE FROM player_vaults WHERE last_modified < FROM_UNIXTIME(?)";
    public static final String SELECT_ALL_PLAYER_UUIDS = "SELECT DISTINCT player_uuid FROM player_vaults";
}
