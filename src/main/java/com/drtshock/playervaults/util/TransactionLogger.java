package com.drtshock.playervaults.util;

import com.drtshock.playervaults.PlayerVaults;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TransactionLogger {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static File logFile;

    private static File getLogFile() {
        if (logFile == null) {
            logFile = new File(PlayerVaults.getInstance().getDataFolder(), "transactions.log");
            if (!logFile.exists()) {
                try {
                    logFile.getParentFile().mkdirs();
                    logFile.createNewFile();
                } catch (IOException e) {
                    Logger.severe("Could not create transactions.log: " + e.getMessage());
                }
            }
        }
        return logFile;
    }

    public static void logTransaction(UUID playerUUID, int vaultNumber, ItemStack[] oldContents,
            ItemStack[] newContents) {
        Map<ItemStack, Integer> oldCounts = countItems(oldContents);
        Map<ItemStack, Integer> newCounts = countItems(newContents);

        // Calculate differences
        Map<ItemStack, Integer> added = new HashMap<>(); // Positive counts
        Map<ItemStack, Integer> removed = new HashMap<>(); // Positive counts

        // Check for removed or reduced items
        for (Map.Entry<ItemStack, Integer> entry : oldCounts.entrySet()) {
            ItemStack item = entry.getKey();
            int oldAmt = entry.getValue();
            int newAmt = newCounts.getOrDefault(item, 0);

            if (newAmt < oldAmt) {
                removed.put(item, oldAmt - newAmt);
            }
        }

        // Check for added or increased items
        for (Map.Entry<ItemStack, Integer> entry : newCounts.entrySet()) {
            ItemStack item = entry.getKey();
            int newAmt = entry.getValue();
            int oldAmt = oldCounts.getOrDefault(item, 0);

            if (newAmt > oldAmt) {
                added.put(item, newAmt - oldAmt);
            }
        }

        if (added.isEmpty() && removed.isEmpty()) {
            return; // No changes
        }

        StringBuilder logEntry = new StringBuilder();
        logEntry.append("[").append(LocalDateTime.now().format(DATE_FORMATTER)).append("] ");
        logEntry.append("Player: ").append(playerUUID).append(" Vault: ").append(vaultNumber).append(" | ");

        if (!added.isEmpty()) {
            logEntry.append("ADDED: ");
            for (Map.Entry<ItemStack, Integer> entry : added.entrySet()) {
                logEntry.append(formatItem(entry.getKey())).append(" x").append(entry.getValue()).append(", ");
            }
        }

        if (!removed.isEmpty()) {
            if (!added.isEmpty())
                logEntry.append("| ");
            logEntry.append("REMOVED: ");
            for (Map.Entry<ItemStack, Integer> entry : removed.entrySet()) {
                logEntry.append(formatItem(entry.getKey())).append(" x").append(entry.getValue()).append(", ");
            }
        }

        writeToFile(logEntry.toString());
    }

    private static Map<ItemStack, Integer> countItems(ItemStack[] contents) {
        Map<ItemStack, Integer> counts = new HashMap<>();
        if (contents == null)
            return counts;

        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                ItemStack key = item.clone();
                key.setAmount(1); // Standardize for key
                counts.put(key, counts.getOrDefault(key, 0) + item.getAmount());
            }
        }
        return counts;
    }

    private static String formatItem(ItemStack item) {
        if (item == null)
            return "null";
        StringBuilder sb = new StringBuilder(item.getType().toString());
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            sb.append(" (").append(item.getItemMeta().getDisplayName()).append(")");
        }
        return sb.toString();
    }

    private static void writeToFile(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getLogFile(), true))) {
            writer.write(message);
            writer.newLine();
        } catch (IOException e) {
            Logger.severe("Failed to write to transactions.log: " + e.getMessage());
        }
    }
}
