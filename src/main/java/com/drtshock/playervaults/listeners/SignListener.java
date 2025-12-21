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

package com.drtshock.playervaults.listeners;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Logger;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.vaultmanagement.VaultOperations;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public class SignListener implements Listener {
    private final PlayerVaults plugin;

    public SignListener(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!PlayerVaults.getInstance().getConf().isSigns()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSleeping() || player.isDead() || !player.isOnline()) {
            return;
        }

        Block block = event.getClickedBlock();
        Action action = event.getAction();

        // Handle opening other inventories while in a vault (always check this if
        // right-clicking a block)
        if (action == Action.RIGHT_CLICK_BLOCK
                && PlayerVaults.getInstance().getInVault().containsKey(player.getUniqueId().toString())) {
            if (block != null && isInvalidBlock(block)) { // Added null check for block
                event.setCancelled(true);
            }
        }

        // If not right-clicking a block, or no block was clicked, then we don't care
        // about sign logic.
        if (block == null || action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Now we know it's a right-click on a block, and signs are enabled.
        // Check if player is setting a sign
        if (PlayerVaults.getInstance().getSetSign().containsKey(player.getName())) {
            int i = PlayerVaults.getInstance().getSetSign().get(player.getName()).getChest();
            boolean self = PlayerVaults.getInstance().getSetSign().get(player.getName()).isSelf();
            UUID owner = self ? null : PlayerVaults.getInstance().getSetSign().get(player.getName()).getOwner();
            PlayerVaults.getInstance().getSetSign().remove(player.getName());
            event.setCancelled(true);
            if (plugin.isSign(block.getType())) { // Check if it's actually a sign
                Sign s = (Sign) block.getState();
                Location l = s.getLocation();
                String world = l.getWorld().getName();
                int x = l.getBlockX();
                int y = l.getBlockY();
                int z = l.getBlockZ();
                if (self) {
                    plugin.getSigns().set(world + ";;" + x + ";;" + y + ";;" + z + ".self", true);
                } else {
                    if (owner != null) {
                        plugin.getSigns().set(world + ";;" + x + ";;" + y + ";;" + z + ".owner", owner.toString());
                    }
                }
                plugin.getSigns().set(world + ";;" + x + ";;" + y + ";;" + z + ".chest", i);
                plugin.saveSigns();
                this.plugin.getTL().setSign().title().send(player);
            } else {
                this.plugin.getTL().notASign().title().send(player);
            }
            return;
        }

        // If not setting a sign, check if it's an existing vault sign
        if (plugin.isSign(block.getType())) { // Check if it's actually a sign
            Location l = block.getLocation();
            String world = l.getWorld().getName();
            int x = l.getBlockX();
            int y = l.getBlockY();
            int z = l.getBlockZ();
            if (plugin.getSigns().getKeys(false).isEmpty()) { // Added check for empty signs config
                return; // No signs configured, so no need to check further
            }
            if (plugin.getSigns().getKeys(false).contains(world + ";;" + x + ";;" + y + ";;" + z)) {
                Logger.debug("Player " + player.getName() + " clicked sign at world(" + x + "," + y + "," + z + ")");
                if (PlayerVaults.getInstance().getInVault().containsKey(player.getUniqueId().toString())) {
                    // don't let them open another vault.
                    Logger.debug("Player " + player.getName() + " denied sign vault because already in a vault!");
                    return;
                }
                int num = PlayerVaults.getInstance().getSigns()
                        .getInt(world + ";;" + x + ";;" + y + ";;" + z + ".chest", 1);
                String numS = String.valueOf(num);
                if (player.hasPermission(Permission.SIGNS_USE) || player.hasPermission(Permission.SIGNS_BYPASS)) {
                    boolean self = PlayerVaults.getInstance().getSigns()
                            .getBoolean(world + ";;" + x + ";;" + y + ";;" + z + ".self", false);
                    String ownerIdentifier = self ? player.getUniqueId().toString()
                            : PlayerVaults.getInstance().getSigns()
                                    .getString(world + ";;" + x + ";;" + y + ";;" + z + ".owner");
                    Logger.debug("Player " + player.getName() + " wants to open a "
                            + (self ? "self" : "non-self (" + ownerIdentifier + ")") + " sign vault");
                    OfflinePlayer offlinePlayer = VaultOperations.getTargetPlayer(ownerIdentifier);
                    if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
                        Logger.debug("Denied sign vault for never-seen-before owner " + ownerIdentifier);
                        this.plugin.getTL().vaultDoesNotExist().title().send(player);
                        return;
                    }
                    if (self) {
                        // We already checked that they can use signs, now lets check if they have this
                        // many vaults.
                        // Sign vaults are forced to be free/skip check?
                        // openOwnVaultSign handles this.
                        VaultOperations.openOwnVaultSign(player, numS);
                        // We cannot check if it succeeded here easily.
                        // We return assuming it worked or async will fail silently/with message to
                        // player.
                        // We might need to block or assume success for event cancellation?
                        // If we don't cancel, they might edit the sign?
                        // We should probably cancel anyway if we think it's a valid sign.

                    } else {
                        VaultOperations.openOtherVault(player, ownerIdentifier, numS, false);
                    }
                    // We assume success for the purpose of the sign interaction (blocking standard
                    // interact)
                    Logger.debug("Player " + player.getName() + " triggered sign vault op");
                    event.setCancelled(true);
                    this.plugin.getTL().openWithSign().title().with("vault", String.valueOf(num))
                            .with("player", offlinePlayer.getName()).send(player);
                } else {
                    Logger.debug("Player " + player.getName() + " no sign perms!");
                    this.plugin.getTL().noPerms().title().send(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!PlayerVaults.getInstance().getConf().isSigns()) {
            return;
        }
        if (plugin.isSign(event.getBlock().getType())) {
            blockChangeCheck(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!PlayerVaults.getInstance().getConf().isSigns()) {
            return;
        }
        if (plugin.isSign(event.getBlock().getType())) {
            blockChangeCheck(event.getBlock().getLocation());
        }
    }

    /**
     * Check if the location given is a sign, and if so, remove it from the
     * signs.yml file
     *
     * @param location The location to check
     */
    public void blockChangeCheck(Location location) {
        if (plugin.getSigns().getKeys(false).isEmpty()) {
            return; // Save us a check.
        }

        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (plugin.getSigns().getKeys(false).contains(world + ";;" + x + ";;" + y + ";;" + z)) {
            plugin.getSigns().set(world + ";;" + x + ";;" + y + ";;" + z, null);
            plugin.saveSigns();
        }
    }

    private boolean isInvalidBlock(Block block) {
        String type = block.getType().name();
        return block.getState() instanceof InventoryHolder || type.contains("ENCHANT") || type.equals("ENDER_CHEST");
    }
}
