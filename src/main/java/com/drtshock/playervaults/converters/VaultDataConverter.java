package com.drtshock.playervaults.converters;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.storage.StorageProvider;
import com.drtshock.playervaults.vaultmanagement.CardboardBoxSerialization;
import com.drtshock.playervaults.vaultmanagement.NBTSerialization;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converter for migrating old CardboardBox serialized vault data to NBTAPI serialization.
 * This class uses deprecated CardboardBoxSerialization methods for reading old data,
 * and should be removed after a suitable grace period once all data is migrated.
 */
@SuppressWarnings("deprecation")
public class VaultDataConverter implements Converter {

    private static final Gson gson = new Gson();

    @Override
    public int run(CommandSender initiator) {
        int convertedVaults = 0;
        PlayerVaults plugin = PlayerVaults.getInstance();
        StorageProvider storage = plugin.getStorageProvider();

        // Get all player UUIDs that have vaults
        // This assumes that each player has a file or directory in the storage location
        // For FileStorageProvider, this would be the player data files.
        // For MySQLStorageProvider, this would involve querying the database.
        // This part needs to be adapted based on the actual StorageProvider implementation.
        // For now, let's assume FileStorageProvider and iterate through player data files.

        if (storage.getClass().getSimpleName().equals("FileStorageProvider")) {
            File dataFolder = new File(plugin.getDataFolder(), "newvaults"); // Use newvaults as the target directory
            if (!dataFolder.exists()) {
                return 0;
            }

            try (Stream<Path> paths = Files.walk(dataFolder.toPath())) {
                for (Path path : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith(".yml")) {
                        String uuidString = fileName.substring(0, fileName.length() - 4); // Remove .yml
                        try {
                            UUID playerUUID = UUID.fromString(uuidString);
                            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
                            String target = offlinePlayer.getUniqueId().toString();

                            Set<Integer> vaultNumbers = storage.getVaultNumbers(playerUUID);
                            for (Integer vaultNumber : vaultNumbers) {
                                String oldData = storage.loadVault(playerUUID, vaultNumber);
                                if (oldData != null) {
                                    ItemStack[] itemStacks = CardboardBoxSerialization.fromStorage(oldData, target);
                                    if (itemStacks != null) {
                                        // Create a temporary inventory with the exact size of the deserialized item stacks.
                                        // This ensures that the original vault dimensions are preserved.
                                        Inventory tempInventory = Bukkit.createInventory(null, itemStacks.length);
                                        tempInventory.setContents(itemStacks);
                                        String newData = NBTSerialization.toStorage(tempInventory.getContents());

                                        JsonObject vaultData = new JsonObject();
                                        vaultData.addProperty("version", PlayerVaults.CURRENT_DATA_VERSION);
                                        vaultData.addProperty("data", newData);

                                        storage.saveVault(playerUUID, vaultNumber, gson.toJson(vaultData));
                                        convertedVaults++;
                                    }
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            // Not a valid UUID, ignore
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Handle other storage providers (e.g., MySQL) if necessary
            // This would involve querying the database for all vault data.
            plugin.getLogger().warning("VaultDataConverter only supports FileStorageProvider for now.");
        }

        return convertedVaults;
    }

    @Override
    public boolean canConvert() {
        // This converter is always applicable as it converts internal data.
        // However, it should only run if there are actual old vaults to convert.
        // For now, we'll keep it true, but in a real scenario, we might want to check for old data existence.
        return true;
    }

    @Override
    public String getName() {
        return "VaultData";
    }
}
