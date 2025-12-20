package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.vaultmanagement.VaultSearcher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SearchCommand implements CommandExecutor {
    private final PlayerVaults plugin;

    public SearchCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            this.plugin.getTL().playerOnly().title().send(sender);
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission(Permission.COMMANDS_SEARCH)) {
            this.plugin.getTL().noPerms().title().send(sender);
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /pvsearch <item>");
            return true;
        }

        String query = String.join(" ", args);
        VaultSearcher.search(player, query);
        return true;
    }
}
