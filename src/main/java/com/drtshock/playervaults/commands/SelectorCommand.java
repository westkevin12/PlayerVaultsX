package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.vaultmanagement.VaultSelector;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SelectorCommand implements CommandExecutor {
    private final PlayerVaults plugin;

    public SelectorCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            this.plugin.getTL().playerOnly().title().send(sender);
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission(Permission.COMMANDS_SELECTOR)) {
            this.plugin.getTL().noPerms().title().send(sender);
            return true;
        }

        VaultSelector.openSelector(player, 1);
        return true;
    }
}
