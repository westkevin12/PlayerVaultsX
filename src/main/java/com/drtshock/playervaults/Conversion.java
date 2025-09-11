/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler
 *
 * This program is free software: you can redistribute and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General License for more details.
 *
 * You should have received a copy of the GNU General License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.drtshock.playervaults;


import com.drtshock.playervaults.storage.StorageProvider;
import com.drtshock.playervaults.vaultmanagement.NBTSerialization;
import com.drtshock.playervaults.vaultmanagement.VaultDataMigrator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Conversion {
    private static final Gson gson = new Gson();
    /**
     * Fancy JSON serialization mostly by evilmidget38.
     *
     * @author evilmidget38, gomeow
     */
    static class OldestSerialization {
        private static Map<String, Object> toMap(JSONObject object) {
            Map<String, Object> map = new HashMap<>();

            // Weird case of bad meta causing null map to be passed here.
            if (object == null) {
                return map;
            }

            for (Object key : object.keySet()) {
                map.put(key.toString(), fromJson(object.get(key)));
            }
            return map;
        }

        private static Object fromJson(Object json) {
            if (json == null) {
                return null;
            } else if (json instanceof JSONObject) {
                return toMap((JSONObject) json);
            } else if (json instanceof JSONArray) {
                return toList((JSONArray) json);
            } else {
                return json;
            }
        }

        private static List<Object> toList(JSONArray array) {
            List<Object> list = new ArrayList<>();
            for (Object value : array) {
                list.add(fromJson(value));
            }
            return list;
        }

        private static ItemStack[] getItems(List<String> stringItems) {
            List<ItemStack> contents = new ArrayList<>();
            for (String piece : stringItems) {
                if (piece.equalsIgnoreCase("null")) {
                    contents.add(null);
                }
                else {
                    ItemStack item = (ItemStack) deserialize(toMap((JSONObject) JSONValue.parse(piece)));
                    contents.add(item);
                }
            }
            ItemStack[] items = new ItemStack[contents.size()];
            for (int x = 0; x < contents.size(); x++) {
                items[x] = contents.get(x);
            }
            return items;
        }

        @SuppressWarnings("unchecked")
        private static Object deserialize(Map<String, Object> map) {
            for (Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    entry.setValue(deserialize((Map<String, Object>) entry.getValue()));
                }
                else if (entry.getValue() instanceof Iterable) {
                    entry.setValue(convertIterable((Iterable<?>) entry.getValue()));
                }
                else if (entry.getValue() instanceof Number) {
                    entry.setValue(convertNumber((Number) entry.getValue()));
                }
            }
            return map.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY) ? ConfigurationSerialization.deserializeObject(map) : map;
        }

        @SuppressWarnings("unchecked")
        private static List<?> convertIterable(Iterable<?> iterable) {
            List<Object> newList = new ArrayList<>();
            for (Object object : iterable) {
                if (object instanceof Map) {
                    object = deserialize((Map<String, Object>) object);
                }
                else if (object instanceof List) {
                    object = convertIterable((Iterable<?>) object);
                }
                else if (object instanceof Number) {
                    object = convertNumber((Number) object);
                }
                newList.add(object);
            }
            return newList;
        }

        private static Number convertNumber(Number number) {
            if (number instanceof Long) {
                Long longObj = (Long) number;
                if (longObj == longObj.intValue()) {
                    return longObj.intValue();
                }
            }
            return number;
        }
    }

    static void convert(PlayerVaults plugin) {
        Logger logger = plugin.getLogger();

        File newDir = plugin.getVaultData();
        File oldVaults = plugin.getDataFolder().toPath().resolve("base64vaults").toFile();
        File reallyOldVaults = plugin.getDataFolder().toPath().resolve("uuidvaults").toFile();

        if (newDir.exists()) {
            plugin.getDataFolder().toPath().resolve("oldVaultsData").toFile().mkdirs();
            if (oldVaults.exists()) {
                try {
                    Files.move(oldVaults.toPath(), plugin.getDataFolder().toPath().resolve("oldVaultsData").resolve("base64vaults"));
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to move old vaults dir: " + e.getMessage());
                }
            }
            if (reallyOldVaults.exists()) {
                try {
                    Files.move(reallyOldVaults.toPath(), plugin.getDataFolder().toPath().resolve("oldVaultsData").resolve("uuidvaults"));
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to move old vaults dir: " + e.getMessage());
                }
            }
            return;
        }

        newDir.mkdirs();

        File oldDir;
        boolean recent;
        if (oldVaults.exists() && oldVaults.isDirectory()) {
            logger.info("********** Starting data storage conversion ********** ");
            logger.info("This might take a while and might say \"unable to resolve\"");
            logger.info(oldVaults.toString() + " will remain as a backup.");
            recent = true;
            oldDir = oldVaults;
        } else if (reallyOldVaults.exists() && reallyOldVaults.isDirectory()) {
            logger.info("********** Starting data storage conversion ********** ");
            logger.info("This might take a while and might say \"unable to resolve\"");
            logger.info(reallyOldVaults.toString() + " will remain as a backup.");
            recent = false;
            oldDir = reallyOldVaults;
        } else {
            logger.info("No old vaults found to convert to new format. :)");
            return;
        }

        int players = 0;
        int vaults = 0;
        int failed = 0;
        for (File file : oldDir.listFiles()) {
            if (file.isDirectory() || !file.getName().endsWith(".yml")) {
                continue; // backups folder.
            }

            FileConfiguration uuidFile = YamlConfiguration.loadConfiguration(file);
            String stringUUID = file.getName().replace(".yml", "");

            for (String key : uuidFile.getKeys(false)) {
                if (!key.startsWith("vault")) {
                    continue;
                }

                int vaultNumber = Integer.parseInt(key.replace("vault", ""));

                try {

                    ItemStack[] contents;
                    if (recent) {
                        String data = uuidFile.getString(key);
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
                        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                        contents = new ItemStack[dataInput.readInt()];
                        // Read the serialized inventory
                        for (int i = 0; i < contents.length; i++) {
                            contents[i] = (ItemStack) dataInput.readObject();
                        }
                        dataInput.close();
                    } else {
                        ConfigurationSection section = uuidFile.getConfigurationSection(key);
                        List<String> data = new ArrayList<>();
                        for (String s : section.getKeys(false)) {
                            String value = section.getString(s);
                            data.add(value);
                        }
                        contents = OldestSerialization.getItems(data);
                    }
                    String newData = NBTSerialization.toStorage(Bukkit.createInventory(null, contents.length, "Conversion").getContents());

                    JsonObject vaultData = new JsonObject();
                    vaultData.addProperty("version", PlayerVaults.CURRENT_DATA_VERSION);
                    vaultData.addProperty("data", newData);

                    uuidFile.set(key, gson.toJson(vaultData));
                    vaults++;
                } catch (ClassNotFoundException | java.io.InvalidClassException e) {
                    logger.severe("Failed to parse vault " + vaultNumber + " for " + stringUUID + ": Incompatible data format. " + e.getMessage());
                    failed++;
                } catch (Exception e) {
                    logger.severe("Failed to parse vault " + vaultNumber + " for " + stringUUID + ": " + e.getMessage());
                    failed++;
                }
            }
            try {
                uuidFile.save(newDir.toPath().resolve(file.getName()).toFile());
            } catch (IOException e) {
                logger.severe("Failed to save new file " + file.getName());
            }

            players++;
        }

        logger.info(String.format("Converted %d vaults for %d players to new storage. %d failed to convert", vaults, players, failed));
    }

    public static ItemStack[] migrateFromOldest(String data) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(data);
            List<String> list = new ArrayList<>();
            for (String key : config.getKeys(false)) {
                list.add(config.getString(key));
            }
            return OldestSerialization.getItems(list);
        } catch (Exception e) {
            return null;
        }
    }

    public static void migrateVaults(PlayerVaults plugin, StorageProvider source, StorageProvider destination) {
        Logger logger = plugin.getLogger();
        logger.info("********** Starting data storage migration ********** ");

        Set<UUID> playerUUIDs = source.getAllStoredUUIDs();
        int playersMigrated = 0;
        int vaultsMigrated = 0;
        int failedVaults = 0;

        for (UUID playerUUID : playerUUIDs) {
            Map<Integer, String> vaults = source.getAllVaults(playerUUID);
            for (Map.Entry<Integer, String> entry : vaults.entrySet()) {
                String rawData = entry.getValue();
                ItemStack[] contents = null;

                // Attempt to migrate from any known old format
                // This logic mirrors the on-the-fly migration in VaultManager
                try {
                     // Try CardboardBox/BukkitObjectStream first, as it's the most recent old format
                    contents = VaultDataMigrator.migrateFromBukkitObjectStream(rawData);
                    if (contents == null) {
                        // Fallback to the oldest JSON-based format
                        contents = VaultDataMigrator.migrateFromOldestSerialization(rawData);
                    }
                     if (contents == null) {
                        // Fallback for unversioned NBT data, just in case
                        contents = VaultDataMigrator.migrateFromUnversionedNBT(rawData);
                    }

                    if (contents != null) {
                        String newStorageData = NBTSerialization.toStorage(contents);

                        // Wrap in versioned JSON object for future-proofing
                        JsonObject vaultData = new JsonObject();
                        vaultData.addProperty("version", PlayerVaults.CURRENT_DATA_VERSION);
                        vaultData.addProperty("data", newStorageData);

                        destination.saveVault(playerUUID, entry.getKey(), gson.toJson(vaultData));
                        vaultsMigrated++;
                    } else {
                        logger.warning("Failed to migrate vault " + entry.getKey() + " for player " + playerUUID + ": Could not decode old data format.");
                        failedVaults++;
                    }
                } catch (Exception e) {
                     logger.log(Level.SEVERE, "Error migrating vault " + entry.getKey() + " for player " + playerUUID, e);
                     failedVaults++;
                }
            }
            if(!vaults.isEmpty()){
                playersMigrated++;
            }
        }
        logger.info(String.format("Migration complete. Migrated %d vaults for %d players. Failed to migrate %d vaults.", vaultsMigrated, playersMigrated, failedVaults));
    }
}