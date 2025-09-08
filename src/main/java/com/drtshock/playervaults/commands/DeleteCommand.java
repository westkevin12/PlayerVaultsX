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
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


public class DeleteCommand implements CommandExecutor {
    private final PlayerVaults plugin;

    public DeleteCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (VaultOperations.isLocked()) {
            this.plugin.getTL().locked().title().send(sender);
            return true;
        }
        switch (args.length) {
            case 1:
                if (sender instanceof Player) {
                    VaultOperations.deleteOwnVault((Player) sender, args[0]);
                } else {
                    this.plugin.getTL().playerOnly().title().send(sender);
                }
                break;
            case 2:
                if (!sender.hasPermission(Permission.DELETE)) {
                    PlayerVaults.getInstance().getTL().noPerms().title().send(sender);
                    break;
                }
                OfflinePlayer searchPlayer = VaultOperations.getTargetPlayer(args[0]);

                if (searchPlayer == null || !searchPlayer.hasPlayedBefore()) {
                    this.plugin.getTL().noOwnerFound().title().with("player", args[0]).send(sender);
                    return true;
                }

                String target = searchPlayer.getUniqueId().toString();
                String targetName = searchPlayer.getName();

                if (args[1].equalsIgnoreCase("all")) {
                    if (sender.hasPermission(Permission.DELETE_ALL)) {
                        VaultManager.getInstance().deleteAllVaults(target);
                        this.plugin.getTL().deleteOtherVaultAll().title().with("player", targetName).send(sender);
                        PlayerVaults.getInstance().getLogger().info(String.format("%s deleted ALL vaults belonging to %s", sender.getName(), targetName));
                    } else {
                        this.plugin.getTL().noPerms().title().send(sender);
                    }
                } else {
                    VaultOperations.deleteOtherVault(sender, target, args[1]);
                }
                break;
            default:
                this.plugin.getTL().deleteHelp().send(sender);
        }
        return true;
    }
}
