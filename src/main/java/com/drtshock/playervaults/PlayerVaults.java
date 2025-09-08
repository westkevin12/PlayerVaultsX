/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler
 *
 * This program is free software: you can redistribute and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.drtshock.playervaults;

import com.drtshock.playervaults.commands.ConsoleCommand;
import com.drtshock.playervaults.commands.ConvertCommand;
import com.drtshock.playervaults.commands.DeleteCommand;
import com.drtshock.playervaults.commands.HelpMeCommand;
import com.drtshock.playervaults.commands.SignCommand;
import com.drtshock.playervaults.commands.SignSetInfo;
import com.drtshock.playervaults.commands.VaultCommand;
import com.drtshock.playervaults.config.Loader;
import com.drtshock.playervaults.config.file.Config;
import com.drtshock.playervaults.config.file.Translation;
import com.drtshock.playervaults.listeners.Listeners;
import com.drtshock.playervaults.listeners.SignListener;
import com.drtshock.playervaults.placeholder.Papi;
import com.drtshock.playervaults.storage.FileStorageProvider;
import com.drtshock.playervaults.storage.MySQLStorageProvider;
import com.drtshock.playervaults.storage.StorageProvider;
import com.drtshock.playervaults.tasks.Cleanup;
import com.drtshock.playervaults.util.ComponentDispatcher;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.vaultmanagement.EconomyOperations;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import com.drtshock.playervaults.vaultmanagement.VaultViewInfo;
import com.google.gson.Gson;
import dev.kitteh.cardboardbox.CardboardBox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PlayerVaults extends JavaPlugin {
    public static boolean DEBUG;
    private static PlayerVaults instance;
    private final HashMap<String, SignSetInfo> setSign = new HashMap<>();
    // Player name - VaultViewInfo
    private final HashMap<String, VaultViewInfo> inVault = new HashMap<>();
    // VaultViewInfo - Inventory
    private final HashMap<String, Inventory> openInventories = new HashMap<>();
    private final Set<Material> blockedMats = new HashSet<>();
    private final Set<Enchantment> blockedEnchs = new HashSet<>();
    private boolean blockWithModelData = false;
    private boolean blockWithoutModelData = false;
    private boolean useVault;
    private YamlConfiguration signs;
    private File signsFile;
    private boolean saveQueued;
    private boolean backupsEnabled;
    private File backupsFolder;
    private File uuidData;
    private File vaultData;
    private String _versionString;
    private int maxVaultAmountPermTest;
    private Metrics metrics;
    private final Config config = new Config();
    private final Translation translation = new Translation(this);
    private final List<String> exceptions = new CopyOnWriteArrayList<>();
    private String updateCheck;
    private Response updateResponse;
    private StorageProvider storageProvider;

    public static PlayerVaults getInstance() {
        return instance;
    }

    public static void debug(String s, long start) {
        if (DEBUG) {
            instance.getLogger().log(Level.INFO, "{0} took {1}ms", new Object[]{s, (System.currentTimeMillis() - start)});
        }
    }

    public static void debug(String s) {
        if (DEBUG) {
            instance.getLogger().log(Level.INFO, s);
        }
    }

    @Override
    public void onEnable() {
        if (!CardboardBox.isReady()) {
            this.getLogger().log(Level.SEVERE, "Could not initialize!", CardboardBox.getException());
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        instance = this;
        long start = System.currentTimeMillis();
        long time = System.currentTimeMillis();
        UpdateCheck update = new UpdateCheck("PlayerVaultsX", this.getDescription().getVersion(), this.getServer().getName(), this.getServer().getVersion());
        debug("adventure!", time);
        time = System.currentTimeMillis();
        loadConfig();
        DEBUG = getConf().isDebug();
        debug("config", time);
        time = System.currentTimeMillis();
        vaultData = new File(this.getDataFolder(), "newvaults");
        Conversion.convert(this);
        if (getConf().getStorage().getStorageType().equalsIgnoreCase("mysql")) {
            storageProvider = new MySQLStorageProvider();
        } else {
            storageProvider = new FileStorageProvider();
        }
        storageProvider.initialize();
        new VaultManager(this, storageProvider);
        debug("conversion", time);
        time = System.currentTimeMillis();
        debug("uuidvaultmanager", time);
        time = System.currentTimeMillis();
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        
        getServer().getPluginManager().registerEvents(new SignListener(this), this);
        debug("registering listeners", time);
        time = System.currentTimeMillis();
        this.backupsEnabled = this.getConf().getStorage().getFlatFile().isBackups();
        this.maxVaultAmountPermTest = this.getConf().getMaxVaultAmountPermTest();
        loadSigns();
        debug("loaded signs", time);
        time = System.currentTimeMillis();
        update.spigotId = "%%__USER__%%";
        getCommand("pv").setExecutor(new VaultCommand(this));
        getCommand("pvdel").setExecutor(new DeleteCommand(this));
        getCommand("pvconvert").setExecutor(new ConvertCommand(this));
        getCommand("pvsign").setExecutor(new SignCommand(this));
        getCommand("pvhelpme").setExecutor(new HelpMeCommand(this));
        getCommand("pvconsole").setExecutor(new ConsoleCommand(this));
        update.meow = this.getClass().getDeclaredMethods().length;
        debug("registered commands", time);
        time = System.currentTimeMillis();
        useVault = EconomyOperations.setup();
        debug("setup economy", time);

        if (getConf().getPurge().isEnabled()) {
            getServer().getScheduler().runTaskAsynchronously(this, new Cleanup(this, getConf().getPurge().getDaysSinceLastEdit()));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (saveQueued) {
                    saveSignsFile();
                }
            }
        }.runTaskTimer(this, 20, 20);

        this.metrics = new Metrics(this, 6905);
        Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        this.metricsDrillPie("vault", () -> this.metricsPluginInfo(vault));
        if (vault != null) {
            this.metricsDrillPie("vault_econ", () -> {
                Map<String, Map<String, Integer>> map = new HashMap<>();
                Map<String, Integer> entry = new HashMap<>();
                entry.put(!this.useVault ? "none" : EconomyOperations.getName(), 1);
                map.put(isEconomyEnabled() ? "enabled" : "disabled", entry);
                return map;
            });
            if (isEconomyEnabled()) {
                String name = EconomyOperations.getName();
                if (name.equals("Essentials Economy")) {
                    name = "Essentials";
                }
                if (name.equals("CMIEconomy")) {
                    name = "CMI";
                }
                Plugin plugin = getServer().getPluginManager().getPlugin(name);
                if (plugin != null) {
                    this.metricsDrillPie("vault_econ_plugins", () -> {
                        Map<String, Map<String, Integer>> map = new HashMap<>();
                        Map<String, Integer> entry = new HashMap<>();
                        entry.put(plugin.getDescription().getVersion(), 1);
                        map.put(plugin.getName(), entry);
                        return map;
                    });
                }
            }
        }

        if (vault != null) {
            String name = EconomyOperations.getPermsName();
            if (name != null) {
                Plugin plugin = getServer().getPluginManager().getPlugin(name);
                final String version;
                if (plugin == null) {
                    version = "unknown";
                }
                else {
                    version = plugin.getDescription().getVersion();
                }
                this.metricsDrillPie("vault_perms", () -> {
                    Map<String, Map<String, Integer>> map = new HashMap<>();
                    Map<String, Integer> entry = new HashMap<>();
                    entry.put(version, 1);
                    map.put(name, entry);
                    return map;
                });
            }
        }

        this.metricsSimplePie("signs", () -> getConf().isSigns() ? "enabled" : "disabled");
        this.metricsSimplePie("cats", () -> HelpMeCommand.likesCats ? "meow" : "purr");
        this.metricsSimplePie("cleanup", () -> getConf().getPurge().isEnabled() ? "enabled" : "disabled");

        this.metricsDrillPie("block_items", () -> {
            Map<String, Map<String, Integer>> map = new HashMap<>();
            Map<String, Integer> entry = new HashMap<>();
            if (getConf().getItemBlocking().isEnabled()) {
                for (Material material : blockedMats) {
                    entry.put(material.toString(), 1);
                }
            }
            if (entry.isEmpty()) {
                entry.put("none", 1);
            }
            map.put(getConf().getItemBlocking().isEnabled() ? "enabled" : "disabled", entry);
            return map;
        });

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new Papi(this.getDescription().getVersion()).register();
            this.getLogger().info("Adding placeholders for PlaceholderAPI!");
        }

        this.getLogger().info("Loaded! Took " + (System.currentTimeMillis() - start) + "ms");

        this.updateCheck = new Gson().toJson(update);
        if (!HelpMeCommand.likesCats) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URI("https://update.plugin.party/check").toURL();
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("POST");
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Type", "application/json");
                    con.setRequestProperty("Accept", "application/json");
                    try (OutputStream out = con.getOutputStream()) {
                        out.write(PlayerVaults.this.updateCheck.getBytes(StandardCharsets.UTF_8));
                    }
                    String reply;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                        reply = reader.lines().collect(Collectors.joining("\n"));
                    }
                    Response response = new Gson().fromJson(reply, Response.class);
                    if (response.isSuccess()) {
                        if (response.isUpdateAvailable()) {
                            PlayerVaults.this.updateResponse = response;
                            if (response.isUrgent()) {
                                PlayerVaults.this.getServer().getOnlinePlayers().forEach(PlayerVaults.this::updateNotification);
                            }
                            PlayerVaults.this.getLogger().warning("Update available: " + response.getLatestVersion() + (response.getMessage() == null ? "" : (" - " + response.getMessage())));
                        }
                    } else {
                        if (response.getMessage().equals("INVALID")) {
                            this.cancel();
                        } else if (response.getMessage().equals("TOO_FAST")) {
                            // Nothing for now
                        } else {
                            PlayerVaults.this.getLogger().warning("Failed to check for updates: " + response.getMessage());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }.runTaskTimerAsynchronously(this, 1, 20 /* ticks */ * 60 /* seconds in a minute */ * 60 /* minutes in an hour*/);
    }

    

    private void metricsDrillPie(String name, Callable<Map<String, Map<String, Integer>>> callable) {
        this.metrics.addCustomChart(new Metrics.DrilldownPie(name, callable));
    }

    private void metricsSimplePie(String name, Callable<String> callable) {
        this.metrics.addCustomChart(new Metrics.SimplePie(name, callable));
    }

    private Map<String, Map<String, Integer>> metricsPluginInfo(Plugin plugin) {
        return this.metricsInfo(plugin, () -> plugin.getDescription().getVersion());
    }

    private Map<String, Map<String, Integer>> metricsInfo(Object plugin, Supplier<String> versionGetter) {
        Map<String, Map<String, Integer>> map = new HashMap<>();
        Map<String, Integer> entry = new HashMap<>();
        entry.put(plugin == null ? "nope" : versionGetter.get(), 1);
        map.put(plugin == null ? "absent" : "present", entry);
        return map;
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.inVault.containsKey(player.getUniqueId().toString())) {
                Inventory inventory = player.getOpenInventory().getTopInventory();
                if (inventory.getViewers().size() == 1) {
                    VaultViewInfo info = this.inVault.get(player.getUniqueId().toString());
                    VaultManager.getInstance().saveVault(inventory, player.getUniqueId().toString(), info.getNumber());
                    this.openInventories.remove(info.toString());
                    // try this to make sure that they can't make further edits if the process hangs.
                    player.closeInventory();
                }

                this.inVault.remove(player.getUniqueId().toString());
                debug("Closing vault for " + player.getName());
                player.closeInventory();
            }
        }

        if (getConf().getPurge().isEnabled()) {
            saveSignsFile();
        }
        if (storageProvider != null) {
            storageProvider.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("pvreload")) {
            reloadConfig();
            loadConfig(); // To update blocked materials.
            reloadSigns();
            sender.sendMessage(ChatColor.GREEN + "Reloaded PlayerVault's configuration and lang files.");
        }
        return true;
    }

    private void loadConfig() {
        File configYaml = new File(this.getDataFolder(), "config.yml");
        if (!(new File(this.getDataFolder(), "config.conf").exists()) && configYaml.exists()) {
            this.config.setFromConfig(this.getLogger(), this.getConfig());
            try {
                Files.move(configYaml.toPath(), this.getDataFolder().toPath().resolve("old_unused_config.yml"));
            } catch (Exception e) {
                this.getLogger().log(Level.SEVERE, "Failed to move config for backup: " + e.getMessage());
                configYaml.deleteOnExit();
            }
        }

        try {
            Loader.loadAndSave("config", this.config);
        } catch (IOException | IllegalAccessException e) {
            this.getLogger().log(Level.SEVERE, "Could not load config.", e);
        }

        // Clear just in case this is a reload.
        blockedMats.clear();
        blockedEnchs.clear();
        this.blockWithModelData = false;
        this.blockWithoutModelData = false;
        if (getConf().getItemBlocking().isEnabled()) {
            for (String s : getConf().getItemBlocking().getList()) {
                if (s.equalsIgnoreCase("BLOCK_ALL_WITH_CUSTOM_MODEL_DATA")) {
                    this.blockWithModelData = true;
                }
                if (s.equalsIgnoreCase("BLOCK_ALL_WITHOUT_CUSTOM_MODEL_DATA")) {
                    this.blockWithoutModelData = true;
                }
                Material mat = Material.matchMaterial(s);
                if (mat != null) {
                    blockedMats.add(mat);
                    getLogger().log(Level.INFO, "Added {0} to list of blocked materials.", mat.name());
                }
            }
            boolean badEnch = false;
            for (String s : getConf().getItemBlocking().getEnchantmentsBlocked()) {
                Enchantment ench = Registry.ENCHANTMENT.match(s);
                if (ench != null) {
                    blockedEnchs.add(ench);
                } else {
                    badEnch = true;
                    this.getLogger().warning("Invalid enchantment in config: " + s);
                }
            }
            if (badEnch) {
                this.getLogger().info("Valid enchantent options: " + Registry.ENCHANTMENT.stream().map(e -> e.getKeyOrThrow().getKey()).collect(Collectors.joining(", ")));
            }
        }
        try {
            ItemMeta.class.getMethod("hasCustomModelData");
        } catch (NoSuchMethodException e) {
            this.blockWithModelData = false;
            this.blockWithoutModelData = false;
        }

        File lang = new File(this.getDataFolder(), "lang");
        if (lang.exists()) {
            this.getLogger().warning("There is no clean way for us to migrate your old lang data.");
            this.getLogger().warning("If you made any customizations, or used another language, you need to migrate the info to the new format in lang.conf");
            try {
                Files.move(lang.toPath(), lang.getParentFile().toPath().resolve("old_unused_lang"));
            } catch (Exception e) {
                this.getLogger().log(Level.SEVERE, "Failed to rename lang folder as it is no longer used: " + e.getMessage());
                configYaml.deleteOnExit();
            }
        }

        try {
            Path langPath = this.getDataFolder().toPath().resolve("lang.conf");
            if (Files.exists(langPath)) {
                List<String> lines = Files.readAllLines(langPath);
                List<String> updatedLines = new ArrayList<>();
                lines.forEach(line -> updatedLines.add(line.replaceAll("\\{(vault|player|price|count|item)}", "<$1>")));
                Files.write(langPath, updatedLines.stream().collect(Collectors.joining("\n")).getBytes());
            }
            Loader.loadAndSave("lang", this.translation);
        } catch (IOException | IllegalAccessException e) {
            this.getLogger().log(Level.SEVERE, "Could not load lang.", e);
        }
        this.translation.cleanupMiniMessup();
    }

    public Config getConf() {
        return this.config;
    }

    private void loadSigns() {
        File signs = new File(getDataFolder(), "signs.yml");
        if (!signs.exists()) {
            try {
                signs.createNewFile();
            } catch (IOException e) {
                getLogger().severe("PlayerVaults has encountered a fatal error trying to load the signs file.");
                getLogger().severe("Please report this error on GitHub @ https://github.com/drtshock/PlayerVaults/");
                e.printStackTrace();
            }
        }
        this.signsFile = signs;
        this.signs = YamlConfiguration.loadConfiguration(signs);
    }

    private void reloadSigns() {
        if (!getConf().isSigns()) {
            return;
        }
        if (!signsFile.exists()) loadSigns();
        try {
            signs.load(signsFile);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("PlayerVaults has encountered a fatal error trying to reload the signs file.");
            getLogger().severe("Please report this error on GitHub @ https://github.com/drtshock/PlayerVaults/");
            e.printStackTrace();
        }
    }

    /**
     * Get the signs.yml config.
     *
     * @return The signs.yml config.
     */
    public YamlConfiguration getSigns() {
        return this.signs;
    }

    /**
     * Save the signs.yml file.
     */
    public void saveSigns() {
        saveQueued = true;
    }

    private void saveSignsFile() {
        if (!getConf().isSigns()) {
            return;
        }

        saveQueued = false;
        try {
            signs.save(this.signsFile);
        } catch (IOException e) {
            getLogger().severe("PlayerVaults has encountered an error trying to save the signs file.");
            getLogger().severe("Please report this error on GitHub @ https://github.com/drtshock/PlayerVaults/");
            e.printStackTrace();
        }
    }

    public HashMap<String, SignSetInfo> getSetSign() {
        return this.setSign;
    }

    public HashMap<String, VaultViewInfo> getInVault() {
        return this.inVault;
    }

    public HashMap<String, Inventory> getOpenInventories() {
        return this.openInventories;
    }

    public boolean isEconomyEnabled() {
        return this.getConf().getEconomy().isEnabled() && this.useVault;
    }

    public File getVaultData() {
        return this.vaultData;
    }

    /**
     * Get the legacy UUID vault data folder.
     * Deprecated in favor of base64 data.
     *
     * @return uuid folder
     */
    @Deprecated
    public File getUuidData() {
        return this.uuidData;
    }

    public boolean isBackupsEnabled() {
        return this.backupsEnabled;
    }

    public File getBackupsFolder() {
        // having this in #onEnable() creates the 'uuidvaults' directory, preventing the conversion from running
        if (this.backupsFolder == null) {
            this.backupsFolder = new File(this.getVaultData(), "backups");
            this.backupsFolder.mkdirs();
        }

        return this.backupsFolder;
    }

    /**
     * Tries to get a name from a given String that we hope is a UUID.
     *
     * @param potentialUUID - potential UUID to try to get the name for.
     * @return the player's name if we can find it, otherwise return what got passed to us.
     */
    public String getNameIfPlayer(String potentialUUID) {
        UUID uuid;
        try {
            uuid = UUID.fromString(potentialUUID);
        } catch (Exception e) {
            return potentialUUID;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        return offlinePlayer != null ? offlinePlayer.getName() : potentialUUID;
    }

    public boolean isBlockedMaterial(Material mat) {
        return blockedMats.contains(mat);
    }

    public boolean isBlockWithModelData() {
        return this.blockWithModelData;
    }

    public boolean isBlockWithoutModelData() {
        return this.blockWithoutModelData;
    }

    public Set<Enchantment> isEnchantmentBlocked(ItemStack item) {
        Set<Enchantment> enchantments = new HashSet<>(item.getEnchantments().keySet());
        enchantments.retainAll(this.blockedEnchs);
        return enchantments;
    }

    /**
     * Tries to grab the server version as a string.
     *
     * @return Version as raw string
     */
    public String getVersion() {
        if (_versionString == null) {
            final String name = Bukkit.getServer().getClass().getPackage().getName();
            _versionString = name.substring(name.lastIndexOf(46) + 1) + ".";
        }
        return _versionString;
    }

    public int getDefaultVaultRows() {
        int def = this.config.getDefaultVaultRows();
        return (def >= 1 && def <= 6) ? def : 6;
    }

    public int getDefaultVaultSize() {
        return this.getDefaultVaultRows() * 9;
    }

    public boolean isSign(Material mat) {
        return mat.name().toUpperCase().contains("SIGN");
    }

    public int getMaxVaultAmountPermTest() {
        return this.maxVaultAmountPermTest;
    }

    public Translation getTL() {
        return this.translation;
    }

    public String getVaultTitle(String id) {
        return this.translation.vaultTitle().with("vault", id).getLegacy();
    }

    public String getExceptions() {
        if (this.exceptions.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean more = false;
        for (String e : this.exceptions) {
            if (more) {
                builder.append("\n\n");
            } else {
                more = true;
            }
            builder.append(e);
        }
        return builder.toString();
    }

    @SuppressWarnings("unused")
    private static class UpdateCheck {
        private String pluginName;
        private String pluginVersion;
        private String serverName;
        private String serverVersion;
        private int meow;
        private String spigotId;

        public UpdateCheck(String pluginName, String pluginVersion, String serverName, String serverVersion) {
            this.pluginName = pluginName;
            this.pluginVersion = pluginVersion;
            this.serverName = serverName;
            this.serverVersion = serverVersion;
        }
    }

    private static class Response {
        private boolean success;
        private String message;
        private boolean updateAvailable;
        private boolean isUrgent;
        private String latestVersion;
        private Component component;

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public boolean isUpdateAvailable() {
            return updateAvailable;
        }

        public boolean isUrgent() {
            return isUrgent;
        }

        public String getLatestVersion() {
            return latestVersion;
        }
        
        public Component getComponent() {
            if (component == null) {
                component = message == null ? null : MiniMessage.miniMessage().deserialize(message);
            }
            return component;
        }
    }

    private final Set<UUID> told = new HashSet<>();

    public void updateNotification(Player player) {
        if (updateResponse == null || !player.hasPermission(Permission.ADMIN)) {
            return;
        }
        if (!updateResponse.isUrgent() && this.told.contains(player.getUniqueId())) {
            return;
        }
        this.told.add(player.getUniqueId());
        ComponentDispatcher.send(player, Component.text().color(TextColor.fromHexString("#e35959"))
                .content("PlayerVaultsX Update Available: " + updateResponse.getLatestVersion()));
        if (updateResponse.isUrgent()) {
            ComponentDispatcher.send(player, Component.text().color(TextColor.fromHexString("#5E0B15"))
                    .content("This is an important update. Download and restart ASAP."));
        }
        if (updateResponse.getComponent() != null) {
            ComponentDispatcher.send(player, updateResponse.getComponent());
        }
    }

    public <T extends Throwable> T addException(T t) {
        if (this.getConf().isDebug()) {
            StringBuilder builder = new StringBuilder();
            builder.append(ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss"))).append('\n');
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            t.printStackTrace(printWriter);
            builder.append(stringWriter.toString());
            this.exceptions.add(builder.toString());
        }
        return t;
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }
}