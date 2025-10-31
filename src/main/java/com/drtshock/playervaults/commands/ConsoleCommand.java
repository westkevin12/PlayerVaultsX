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
import com.drtshock.playervaults.vaultmanagement.VaultOperations;
import com.drtshock.playervaults.vaultmanagement.VaultViewInfo;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;


public class ConsoleCommand implements CommandExecutor {
    private final PlayerVaults plugin;

    public ConsoleCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            return true;
        }
        if (VaultOperations.isLocked()) {
            this.plugin.getTL().locked().title().send(sender);
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("/" + label + " openforplayer <player> <owner> <vaultid>");
            return true;
        } else {
            if (args[0].equals("openforplayer")) {
                if (args.length < 4) {
                    sender.sendMessage("/" + label + " openforplayer <player> <owner> <vaultid>");
                    return true;
                } else {
                    String player = args[1];
                    String ownerIdentifier = args[2];
                    String vaultId = args[3];
                    Player plr = this.plugin.getServer().getPlayerExact(player);
                    if (plr == null) {
                        sender.sendMessage("NOT ONLINE");
                        return true;
                    }

                    OfflinePlayer ownerPlayer = VaultOperations.getTargetPlayer(ownerIdentifier);

                    if (ownerPlayer == null || !ownerPlayer.hasPlayedBefore()) {
                        sender.sendMessage("Could not find player: " + ownerIdentifier);
                        return true;
                    }

                    String ownerUUID = ownerPlayer.getUniqueId().toString();

                    int number;
                    try {
                        number = Integer.parseInt(vaultId);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("NOT NUMBER");
                        return true;
                    }
                    if (VaultOperations.openOtherVault(plr, ownerUUID, vaultId)) {
                        PlayerVaults.getInstance().getInVault().put(plr.getUniqueId().toString(), new VaultViewInfo(ownerUUID, number));
                    } else {
                        sender.sendMessage("FAILED!?");
                    }
                }
            }
        }
        return true;
    }
}
