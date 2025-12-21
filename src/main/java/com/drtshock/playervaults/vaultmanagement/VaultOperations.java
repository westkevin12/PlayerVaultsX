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
import com.drtshock.playervaults.util.Logger;
import com.drtshock.playervaults.util.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import com.drtshock.playervaults.storage.StorageException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class VaultOperations {

    private static final AtomicBoolean LOCKED = new AtomicBoolean(false);

    /**
     * Gets whether or not player vaults are locked
     *
     * @return true if locked, false otherwise
     */
    public static boolean isLocked() {
        return LOCKED.get();
    }

    /**
     * Sets whether or not player vaults are locked. If set to true, this will kick
     * anyone who is currently using their
     * vaults out.
     *
     * @param locked true for locked, false otherwise
     */
    public static void setLocked(boolean locked) {
        LOCKED.set(locked);

        if (locked) {
            for (Player player : PlayerVaults.getInstance().getServer().getOnlinePlayers()) {
                if (player == null)
                    continue;
                if (player.getOpenInventory() != null) {
                    InventoryView view = player.getOpenInventory();
                    if (view.getTopInventory() != null && view.getTopInventory().getHolder() instanceof VaultHolder) {
                        player.closeInventory();
                        PlayerVaults.getInstance().getTL().locked().title().send(player);
                    }
                }
            }
        }
    }

    /**
     * Gets an offline player by name or UUID.
     *
     * @param name The name or UUID of the player to get.
     * @return The OfflinePlayer object, or null if not found.
     */
    public static OfflinePlayer getTargetPlayer(String name) {
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }
        try {
            UUID uuid = UUID.fromString(name);
            return Bukkit.getOfflinePlayer(uuid);
        } catch (IllegalArgumentException e) {
            // It's not a UUID, so let's try it as a name (the deprecated way)
            @SuppressWarnings("deprecation")
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
            return offlinePlayer;
        }
    }

    /**
     * Check whether or not the player has permission to open the requested vault.
     *
     * @param sender The person to check.
     * @param number The vault number.
     * @return Whether or not they have permission.
     */
    public static boolean checkPerms(CommandSender sender, int number) {
        for (int x = number; x <= PlayerVaults.getInstance().getMaxVaultAmountPermTest(); x++) {
            if (sender.hasPermission(Permission.amount(x))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the max size vault a player is allowed to have.
     *
     * @param name that is having his permissions checked.
     * @return max size as integer. If no max size is set then it will default to
     *         the configured default.
     */
    public static int getMaxVaultSize(String name) {
        try {
            UUID uuid = UUID.fromString(name);
            return getMaxVaultSize(Bukkit.getOfflinePlayer(uuid));
        } catch (Exception e) {
            // Not a UUID
        }

        return PlayerVaults.getInstance().getDefaultVaultSize();
    }

    /**
     * Get the max size vault a player is allowed to have.
     *
     * @param player that is having his permissions checked.
     * @return max size as integer. If no max size is set then it will default to
     *         the configured default.
     */
    public static int getMaxVaultSize(OfflinePlayer player) {
        if (player == null || !player.isOnline()) {
            return 6 * 9;
        }
        for (int i = 6; i != 0; i--) {
            if (player.getPlayer().hasPermission(Permission.size(i))) {
                return i * 9;
            }
        }
        return PlayerVaults.getInstance().getDefaultVaultSize();
    }

    /**
     * Open a player's own vault.
     *
     * @param player The player to open to.
     * @param arg    The vault number to open.
     */
    public static void openOwnVault(Player player, String arg) {
        openOwnVaultE(player, arg, false, true);
    }

    public static void openOwnVaultSign(Player player, String arg) {
        openOwnVaultE(player, arg, true, false);
    }

    private static void openOwnVaultE(Player player, String arg, boolean free, boolean send) {
        if (isLocked()) {
            return;
        }
        if (player.isSleeping() || player.isDead() || !player.isOnline()) {
            return;
        }
        int number;
        try {
            number = Integer.parseInt(arg);
            if (number < 1) {
                return;
            }
        } catch (NumberFormatException nfe) {
            PlayerVaults.getInstance().getTL().mustBeNumber().title().send(player);
            return;
        }

        if (checkPerms(player, number)) {
            // Async load
            int finalNumber = number;
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    // Load vault and check existence in one go
                    return VaultManager.getInstance().loadOwnVaultWithStatus(player, finalNumber,
                            getMaxVaultSize(player));
                } catch (Exception e) {
                    return null;
                }
            }).thenAccept(loadedVault -> {
                Bukkit.getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                    if (!player.isOnline())
                        return;

                    if (loadedVault == null || loadedVault.inventory() == null) {
                        Logger.debug(String.format("Failed to open null vault %d for %s. This is weird.", finalNumber,
                                player.getName()));
                        return;
                    }

                    Inventory inv = loadedVault.inventory();

                    // Economy check - deferred to here.
                    // We use the new payToOpen overload which accepts existence status
                    if (free || EconomyOperations.payToOpen(player, finalNumber, loadedVault.existed())) {
                        player.openInventory(inv);

                        // Check if the inventory was actually opened
                        if (player.getOpenInventory().getTopInventory() instanceof CraftingInventory
                                || player.getOpenInventory().getTopInventory() == null) {
                            Logger.debug(String.format("Cancelled opening vault %s for %s from an outside source.", arg,
                                    player.getName()));
                            return; // inventory open event was cancelled.
                        }

                        VaultViewInfo info = new VaultViewInfo(player.getUniqueId().toString(), finalNumber);
                        PlayerVaults.getInstance().getOpenInventories().put(info.toString(), inv);
                        // Also update InVault map which VaultCommand used to do
                        PlayerVaults.getInstance().getInVault().put(player.getUniqueId().toString(), info);

                        if (send) {
                            PlayerVaults.getInstance().getTL().openVault().title().with("vault", arg).send(player);
                        }
                    } else {
                        PlayerVaults.getInstance().getTL().insufficientFunds().title().send(player);
                    }
                });
            });
        } else {
            PlayerVaults.getInstance().getTL().noPerms().title().send(player);
        }
    }

    /**
     * Open a player's own vault. If player is using a command, they'll need the
     * required permission.
     *
     * @param player    The player to open to.
     * @param arg       The vault number to open.
     * @param isCommand - if player is opening via a command or not.
     */
    public static void openOwnVault(Player player, String arg, boolean isCommand) {
        if (!isCommand || player.hasPermission(Permission.COMMANDS_USE)) {
            openOwnVault(player, arg);
            return;
        }
        PlayerVaults.getInstance().getTL().noPerms().title().send(player);
    }

    /**
     * Open another player's vault.
     *
     * @param player     The player to open to.
     * @param vaultOwner The name of the vault owner.
     * @param arg        The vault number to open.
     */
    public static void openOtherVault(Player player, String vaultOwner, String arg) {
        openOtherVault(player, vaultOwner, arg, true, false);
    }

    public static void openOtherVault(Player player, String vaultOwner, String arg, boolean send) {
        openOtherVault(player, vaultOwner, arg, send, false);
    }

    public static void openOtherVault(Player player, String vaultOwner, String arg, boolean send, boolean readOnly) {
        if (isLocked()) {
            return;
        }

        if (player.isSleeping() || player.isDead() || !player.isOnline()) {
            return;
        }

        int number = 0;
        try {
            number = Integer.parseInt(arg);
            if (number < 1) {
                PlayerVaults.getInstance().getTL().mustBeNumber().title().send(player);
                return;
            }
        } catch (NumberFormatException nfe) {
            PlayerVaults.getInstance().getTL().mustBeNumber().title().send(player);
            return;
        }

        int finalNumber = number;
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return VaultManager.getInstance().loadOtherVault(vaultOwner, finalNumber, getMaxVaultSize(vaultOwner));
            } catch (StorageException e) {
                // We need to handle this in main thread log
                return null;
            }
        }).thenAccept(inv -> {
            Bukkit.getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                if (!player.isOnline())
                    return;

                if (inv == null) {
                    // Logic from original catch block
                    PlayerVaults.getInstance().getTL().storageLoadError().title().send(player);
                    // We don't have exception string unless we passed it.
                    // Just generic error or we could enhance the Future to return a Result object.
                    // For now, logging general error.
                    PlayerVaults.getInstance().getLogger().warning("Error loading other vault for " + vaultOwner);
                    return;
                }

                String name = vaultOwner;
                try {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(vaultOwner));
                    name = offlinePlayer.getName();
                } catch (Exception e) {
                    // not a player
                }

                player.openInventory(inv);

                // Check if the inventory was actually opened
                if (player.getOpenInventory().getTopInventory() instanceof CraftingInventory
                        || player.getOpenInventory().getTopInventory() == null) {
                    Logger.debug(String.format("Cancelled opening vault %s for %s from an outside source.", arg,
                            player.getName()));
                    return;
                }
                if (send) {
                    PlayerVaults.getInstance().getTL().openOtherVault().title().with("vault", arg).with("player", name)
                            .send(player);
                }

                // Need to set ViewInfo for a third party vault for the opening player.
                VaultViewInfo info = new VaultViewInfo(vaultOwner, finalNumber, readOnly);
                PlayerVaults.getInstance().getInVault().put(player.getUniqueId().toString(), info);
                PlayerVaults.getInstance().getOpenInventories().put(player.getUniqueId().toString(), inv);
            });
        });
    }

    /**
     * Delete a player's own vault.
     *
     * @param player The player to delete.
     * @param arg    The vault number to delete.
     */
    public static void deleteOwnVault(Player player, String arg) {
        if (isLocked()) {
            return;
        }
        if (isNumber(arg)) {
            int number;
            try {
                number = Integer.parseInt(arg);
                if (number == 0) {
                    PlayerVaults.getInstance().getTL().mustBeNumber().title().send(player);
                    return;
                }
            } catch (NumberFormatException nfe) {
                PlayerVaults.getInstance().getTL().mustBeNumber().title().send(player);
                return;
            }

            int finalNumber = number;
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return VaultManager.getInstance().vaultExists(player.getUniqueId().toString(), finalNumber);
                } catch (Exception e) {
                    return false;
                }
            }).thenAccept(exists -> {
                Bukkit.getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                    if (!player.isOnline())
                        return;
                    if (EconomyOperations.refundOnDelete(player, finalNumber, exists)) {
                        // Refund success (or not needed)
                        // Now delete.

                        // We checked existence async, then refunded sync.
                        // If it doesn't exist, refundOnDelete(..., false) handles messaging player.
                        if (!exists)
                            return;

                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                VaultManager.getInstance().deleteVault(player.getUniqueId().toString(), finalNumber);
                                Bukkit.getScheduler().runTask(PlayerVaults.getInstance(), () -> PlayerVaults
                                        .getInstance().getTL().deleteVault().title().with("vault", arg).send(player));
                            } catch (StorageException e) {
                                Bukkit.getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                                    PlayerVaults.getInstance().getTL().storageSaveError().title().send(player);
                                    PlayerVaults.getInstance().getLogger().severe(String.format(
                                            "Error deleting own vault %d for %s: %s", finalNumber, player.getName(),
                                            e.getMessage()));
                                });
                            }
                        });
                    }
                });
            });

        } else {
            PlayerVaults.getInstance().getTL().mustBeNumber().title().send(player);
        }
    }

    /**
     * Delete a player's vault.
     *
     * @param sender The sender executing the deletion.
     * @param holder The user to whom the deleted vault belongs.
     * @param arg    The vault number to delete.
     */
    public static void deleteOtherVault(CommandSender sender, String holder, String arg) {
        if (isLocked()) {
            return;
        }
        if (sender.hasPermission(Permission.DELETE)) {
            if (isNumber(arg)) {
                int number;
                try {
                    number = Integer.parseInt(arg);
                    if (number == 0) {
                        PlayerVaults.getInstance().getTL().mustBeNumber().title().send(sender);
                        return;
                    }
                } catch (NumberFormatException nfe) {
                    PlayerVaults.getInstance().getTL().mustBeNumber().title().send(sender);
                    return;
                }

                int finalNumber = number;
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        VaultManager.getInstance().deleteVault(holder, finalNumber);
                        Bukkit.getScheduler().runTask(PlayerVaults.getInstance(),
                                () -> PlayerVaults.getInstance().getTL().deleteOtherVault().title().with("vault", arg)
                                        .with("player", holder).send(sender));
                    } catch (StorageException e) {
                        Bukkit.getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                            PlayerVaults.getInstance().getTL().storageSaveError().title().send(sender);
                            PlayerVaults.getInstance().getLogger().severe(
                                    String.format("Error deleting vault %d for %s: %s", finalNumber, holder,
                                            e.getMessage()));
                        });
                    }
                });
            } else {
                PlayerVaults.getInstance().getTL().mustBeNumber().title().send(sender);
            }
        } else {
            PlayerVaults.getInstance().getTL().noPerms().title().send(sender);
        }
    }

    /**
     * Delete all of a player's vaults
     *
     * @param sender The sender executing the deletion.
     * @param holder The user to whom the deleted vault belongs.
     */
    public static void deleteOtherAllVaults(CommandSender sender, String holder) {
        if (isLocked() || holder == null) {
            return;
        }

        if (sender.hasPermission(Permission.DELETE_ALL)) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    VaultManager.getInstance().deleteAllVaults(holder);
                    Bukkit.getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                        Logger.info(String.format("%s deleted ALL vaults belonging to %s", sender.getName(), holder));
                        PlayerVaults.getInstance().getTL().deleteOtherVaultAll().title().with("player", holder)
                                .send(sender);
                    });
                } catch (StorageException e) {
                    Bukkit.getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                        PlayerVaults.getInstance().getTL().storageSaveError().title().send(sender);
                        PlayerVaults.getInstance().getLogger()
                                .severe(String.format("Error deleting all vaults for %s: %s", holder, e.getMessage()));
                    });
                }
            });
        } else {
            PlayerVaults.getInstance().getTL().noPerms().title().send(sender);
        }
    }

    private static boolean isNumber(String check) {
        try {
            Integer.parseInt(check);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private record PlayerCount(int count, Instant time) {
    }

    private static Map<UUID, PlayerCount> countCache = new ConcurrentHashMap<>();

    private static final int secondsToLive = 2;

    public static int countVaults(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerCount count = countCache.get(uuid);
        if (count != null && count.time().isAfter(Instant.now().plus(secondsToLive, ChronoUnit.SECONDS))) {
            return count.count;
        }
        int vaultCount = 0;
        for (int x = 1; x <= PlayerVaults.getInstance().getMaxVaultAmountPermTest(); x++) {
            if (player.hasPermission(Permission.amount(x))) {
                vaultCount = x;
            }
        }
        PlayerCount newCount = new PlayerCount(vaultCount, Instant.now());
        countCache.put(uuid, newCount);
        PlayerVaults.getInstance().getServer().getScheduler().runTaskLater(PlayerVaults.getInstance(), () -> {
            if (countCache.get(uuid) == newCount) {
                countCache.remove(uuid); // Do a lil cleanup to avoid the world's smallest memory leak
            }
        }, 20 * secondsToLive + 1);
        return vaultCount;
    }
}
