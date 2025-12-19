package com.drtshock.playervaults.vaultmanagement;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.ComponentDispatcher;
import com.drtshock.playervaults.util.Logger;
import com.drtshock.playervaults.storage.StorageException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VaultSearcher {

    public record SearchResult(int vaultNumber, ItemStack item, int originalSlot) {
    }

    // NBT key to store context on the display item
    public static final NamespacedKey VAULT_NUMBER_KEY = new NamespacedKey(PlayerVaults.getInstance(),
            "search_vault_number");

    public static void search(Player player, String query) {
        ComponentDispatcher.send(player,
                Component.text("Searching vaults for '" + query + "'...").color(NamedTextColor.GRAY));

        PlayerVaults.getInstance().getServer().getScheduler().runTaskAsynchronously(PlayerVaults.getInstance(), () -> {
            try {
                Set<Integer> vaultNumbers = VaultManager.getInstance().getVaultNumbers(player.getUniqueId().toString());
                Map<Integer, String> vaultData = new HashMap<>();

                // 1. IO Phase (Async)
                for (int number : vaultNumbers) {
                    try {
                        String data = VaultManager.getInstance().getStorage().loadVault(player.getUniqueId(), number);
                        if (data != null) {
                            vaultData.put(number, data);
                        }
                    } catch (StorageException e) {
                        Logger.warn("Failed to load vault " + number + " during search for " + player.getName());
                    }
                }

                // 2. Processing Phase (Sync)
                PlayerVaults.getInstance().getServer().getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                    List<ItemStack> matches = new ArrayList<>();
                    String lowerQuery = query.toLowerCase();

                    for (Map.Entry<Integer, String> entry : vaultData.entrySet()) {
                        int vaultNum = entry.getKey();
                        String data = entry.getValue();

                        try {
                            ItemStack[] contents = CardboardBoxSerialization.fromStorage(data,
                                    player.getUniqueId().toString());
                            if (contents != null) {
                                for (int i = 0; i < contents.length; i++) {
                                    ItemStack item = contents[i];
                                    if (item != null && item.getType() != Material.AIR) {
                                        if (matchesItem(item, lowerQuery)) {
                                            ItemStack displayItem = item.clone();
                                            ItemMeta meta = displayItem.getItemMeta();
                                            if (meta != null) {
                                                List<String> lore = meta.getLore();
                                                if (lore == null)
                                                    lore = new ArrayList<>();
                                                lore.add("");
                                                lore.add(
                                                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                                                .legacySection().serialize(
                                                                        Component.text("Found in Vault #" + vaultNum)
                                                                                .color(NamedTextColor.GOLD)));
                                                meta.setLore(lore);

                                                // Store vault number in PDC for easy retrieval
                                                meta.getPersistentDataContainer().set(VAULT_NUMBER_KEY,
                                                        PersistentDataType.INTEGER, vaultNum);

                                                displayItem.setItemMeta(meta);
                                                matches.add(displayItem);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Logger.warn("Error deserializing vault " + vaultNum + " during search: " + e.getMessage());
                        }
                    }

                    openSearchGUI(player, matches, query);
                });

            } catch (Exception e) {
                Logger.severe("Critical error during search for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                PlayerVaults.getInstance().getServer().getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                    ComponentDispatcher.send(player,
                            Component.text("An error occurred while searching.").color(NamedTextColor.RED));
                });
            }
        });
    }

    private static boolean matchesItem(ItemStack item, String query) {
        if (item.getType().name().toLowerCase().contains(query)) {
            return true;
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                // Component usage in modern paper?
                // We don't have easy Component -> String conversion here properly without NMS
                // or serializer
                // But default implementation of toString might help, or usage of getDisplayName
                // (legacy)
                // Paper/Adventure APIs:
                // Let's use getDisplayName() for legacy support/ease, assuming Server has it
                // (it is deprecated in new Paper but exists)
                // Or we can check serialized component.
                // Using item.getI18NDisplayName? No.
                // Let's stick to Material name and straightforward checks for now.
                // We can check display name if available.
                try {
                    if (meta.getDisplayName().toLowerCase().contains(query)) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    private static void openSearchGUI(Player player, List<ItemStack> matches, String query) {
        if (matches.isEmpty()) {
            ComponentDispatcher.send(player,
                    Component.text("No items found matching '" + query + "'.").color(NamedTextColor.YELLOW));
            return;
        }

        SearchHolder holder = new SearchHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, "Search: " + query);
        holder.setInventory(inv);

        // Simple pagination? Just first 54 items for now.
        int limit = Math.min(matches.size(), 54);
        for (int i = 0; i < limit; i++) {
            inv.setItem(i, matches.get(i));
        }

        player.openInventory(inv);
        ComponentDispatcher.send(player, Component
                .text("Found " + matches.size() + " items. Showing top " + limit + ".").color(NamedTextColor.GREEN));
    }
}
