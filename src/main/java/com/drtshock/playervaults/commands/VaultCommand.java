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

package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import com.drtshock.playervaults.vaultmanagement.VaultOperations;
import com.drtshock.playervaults.vaultmanagement.VaultViewInfo;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class VaultCommand implements CommandExecutor {
    private final PlayerVaults plugin;

    public VaultCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (VaultOperations.isLocked()) {
            this.plugin.getTL().locked().title().send(sender);
            return true;
        }

        if (sender instanceof Player player) {
            if (PlayerVaults.getInstance().getInVault().containsKey(player.getUniqueId().toString())) {
                // don't let them open another vault.
                return true;
            }

            switch (args.length) {
                case 1:
                    if (VaultOperations.openOwnVault(player, args[0], true)) {
                        PlayerVaults.getInstance().getInVault().put(player.getUniqueId().toString(), new VaultViewInfo(player.getUniqueId().toString(), Integer.parseInt(args[0])));
                    }
                    break;
                case 2:
                    if (!player.hasPermission(Permission.ADMIN)) {
                        this.plugin.getTL().noPerms().title().send(sender);
                        break;
                    }

                    if ("list".equals(args[1])) {
                        String target = getTarget(args[0]);
                        if (target == null) {
                            this.plugin.getTL().noOwnerFound().title().with("player", args[0]).send(sender);
                            break;
                        }
                        Set<Integer> vaults = VaultManager.getInstance().getVaultNumbers(target);
                        if (vaults.isEmpty()) {
                            this.plugin.getTL().vaultDoesNotExist().title().send(sender);
                        } else {
                            StringBuilder sb = new StringBuilder();
                            for (Integer vaultNum : vaults) {
                                sb.append(vaultNum).append(" ");
                            }

                            this.plugin.getTL().existingVaults().title().with("player", args[0]).with("vault", sb.toString().trim()).send(sender);
                        }
                        break;
                    }

                    int number;
                    try {
                        number = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        this.plugin.getTL().mustBeNumber().title().send(sender);
                        return true;
                    }

                    String target = getTarget(args[0]);
                    if (target == null) {
                        this.plugin.getTL().noOwnerFound().title().with("player", args[0]).send(sender);
                        break;
                    }

                    if (VaultOperations.openOtherVault(player, target, args[1])) {
                        PlayerVaults.getInstance().getInVault().put(player.getUniqueId().toString(), new VaultViewInfo(target, number));
                    } else {
                        this.plugin.getTL().noOwnerFound().title().with("player", args[0]).send(sender);
                    }
                    break;
                default:
                    this.plugin.getTL().help().title().send(sender);
            }
        } else {
            this.plugin.getTL().playerOnly().title().send(sender);
        }

        return true;
    }

    private String getTarget(String name) {
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId().toString();
        }
        try {
            UUID uuid = UUID.fromString(name);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            if (offlinePlayer.hasPlayedBefore()) {
                return uuid.toString();
            }
        } catch (IllegalArgumentException e) {
            // Not a UUID
        }
        return null;
    }
}
