package com.drtshock.playervaults.vaultmanagement;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.ComponentDispatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VaultSelector implements InventoryHolder {
    private Inventory inventory;
    private final int page;

    public static final NamespacedKey VAULT_ID_KEY = new NamespacedKey(PlayerVaults.getInstance(), "selector_vault_id");

    public VaultSelector(int page) {
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public static void openSelector(Player player, int page) {
        if (page < 1)
            page = 1;

        // This process might be heavy if loaded synchronously.
        // Icons *should* be quick to load if local file, but ideally async.
        // For phase 1 compliance, we do it async.

        final int currentPage = page;
        PlayerVaults.getInstance().getServer().getScheduler().runTaskAsynchronously(PlayerVaults.getInstance(), () -> {
            try {
                Set<Integer> vaultNumbers = VaultManager.getInstance().getVaultNumbers(player.getUniqueId().toString());
                // Pagination logic
                // Size 54. 45 vaults per page?
                // Bottom row for nav.

                int maxPage = (vaultNumbers.stream().mapToInt(v -> v).max().orElse(0) / 45) + 1;

                VaultSelector holder = new VaultSelector(currentPage);
                Inventory inv = Bukkit.createInventory(holder, 54, "Vault Selector - Page " + currentPage);
                holder.setInventory(inv);

                // Populate
                // For this page, which vaults?
                // Just iterate 1 to MAX_VAULTS? Or just existing?
                // Selector should probably show available vaults.
                // Or showing existing vaults only.
                // Let's show existing vaults.

                // Sort numbers
                List<Integer> sorted = new ArrayList<>(vaultNumbers);
                java.util.Collections.sort(sorted);

                // Paging logic on sorted list
                int start = (currentPage - 1) * 45;
                int end = start + 45;

                for (int i = 0; i < sorted.size(); i++) {
                    if (i >= start && i < end) {
                        int vaultNum = sorted.get(i);
                        ItemStack icon = VaultManager.getInstance().getVaultIcon(player.getUniqueId().toString(),
                                vaultNum);
                        if (icon == null || icon.getType() == Material.AIR) {
                            icon = new ItemStack(Material.CHEST);
                        } else {
                            icon = icon.clone();
                        }

                        ItemMeta meta = icon.getItemMeta();
                        if (meta != null) {
                            Component name = meta.hasDisplayName() ? null
                                    : Component.text("Vault #" + vaultNum).color(NamedTextColor.GREEN);
                            // Use legacy serializer for sync compatibility
                            String nameStr = name != null
                                    ? net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                            .legacySection().serialize(name)
                                    : (ChatColor.GREEN + "Vault #" + vaultNum);
                            meta.setDisplayName(nameStr);

                            List<String> lore = meta.getLore();
                            if (lore == null)
                                lore = new ArrayList<>();
                            lore.add(
                                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                                            .serialize(Component.text("Click to open").color(NamedTextColor.GRAY)));
                            meta.setLore(lore);

                            meta.getPersistentDataContainer().set(VAULT_ID_KEY, PersistentDataType.INTEGER, vaultNum);
                            icon.setItemMeta(meta);
                        }

                        inv.setItem(i - start, icon);
                    }
                }

                // Nav buttons
                if (currentPage > 1) {
                    ItemStack prev = new ItemStack(Material.ARROW);
                    ItemMeta meta = prev.getItemMeta();
                    meta.setDisplayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection().serialize(Component.text("Previous Page").color(NamedTextColor.YELLOW)));
                    prev.setItemMeta(meta);
                    inv.setItem(45, prev); // Bottom left
                }

                if (currentPage < maxPage) {
                    ItemStack next = new ItemStack(Material.ARROW);
                    ItemMeta meta = next.getItemMeta();
                    meta.setDisplayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection().serialize(Component.text("Next Page").color(NamedTextColor.YELLOW)));
                    next.setItemMeta(meta);
                    inv.setItem(53, next); // Bottom right
                }

                // Sync open
                PlayerVaults.getInstance().getServer().getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                    player.openInventory(inv);
                });

            } catch (Exception e) {
                e.printStackTrace();
                PlayerVaults.getInstance().getServer().getScheduler().runTask(PlayerVaults.getInstance(), () -> {
                    ComponentDispatcher.send(player,
                            Component.text("Failed to load selector.").color(NamedTextColor.RED));
                });
            }
        });
    }

    public int getPage() {
        return page;
    }
}
