package com.drtshock.playervaults.vaultmanagement;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.drtshock.playervaults.util.ComponentDispatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

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

    @SuppressWarnings("deprecation")
    public static void openSelector(Player player, int page) {
        if (page < 1)
            page = 1;

        final int currentPage = page;
        PlayerVaults.getInstance().getServer().getScheduler().runTaskAsynchronously(PlayerVaults.getInstance(), () -> {
            try {
                Set<Integer> vaultNumbers = VaultManager.getInstance().getVaultNumbers(player.getUniqueId().toString());
                int maxVaults = PlayerVaults.getInstance().getConf().getMaxVaultAmountPermTest();
                int vaultsPerPage = 45;
                int maxPage = (maxVaults / vaultsPerPage) + 1;

                VaultSelector holder = new VaultSelector(currentPage);
                Inventory inv = Bukkit.createInventory(holder, 54, "Vault Selector - Page " + currentPage);
                holder.setInventory(inv);

                Config.Storage.Selector selectorConfig = PlayerVaults.getInstance().getConf().getStorage()
                        .getSelector();
                Material lockedMat = Material.getMaterial(selectorConfig.getLockedIcon());
                if (lockedMat == null)
                    lockedMat = Material.BARRIER;
                Material unownedMat = Material.getMaterial(selectorConfig.getUnownedIcon());
                if (unownedMat == null)
                    unownedMat = Material.MINECART;
                Material baseMat = Material.getMaterial(selectorConfig.getBaseIcon());
                if (baseMat == null)
                    baseMat = Material.CHEST;

                int start = (currentPage - 1) * vaultsPerPage;

                for (int i = 0; i < vaultsPerPage; i++) {
                    int vaultNum = start + i + 1;
                    if (vaultNum > maxVaults)
                        break;

                    ItemStack icon;
                    Component name;
                    List<Component> lore = new ArrayList<>();
                    boolean isLocked = !VaultOperations.checkPerms(player, vaultNum);

                    if (isLocked) {
                        icon = new ItemStack(lockedMat);
                        ItemMeta m = icon.getItemMeta();
                        if (selectorConfig.getLockedModelData() != 0) {
                            m.setCustomModelData(selectorConfig.getLockedModelData());
                        }
                        name = Component.text("Vault #" + vaultNum).color(NamedTextColor.RED);
                        lore.add(Component.text("Locked").color(NamedTextColor.GRAY));
                        icon.setItemMeta(m);
                    } else if (vaultNumbers.contains(vaultNum)) {
                        // Owned
                        icon = VaultManager.getInstance().getVaultIcon(player.getUniqueId().toString(), vaultNum);
                        if (icon == null || icon.getType() == Material.AIR) {
                            icon = new ItemStack(baseMat);
                        } else {
                            icon = icon.clone();
                        }
                        ItemMeta m = icon.getItemMeta();
                        name = m.hasDisplayName() ? null
                                : Component.text("Vault #" + vaultNum).color(NamedTextColor.GREEN);
                        lore.add(Component.text("Click to open").color(NamedTextColor.GRAY));
                        icon.setItemMeta(m);
                    } else {
                        // Unowned / Purchasable
                        icon = new ItemStack(unownedMat);
                        ItemMeta m = icon.getItemMeta();
                        if (selectorConfig.getUnownedModelData() != 0) {
                            m.setCustomModelData(selectorConfig.getUnownedModelData());
                        }
                        name = Component.text("Vault #" + vaultNum).color(NamedTextColor.YELLOW);
                        lore.add(Component.text("Click to create/buy").color(NamedTextColor.GRAY));
                        icon.setItemMeta(m);
                    }

                    ItemMeta meta = icon.getItemMeta();
                    if (name != null) {
                        // Use legacy serializer for sync compatibility if needed, or adventure
                        // For now, let's stick to setting display name via legacy to be safe with
                        // existing code
                        meta.setDisplayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacySection().serialize(name));
                    }

                    List<String> legacyLore = new ArrayList<>();
                    for (Component c : lore) {
                        legacyLore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacySection().serialize(c));
                    }
                    // Append existing lore if any? No, we overwrite for selector consistency
                    meta.setLore(legacyLore);

                    meta.getPersistentDataContainer().set(VAULT_ID_KEY, PersistentDataType.INTEGER, vaultNum);
                    icon.setItemMeta(meta);

                    inv.setItem(i, icon);
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
