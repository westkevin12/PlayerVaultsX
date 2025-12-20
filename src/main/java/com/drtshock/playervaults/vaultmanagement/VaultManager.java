/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.drtshock.playervaults.vaultmanagement;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.drtshock.playervaults.storage.StorageProvider;
import com.drtshock.playervaults.storage.StorageException;
import com.drtshock.playervaults.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import com.drtshock.playervaults.util.TransactionLogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.List;

public class VaultManager {

    private static VaultManager instance;
    private final StorageProvider storage;
    private final Map<String, ItemStack[]> snapshots = new ConcurrentHashMap<>();
    private final PlayerVaults plugin;

    public VaultManager(PlayerVaults plugin, StorageProvider storage) {
        this.plugin = plugin;
        this.storage = storage;
        instance = this;
    }

    /**
     * Get the instance of this class.
     *
     * @return - instance of this class.
     */
    public static VaultManager getInstance() {
        return instance;
    }

    public StorageProvider getStorage() {
        return storage;
    }

    /**
     * Resolves the scope for a given world based on configuration.
     * 
     * @param worldName The name of the world.
     * @return The resolved scope (group name) or "global".
     */
    public String resolveScope(String worldName) {
        Config.Storage.WorldScoping scoping = plugin.getConf().getStorage().getScoping();
        if (!scoping.isEnabled()) {
            return "global";
        }

        for (Map.Entry<String, List<String>> entry : scoping.getGroups().entrySet()) {
            if (entry.getValue().contains(worldName)) {
                return entry.getKey();
            }
        }

        return scoping.getDefaultGroup();
    }

    /**
     * Simplifies getting scope from player.
     * 
     * @param p Player
     * @return scope
     */
    public String resolveScope(Player p) {
        if (p == null)
            return "global";
        return resolveScope(p.getWorld().getName());
    }

