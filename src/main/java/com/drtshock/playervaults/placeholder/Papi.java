package com.drtshock.playervaults.placeholder;

import com.drtshock.playervaults.vaultmanagement.VaultOperations;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class Papi extends PlaceholderExpansion {
    private final String version;

    public Papi(String version) {
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "playervaults";
    }

    @Override
    public String getAuthor() {
        return "mbaxter";
    }

    @Override
    public String getVersion() {
        return this.version;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return null;
        }
        if (params.equals("vaults_max_by_perms")) {
            return String.valueOf(VaultOperations.countVaults(player));
        }
        return null;
    }
}
