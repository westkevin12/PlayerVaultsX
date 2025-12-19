/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler, Laxwashere, CmdrKittens
 *
 * This program is free software: you can redistribute it and/or modify
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
package com.drtshock.playervaults.config.file;

import com.drtshock.playervaults.config.annotation.Comment;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@SuppressWarnings("all")
public class Config {
    @SuppressWarnings("all")
    public class Block {
        private boolean enabled = true;
        @Comment("""
                Material list for blocked items (does not support ID's), only effective if the feature is enabled.
                 If you don't know material names: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html

                Also, if you add "BLOCK_ALL_WITH_CUSTOM_MODEL_DATA" or "BLOCK_ALL_WITHOUT_CUSTOM_MODEL_DATA"
                 then either all items with custom model data will be blocked, or all items without custom model data will be blocked.""")
        private List<String> list = new ArrayList<>() {
            {
                this.add("PUMPKIN");
            }
        };

        @Comment("Enchantments to block from entering a vault at all.")
        private List<String> enchantmentsBlocked = new ArrayList<>();

        public boolean isEnabled() {
            return this.enabled;
        }

        public List<String> getList() {
            if (this.list == null) {
                this.list = new ArrayList<>();
            }
            return Collections.unmodifiableList(this.list);
        }

        public List<String> getEnchantmentsBlocked() {
            if (this.enchantmentsBlocked == null) {
                this.enchantmentsBlocked = new ArrayList<>();
            }
            return Collections.unmodifiableList(this.enchantmentsBlocked);
        }
    }

    @SuppressWarnings("all")
    public class Economy {
        @Comment("Set me to true to enable economy features!")
        private boolean enabled = false;
        private double feeToCreate = 100;
        private double feeToOpen = 10;
        private double refundOnDelete = 50;

        public boolean isEnabled() {
            return this.enabled;
        }

        public double getFeeToCreate() {
            return this.feeToCreate;
        }

        public double getFeeToOpen() {
            return this.feeToOpen;
        }

        public double getRefundOnDelete() {
            return this.refundOnDelete;
        }
    }

    @SuppressWarnings("all")
    public class PurgePlanet {
        private boolean enabled = false;
        @Comment("Time, in days, since last edit")
        private int daysSinceLastEdit = 30;

        public boolean isEnabled() {
            return this.enabled;
        }

        public int getDaysSinceLastEdit() {
            return this.daysSinceLastEdit;
        }
    }

    @SuppressWarnings("all")
    public class Storage {
        @SuppressWarnings("all")
        public class FlatFile {
            @Comment("""
                    Backups
                     Enabling this will create backups of vaults automagically.""")
            private boolean backups = true;

            public boolean isBackups() {
                return this.backups;
            }
        }

        @SuppressWarnings("all")
        public class MySQL {
            private String host = "localhost";
            private int port = 3306;
            private String database = "playervaults";
            private String username = "root";
            private String password = "";
            private boolean useSSL = false;
            private int connectionTimeout = 5000;
            private int maxPoolSize = 10;
            private int minimumIdle = 10;
            private int idleTimeout = 600000;
            private int maxLifetime = 1800000;
            private String connectionTestQuery = "SELECT 1";

            public String getHost() {
                return this.host;
            }

            public int getPort() {
                return this.port;
            }

            public String getDatabase() {
                return this.database;
            }

            public String getUsername() {
                return this.username;
            }

            public String getPassword() {
                return this.password;
            }

            public boolean useSSL() {
                return this.useSSL;
            }

            public int getConnectionTimeout() {
                return this.connectionTimeout;
            }

            public int getMaxPoolSize() {
                return this.maxPoolSize;
            }

            public int getMinimumIdle() {
                return this.minimumIdle;
            }

            public int getIdleTimeout() {
                return this.idleTimeout;
            }

            public int getMaxLifetime() {
                return this.maxLifetime;
            }

            public String getConnectionTestQuery() {
                return this.connectionTestQuery;
            }
        }

        @SuppressWarnings("all")
        public class Redis {
            private String host = "localhost";
            private int port = 6379;
            private String password = "";
            private int timeout = 2000;
            private int database = 0;
            private boolean ssl = false;
            private boolean enabled = false;
            @Comment("Time in minutes to keep vaults in cache")
            private long ttl = 30;

            public boolean isEnabled() {
                return enabled;
            }

            public String getHost() {
                return host;
            }

            public int getPort() {
                return port;
            }

            public String getPassword() {
                return password;
            }

            public int getTimeout() {
                return timeout;
            }

