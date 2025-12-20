package com.drtshock.playervaults.listeners;

import com.drtshock.playervaults.vaultmanagement.VaultOperations;
import com.drtshock.playervaults.vaultmanagement.VaultSelector;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class SelectorListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof VaultSelector selector
                && event.getWhoClicked() instanceof Player player) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();

            if (item == null || item.getType() == Material.AIR)
                return;

            // Nav handling
            if (item.getType() == Material.ARROW) {
                if (event.getSlot() == 45) { // Prev
                    VaultSelector.openSelector(player, selector.getPage() - 1);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                } else if (event.getSlot() == 53) { // Next
                    VaultSelector.openSelector(player, selector.getPage() + 1);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                }
                return;
            }

            // Search handling
            if (item.getType() == Material.COMPASS) {
                player.closeInventory();
                SearchInputListener.awaitInput(player.getUniqueId());
                com.drtshock.playervaults.util.ComponentDispatcher.send(player,
                        net.kyori.adventure.text.Component.text("Type your search query in chat...")
                                .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                return;
            }

            // Vault handling
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(VaultSelector.VAULT_ID_KEY,
                    PersistentDataType.INTEGER)) {
                int vaultNum = item.getItemMeta().getPersistentDataContainer().get(VaultSelector.VAULT_ID_KEY,
                        PersistentDataType.INTEGER);
                player.closeInventory();
                VaultOperations.openOwnVault(player, String.valueOf(vaultNum));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
        }
    }
}
