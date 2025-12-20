package com.drtshock.playervaults.serialization;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.drtshock.playervaults.vaultmanagement.CardboardBoxSerialization;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComplexItemTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("deprecation")
    void testCustomModelDataSerialization() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(12345);
        meta.setDisplayName("Excalibur");
        item.setItemMeta(meta);

        Inventory inv = server.createInventory(null, 9);
        inv.setItem(0, item);

        String serialized = CardboardBoxSerialization.toStorage(inv, "testModelData");
        ItemStack[] deserialized = CardboardBoxSerialization.fromStorage(serialized, "testModelData");

        assertNotNull(deserialized);
        ItemStack result = deserialized[0];
        assertNotNull(result);
        assertTrue(result.hasItemMeta());
        assertEquals(12345, result.getItemMeta().getCustomModelData());
        assertEquals("Excalibur", result.getItemMeta().getDisplayName());
    }

    @Test
    @SuppressWarnings("deprecation")
    void testComplexMetaSerialization() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Ancient Scroll");
        meta.setLore(Arrays.asList("§7A mysterious scroll", "§7found in the ruins."));
        meta.addEnchant(Enchantment.UNBREAKING, 10, true); // Unsafe enchantment support
        item.setItemMeta(meta);

        Inventory inv = server.createInventory(null, 9);
        inv.setItem(0, item);

        String serialized = CardboardBoxSerialization.toStorage(inv, "testComplexMeta");
        ItemStack[] deserialized = CardboardBoxSerialization.fromStorage(serialized, "testComplexMeta");

        ItemStack result = deserialized[0];
        assertNotNull(result.getItemMeta());
        assertEquals("§6Ancient Scroll", result.getItemMeta().getDisplayName());
        assertEquals(2, result.getItemMeta().getLore().size());
        assertEquals(10, result.getItemMeta().getEnchantLevel(Enchantment.UNBREAKING));
    }
}
