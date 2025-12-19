package com.drtshock.playervaults.listeners;

import com.drtshock.playervaults.vaultmanagement.SearchHolder;
import com.drtshock.playervaults.vaultmanagement.VaultOperations;
import com.drtshock.playervaults.vaultmanagement.VaultSearcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class SearchListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof SearchHolder) {
            event.setCancelled(true); // Prevent taking items

            if (event.getCurrentItem() != null && event.getWhoClicked() instanceof Player player) {
                ItemStack item = event.getCurrentItem();
                if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                        .has(VaultSearcher.VAULT_NUMBER_KEY, PersistentDataType.INTEGER)) {
                    int vaultNumber = item.getItemMeta().getPersistentDataContainer()
                            .get(VaultSearcher.VAULT_NUMBER_KEY, PersistentDataType.INTEGER);

                    player.closeInventory();
                    // Open the vault
                    VaultOperations.openOwnVault(player, String.valueOf(vaultNumber));
                }
            }
        }
    }
}
