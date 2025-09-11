package com.drtshock.playervaults.vaultmanagement;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.Conversion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Level;

public class VaultDataMigrator {

    public static ItemStack[] migrateFromUnversionedNBT(String data) {
        try {
            return NBTSerialization.fromStorage(data);
        } catch (Exception e) {
            PlayerVaults.getInstance().getLogger().log(Level.WARNING, "Failed to migrate from unversioned NBT: " + e.getMessage() + " Raw data: " + data);
            return null;
        }
    }

    public static ItemStack[] migrateFromBukkitObjectStream(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            ItemStack[] contents = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < contents.length; i++) {
                contents[i] = (ItemStack) dataInput.readObject();
            }
            return contents;
        } catch (IOException | ClassNotFoundException e) {
            PlayerVaults.getInstance().getLogger().log(Level.WARNING, "Failed to migrate from BukkitObjectStream: " + e.getMessage());
            return null;
        } catch (Exception e) {
            PlayerVaults.getInstance().getLogger().log(Level.WARNING, "Failed to migrate from BukkitObjectStream (general error): " + e.getMessage());
            return null;
        }
    }

    public static ItemStack[] migrateFromOldestSerialization(String data) {
        try {
            return Conversion.migrateFromOldest(data);
        } catch (Exception e) {
            PlayerVaults.getInstance().getLogger().log(Level.WARNING, "Failed to migrate from OldestSerialization: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    public static ItemStack[] migrateFromCardboardBox(String data, String target) {
        try {
            return CardboardBoxSerialization.fromStorage(data, target);
        } catch (Exception e) {
            PlayerVaults.getInstance().getLogger().log(Level.WARNING, "Failed to migrate from CardboardBox: " + e.getMessage());
            return null;
        }
    }

    // Helper to get a dummy YamlConfiguration for OldestSerialization if needed
    // This is a workaround if OldestSerialization.getItems requires a ConfigurationSection
    public static YamlConfiguration getDummyYamlConfiguration(String key, String value) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(key, value);
        return yaml;
    }
}