            public int getDatabase() {
                return database;
            }

            public boolean isSsl() {
                return ssl;
            }

            public long getTtl() {
                return ttl;
            }
        }

        private FlatFile flatFile = new FlatFile();
        private MySQL mysql = new MySQL();
        private Redis redis = new Redis();
        private String storageType = "flatfile";

        public FlatFile getFlatFile() {
            return this.flatFile;
        }

        public MySQL getMySQL() {
            return this.mysql;
        }

        public Redis getRedis() {
            return this.redis;
        }

        public String getStorageType() {
            return this.storageType;
        }
    }

    @Comment("""
            PlayerVaults
            Created by: https://github.com/drtshock/PlayerVaults/graphs/contributors/
            Resource page: https://www.spigotmc.org/resources/51204/
            Discord server: https://discordapp.com/invite/JZcWDEt/
            Made with love <3""")
    private boolean aPleasantHello = true;

    @Comment("""
            Debug Mode
             This will print everything the plugin is doing to console.
             You should only enable this if you're working with a contributor to fix something.""")
    private boolean debug = false;

    @Comment("""
            Can be 1 through 6.
            Default: 6""")
    private int defaultVaultRows = 6;

    @Comment("""
            Signs
             This will determine whether vault signs are enabled.
             If you don't know what this is or if it's for you, see the resource page.""")
    private boolean signs = false;

    @Comment("""
            Economy
             These are all of the settings for the economy integration. (Requires Vault)
              Bypass permission is: playervaults.free""")
    private Economy economy = new Economy();

    @Comment("""
            Blocked Items
             This will allow you to block specific materials from vaults.
              Bypass permission is: playervaults.bypassblockeditems""")
    private Block itemBlocking = new Block();

    @Comment("""
            Cleanup
             Enabling this will purge vaults that haven't been touched in the specified time frame.
              Reminder: This is only checked during startup.
                        This will not lag your server or touch the backups folder.""")
    private PurgePlanet purge = new PurgePlanet();

    @Comment("Sets the highest vault amount this plugin will test perms for")
    private int maxVaultAmountPermTest = 99;

    @Comment("Storage option. Currently only flatfile, but soon more! :)")
    private Storage storage = new Storage();

    public void setFromConfig(Logger l, FileConfiguration c) {
        l.info("Importing old configuration...");
        l.info("debug = " + (this.debug = c.getBoolean("debug", false)));
        l.info("signs = " + (this.signs = c.getBoolean("signs-enabled", false)));
        l.info("economy enabled = " + (this.economy.enabled = c.getBoolean("economy.enabled", false)));
        l.info(" creation fee = " + (this.economy.feeToCreate = c.getDouble("economy.cost-to-create", 100)));
        l.info(" open fee = " + (this.economy.feeToOpen = c.getDouble("economy.cost-to-open", 10)));
        l.info(" refund = " + (this.economy.refundOnDelete = c.getDouble("economy.refund-on-delete", 50)));
        l.info("item blocking enabled = " + (this.itemBlocking.enabled = c.getBoolean("blockitems", true)));
        l.info("blocked items = " + (this.itemBlocking.list = c.getStringList("blocked-items")));
        if (this.itemBlocking.list == null) {
            this.itemBlocking.list = new ArrayList<>();
            this.itemBlocking.list.add("PUMPKIN");
            this.itemBlocking.list.add("DIAMOND_BLOCK");
            l.info(" set defaults: " + this.itemBlocking.list);
        }
        l.info("cleanup purge enabled = " + (this.purge.enabled = c.getBoolean("cleanup.enable", false)));
        l.info(" days since last edit = " + (this.purge.daysSinceLastEdit = c.getInt("cleanup.lastEdit", 30)));
        l.info("flatfile storage backups = " + (this.storage.flatFile.backups = c.getBoolean("backups.enabled", true)));
        l.info("max vault amount to test via perms = "
                + (this.maxVaultAmountPermTest = c.getInt("max-vault-amount-perm-to-test", 99)));
    }

    public boolean isDebug() {
        return this.debug;
    }

    public int getDefaultVaultRows() {
        return this.defaultVaultRows;
    }

    public boolean isSigns() {
        return this.signs;
    }

    public Economy getEconomy() {
        return this.economy;
    }

    public Block getItemBlocking() {
        return this.itemBlocking;
    }

    public PurgePlanet getPurge() {
        return this.purge;
    }

    public int getMaxVaultAmountPermTest() {
        return this.maxVaultAmountPermTest;
    }

    public Storage getStorage() {
        return this.storage;
    }
}