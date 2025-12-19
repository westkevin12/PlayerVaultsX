package com.drtshock.playervaults.listeners;

import com.drtshock.playervaults.vaultmanagement.VaultSearcher;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SearchInputListener implements Listener {

    private static final Set<UUID> AWAITING_SEARCH = new HashSet<>();

    public static void awaitInput(UUID uuid) {
        AWAITING_SEARCH.add(uuid);
    }

    public static boolean isAwaiting(UUID uuid) {
        return AWAITING_SEARCH.contains(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (AWAITING_SEARCH.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            AWAITING_SEARCH.remove(event.getPlayer().getUniqueId());

            String query = event.getMessage();

            // Sync to main thread for search initiation if needed, but VaultSearcher
            // handles async logic mostly
            // However, VaultSearcher.search starts with async task.
            // Let's run it.
            // Spigot AsyncPlayerChatEvent runs async, so calling another async method is
            // fine,
            // but ensure we don't access Bukkit API unsafely.
            // VaultSearcher.search schedules an async task immediately.
            VaultSearcher.search(event.getPlayer(), query);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        AWAITING_SEARCH.remove(event.getPlayer().getUniqueId());
    }
}
