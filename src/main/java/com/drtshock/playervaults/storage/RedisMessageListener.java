package com.drtshock.playervaults.storage;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Logger;
import com.drtshock.playervaults.vaultmanagement.VaultViewInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class RedisMessageListener extends JedisPubSub {

    private final String serverId;

    public RedisMessageListener(String serverId) {
        this.serverId = serverId;
    }

    @Override
    public void onMessage(String channel, String message) {
        // Run on main thread to interact with Bukkit API
        Bukkit.getScheduler().runTask(PlayerVaults.getInstance(), () -> {
            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                String sourceServer = json.get("server").getAsString();

                // Ignore messages from self
                if (serverId.equals(sourceServer)) {
                    return;
                }

                String type = json.get("type").getAsString();
                if ("UPDATE".equals(type)) {
                    handleUpdate(json);
                }
            } catch (Exception e) {
                Logger.warn("Failed to process Redis message: " + e.getMessage());
            }
        });
    }

    private void handleUpdate(JsonObject json) {
        String uuidStr = json.get("uuid").getAsString();
        int vaultNumber = json.get("vault").getAsInt();
        String scope = json.has("scope") ? json.get("scope").getAsString() : "global";

        // Logic:
        // 1. Invalidate local cache (handled implicitly by simple TTL or manual
        // eviction if we had a local map)
        // 2. Notify player if they are viewing this vault

        UUID uuid = UUID.fromString(uuidStr);
        Player player = Bukkit.getPlayer(uuid);

        // If the player is online on this server
        if (player != null && player.isOnline()) {
            // Check if they are currently viewing this specific vault
            // VaultViewInfo construction matches VaultManager logic
            VaultViewInfo info = new VaultViewInfo(uuidStr, vaultNumber, scope);
            if (PlayerVaults.getInstance().getOpenInventories().containsKey(info.toString())) {
                // They are looking at it! Close it or Refresh it.
                // For safety, let's close it with a message so they re-open and fetch fresh
                // data.
                player.closeInventory();
                player.sendMessage(PlayerVaults.getInstance().getTL().vaultUpdated().toString());
                // Assuming vaultUpdated translation exists, otherwise generic message
                Logger.info(
                        "Closed vault " + vaultNumber + " for " + player.getName() + " due to cross-server update.");
            }
        }
    }
}