    /**
     * Saves the inventory to the specified player and vault number.
     * Uses the scope from the inventory holder if available, otherwise attempts to
     * resolve.
     *
     * @param inventory The inventory to be saved.
     * @param target    The player of whose file to save to.
     * @param number    The vault number.
     */
    public void saveVault(Inventory inventory, String target, int number) {
        String scope = "global";
        if (inventory.getHolder() instanceof VaultHolder) {
            scope = ((VaultHolder) inventory.getHolder()).getScope();
        } else {
            // Fallback: Try to resolve from online player if available
            Player p = Bukkit.getPlayer(target);
            if (p != null) {
                scope = resolveScope(p);
            }
        }

        String key = target + " " + number + " " + scope;
        ItemStack[] oldContents = snapshots.remove(key);
        // Fallback for migration/legacy keys without scope if needed?
        // But snapshots are runtime only, so restart clears them.

        if (oldContents != null) {
            TransactionLogger.logTransaction(UUID.fromString(target), number, oldContents, inventory.getContents());
        }

        UUID uuid = UUID.fromString(target);
        String serialized = CardboardBoxSerialization.toStorage(inventory, target);
        try {
            storage.saveVault(uuid, number, serialized, scope);
        } catch (StorageException e) {
            Logger.severe("Error saving vault for player " + target + " vault " + number + " scope " + scope + ": "
                    + e.getMessage());
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.getTL().storageSaveError().title().send(player);
            }
        } finally {
            storage.unlock(uuid, number, scope);
        }
    }

    /**
     * Discards snapshot and unlocks.
     * Needs scope to unlock correctly.
     */
    public void discardSnapshot(String target, int number, String scope) {
        snapshots.remove(target + " " + number + " " + scope);
        try {
            storage.unlock(UUID.fromString(target), number, scope);
        } catch (Exception e) {
            // Ignore UUID parse error
        }
    }

    /**
     * Load the player's vault and return it.
     * Resolves scope automatically from player's current world.
     *
     * @param player The holder of the vault.
     * @param number The vault number.
     */
    public Inventory loadOwnVault(Player player, int number, int size) {
        if (size % 9 != 0) {
            size = PlayerVaults.getInstance().getDefaultVaultSize();
        }

        String scope = resolveScope(player);
        Logger.debug("Loading self vault for " + player.getName() + " (" + player.getUniqueId() + ") scope: " + scope);

        String title = PlayerVaults.getInstance().getVaultTitle(String.valueOf(number));
        VaultViewInfo info = new VaultViewInfo(player.getUniqueId().toString(), number, scope);
        if (PlayerVaults.getInstance().getOpenInventories().containsKey(info.toString())) {
            Logger.debug("Already open");
            return PlayerVaults.getInstance().getOpenInventories().get(info.toString());
        }

        VaultHolder vaultHolder = new VaultHolder(number, scope);

        // Attempt to acquire lock
        if (!storage.attemptLock(player.getUniqueId(), number, scope)) {
            // Lock failed
            com.drtshock.playervaults.util.ComponentDispatcher.send(player,
                    net.kyori.adventure.text.Component.text("Vault is currently locked by another session.")
                            .color(net.kyori.adventure.text.format.NamedTextColor.RED));
            return null;
        }

        String data;
        try {
            data = storage.loadVault(player.getUniqueId(), number, scope);
        } catch (StorageException e) {
            Logger.severe("Error loading own vault for player " + player.getName() + " vault " + number + " scope "
                    + scope + ": "
                    + e.getMessage());
            plugin.getTL().storageLoadError().title().send(player);
            storage.unlock(player.getUniqueId(), number, scope); // Release lock if load fails
            return null;
        }
        if (data == null) {
            Logger.debug("No vault matching number");
            Inventory inv = Bukkit.createInventory(vaultHolder, size, title);
            vaultHolder.setInventory(inv);
            snapshots.put(info.toString(), inv.getContents());
            return inv;
        } else {
            Inventory inv = getInventory(vaultHolder, player.getUniqueId().toString(), data, size, title);
            if (inv == null) {
                storage.unlock(player.getUniqueId(), number, scope); // Release lock if deserialization fails
                return null;
            }
            snapshots.put(info.toString(), inv.getContents());
            return inv;
        }
    }

    /**
     * Load the player's vault and return it.
     *
     * @param name   The holder of the vault.
     * @param number The vault number.
     * @param scope  The scope to load from.
     */
    public Inventory loadOtherVault(String name, int number, int size, String scope) throws StorageException {
        if (size % 9 != 0) {
            size = PlayerVaults.getInstance().getDefaultVaultSize();
        }

        if (scope == null || scope.isEmpty())
            scope = "global";

        Logger.debug("Loading other vault for " + name + " scope " + scope);

        UUID holder = null;
        OfflinePlayer offlinePlayer = Bukkit.getPlayer(name);
        if (offlinePlayer == null) {
            try {
                holder = UUID.fromString(name);
                offlinePlayer = Bukkit.getOfflinePlayer(holder);
            } catch (IllegalArgumentException e) {
                // Not a valid UUID
            }
        }

        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
            return null;
        }
        holder = offlinePlayer.getUniqueId();

        String title = PlayerVaults.getInstance().getVaultTitle(String.valueOf(number));
        VaultViewInfo info = new VaultViewInfo(name, number, true, scope); // Read-only usually? Or admin edit? Using
                                                                           // readOnly=true in constructor just sets
                                                                           // flag

        // Wait, typical loadOtherVault (admin) is editable.
        // Rechecking old code: `new VaultViewInfo(name, number)` -> `readOnly=false`.
        // So I should pass `false` unless intended to be read only. Existing code
        // allowed editing.
        info = new VaultViewInfo(name, number, false, scope);

        Inventory inv;
        VaultHolder vaultHolder = new VaultHolder(number, scope);
        if (PlayerVaults.getInstance().getOpenInventories().containsKey(info.toString())) {
            Logger.debug("Already open");
            inv = PlayerVaults.getInstance().getOpenInventories().get(info.toString());
        } else {
            String data;
            try {
                data = storage.loadVault(holder, number, scope);
            } catch (StorageException e) {
                Logger.severe(
                        "Error loading other vault for player " + name + " vault " + number + ": " + e.getMessage());
                throw e; // Re-throw the exception for the caller to handle
            }
            Inventory i = getInventory(vaultHolder, holder.toString(), data, size, title);
            if (i == null) {
                return null;
            } else {
                inv = i;
            }
            snapshots.put(info.toString(), inv.getContents());
            PlayerVaults.getInstance().getOpenInventories().put(info.toString(), inv);
        }
        return inv;
    }

    // Overloaded for backward compatibility / existing calls that default to global
    public Inventory loadOtherVault(String name, int number, int size) throws StorageException {
        return loadOtherVault(name, number, size, "global");
    }

    /**
     * Get an inventory from file. Returns null if the inventory doesn't exist.
     * SHOULD ONLY BE USED INTERNALLY
     *
     * @param data the base64 encoded inventory data.
     * @param size the size of the vault.
     * @return inventory if exists, otherwise null.
     */
    private Inventory getInventory(InventoryHolder owner, String ownerName, String data, int size, String title) {
        Inventory inventory = Bukkit.createInventory(owner, size, title);

        ItemStack[] deserialized = CardboardBoxSerialization.fromStorage(data, ownerName);
        if (deserialized == null) {
            Logger.debug("Loaded vault for " + ownerName + " as null");
            return inventory;
        }

        // Check if deserialized has more used slots than the limit here.
        // Happens on change of permission or if people used the broken version.
        // In this case, players will lose items.
        if (deserialized.length > size) {
            Logger.debug("Loaded vault for " + ownerName + " and got " + deserialized.length
                    + " items for allowed size of " + size + ". Attempting to rescue!");
            for (ItemStack stack : deserialized) {
                if (stack != null) {
                    inventory.addItem(stack);
                }
            }
        } else {
            inventory.setContents(deserialized);
        }

        Logger.debug("Loaded vault");
        return inventory;
    }

    /**
     * Gets an inventory without storing references to it. Used for dropping a
     * players inventories on death.
     *
     * @param holder The holder of the vault.
     * @param number The vault number.
     * @return The inventory of the specified holder and vault number. Can be null.
     */
    public Inventory getVault(String holder, int number) {
        UUID uuid = UUID.fromString(holder);
        // Resolve scope from player if online
        Player p = Bukkit.getPlayer(uuid);
        String scope = resolveScope(p);

        String serialized;
        try {
            serialized = storage.loadVault(uuid, number, scope);
        } catch (StorageException e) {
            Logger.severe("Could not get vault " + number + " for player " + holder
                    + " for inventory drop. This may result in data loss. " + e.getMessage());
            return null;
        }
        ItemStack[] contents = CardboardBoxSerialization.fromStorage(serialized, holder);
        // Inventory holder is null because it's transient
        Inventory inventory = Bukkit.createInventory(null, contents.length, holder + " vault " + number);
        inventory.setContents(contents);
        return inventory;
    }

    /**
     * Checks if a vault exists.
     *
     * @param holder holder of the vault.
     * @param number vault number.
     * @param scope  vault scope.
     * @return true if the vault file and vault number exist in that file, otherwise
     *         false.
     */
    public boolean vaultExists(String holder, int number, String scope) throws StorageException {
        try {
            if (scope == null)
                scope = "global";
            return storage.vaultExists(UUID.fromString(holder), number, scope);
        } catch (StorageException e) {
            Logger.severe(
                    "Error checking if vault exists for player " + holder + " vault " + number + ": " + e.getMessage());
            throw e; // Re-throw the exception for the caller to handle
        }
    }

    public boolean vaultExists(String holder, int number) throws StorageException {
        return vaultExists(holder, number, "global");
    }

    /**
     * Gets the numbers belonging to all their vaults.
     *
     * @param holder holder
     * @param scope  scope
     * @return a set of Integers, which are player's vaults' numbers.
     */
    public Set<Integer> getVaultNumbers(String holder, String scope) throws StorageException {
        try {
            if (scope == null)
                scope = "global";
            return storage.getVaultNumbers(UUID.fromString(holder), scope);
        } catch (StorageException e) {
            Logger.severe("Error getting vault numbers for player " + holder + ": " + e.getMessage());
            throw e; // Re-throw the exception for the caller to handle
        }
    }

    public Set<Integer> getVaultNumbers(String holder) throws StorageException {
        return getVaultNumbers(holder, "global");
    }

    /**
     * Deletes all vaults.
     */
    public void deleteAllVaults(String holder, String scope) throws StorageException {
        try {
            if (scope == null)
                scope = "global";
            storage.deleteAllVaults(UUID.fromString(holder), scope);
        } catch (StorageException e) {
            Logger.severe("Error deleting all vaults for player " + holder + ": " + e.getMessage());
            throw e; // Re-throw the exception for the caller to handle
        }
    }

    public void deleteAllVaults(String holder) throws StorageException {
        deleteAllVaults(holder, "global");
    }

    /**
     * Deletes a players vault.
     *
     * @param holder The vault holder.
     * @param number The vault number.
     * @param scope  The scope.
     */
    public void deleteVault(final String holder, final int number, String scope) throws StorageException {
        try {
            if (scope == null)
                scope = "global";
            storage.deleteVault(UUID.fromString(holder), number, scope);
        } catch (StorageException e) {
            Logger.severe("Error deleting vault for player " + holder + " vault " + number + ": " + e.getMessage());
            throw e; // Re-throw the exception for the caller to handle
        }
    }

    public void deleteVault(final String holder, final int number) throws StorageException {
        deleteVault(holder, number, "global");
    }

    public void setVaultIcon(String holder, int number, ItemStack icon) {
        // Resolve scope: try to find player
        try {
            UUID uuid = UUID.fromString(holder);
            Player p = Bukkit.getPlayer(uuid);
            String scope = resolveScope(p);
            setVaultIcon(holder, number, icon, scope);
        } catch (IllegalArgumentException e) {
            // If UUID parse fails or player not found, default global?
            // Existing code didn't handle UUID parse fail well in signature string arg
            // but usually holder IS uuid string.
            // We'll fallback to global if simple setVaultIcon is called without context.
            setVaultIcon(holder, number, icon, "global");
        }
    }

    public void setVaultIcon(String holder, int number, ItemStack icon, String scope) {
        String serialized = CardboardBoxSerialization.serializeItem(icon);
        try {
            storage.saveVaultIcon(UUID.fromString(holder), number, serialized, scope);
        } catch (StorageException e) {
            Logger.severe("Error saving vault icon for player " + holder + " vault " + number + " scope " + scope + ": "
                    + e.getMessage());
        }
    }

    public ItemStack getVaultIcon(String holder, int number) {
        // Resolve scope
        try {
            UUID uuid = UUID.fromString(holder);
            Player p = Bukkit.getPlayer(uuid);
            String scope = resolveScope(p);
            return getVaultIcon(holder, number, scope);
        } catch (IllegalArgumentException e) {
            return getVaultIcon(holder, number, "global");
        }
    }

    public ItemStack getVaultIcon(String holder, int number, String scope) {
        try {
            String data = storage.loadVaultIcon(UUID.fromString(holder), number, scope);
            return CardboardBoxSerialization.deserializeItem(data);
        } catch (StorageException e) {
            Logger.warn("Error loading vault icon for player " + holder + " vault " + number + " scope " + scope + ": "
                    + e.getMessage());
            return null;
        }
    }
}
