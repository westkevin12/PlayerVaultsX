package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class IconCommand implements CommandExecutor {
    private final PlayerVaults plugin;

    public IconCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            this.plugin.getTL().playerOnly().title().send(sender);
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission(Permission.COMMANDS_ICON)) {
            this.plugin.getTL().noPerms().title().send(sender);
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /pvicon <vault number>");
            return true;
        }

        int vaultNum;
        try {
            vaultNum = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            this.plugin.getTL().mustBeNumber().title().send(sender);
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            // Allow setting AIR (clearing icon) if intended, or just warn?
            // VaultCommand allowed it but did nothing special.
            // We will proceed.
        }

        PlayerVaults.getInstance().getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            VaultManager.getInstance().setVaultIcon(player.getUniqueId().toString(), vaultNum, item);
            PlayerVaults.getInstance().getServer().getScheduler().runTask(this.plugin, () -> {
                com.drtshock.playervaults.util.ComponentDispatcher.send(player, net.kyori.adventure.text.Component
                        .text("Vault icon updated.").color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
            });
        });
        return true;
    }
}
