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
import com.drtshock.playervaults.converters.*;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.vaultmanagement.VaultOperations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConvertCommand implements CommandExecutor {

    private final List<Converter> converters = new ArrayList<>();
    private final PlayerVaults plugin;

    public ConvertCommand(PlayerVaults plugin) {
        converters.add(new BackpackConverter());
        converters.add(new Cosmic2Converter());
        converters.add(new Cosmic3Converter());
        converters.add(new EnderVaultsConverter());
        converters.add(new AxVaultsConverter());
        converters.add(new AxVaults2Converter());
        converters.add(new UniVaultsConverter());
        converters.add(new XVaultsConverter());
        converters.add(new FairyVaultsConverter());
        converters.add(new MaltsConverter());
        converters.add(new VanillaEnderChestConverter());
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Permission.CONVERT)) {
            this.plugin.getTL().noPerms().title().send(sender);
        } else {
            if (args.length == 0) {
                sender.sendMessage("/" + label + " <all | plugin name | storage>");
            } else {
                String name = args[0];
                final List<Converter> applicableConverters = new ArrayList<>();
                if (name.equalsIgnoreCase("all")) {
                    applicableConverters.addAll(converters);
                } else if (name.equalsIgnoreCase("storage")) {
                    if (args.length > 1) {
                        applicableConverters.add(new StorageConverter(plugin, args[1]));
                    } else {
                        sender.sendMessage("/" + label + " storage <mysql|file>");
                        return true;
                    }
                } else {
                    for (Converter converter : converters) {
                        if (converter.getName().equalsIgnoreCase(name)) {
                            applicableConverters.add(converter);
                        }
                    }
                }
                if (applicableConverters.size() <= 0) {
                    this.plugin.getTL().convertPluginNotFound().title().send(sender);
                } else {
                    // Fork into background
                    this.plugin.getTL().convertBackground().title().send(sender);
                    PlayerVaults.getInstance().getServer().getScheduler()
                            .runTaskLaterAsynchronously(PlayerVaults.getInstance(), () -> {
                                int convertedVaults = 0;
                                int affectedPlayers = 0;
                                long startTime = System.currentTimeMillis();
                                VaultOperations.setLocked(true);
                                for (Converter converter : applicableConverters) {
                                    if (converter.canConvert()) {
                                        Object result = converter.run(sender);
                                        if (result instanceof Integer) {
                                            convertedVaults += (Integer) result;
                                        } else if (result instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Integer> map = (Map<String, Integer>) result;
                                            convertedVaults += map.getOrDefault("convertedVaults", 0);
                                            affectedPlayers += map.getOrDefault("affectedPlayers", 0);
                                        }
                                    }
                                }
                                VaultOperations.setLocked(false);
                                long duration = System.currentTimeMillis() - startTime;
                                if (affectedPlayers > 0) {
                                    sender.sendMessage(String.format("Converted %d vaults for %d players in %dms.",
                                            convertedVaults, affectedPlayers, duration));
                                } else {
                                    this.plugin.getTL().convertComplete().title().with("count", convertedVaults + "")
                                            .send(sender);
                                }
                            }, 5);
                }
            }
        }
        return true;
    }
}
