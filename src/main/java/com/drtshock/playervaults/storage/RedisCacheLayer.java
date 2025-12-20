package com.drtshock.playervaults.storage;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.drtshock.playervaults.util.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RedisCacheLayer implements StorageProvider {

    private final StorageProvider backingStore;
    private JedisPool pool;
    private boolean enabled = false;
    private long ttlSeconds = 1800; // default 30 mins

    public RedisCacheLayer(StorageProvider backingStore) {
        this.backingStore = backingStore;
        initialize();
    }

    @Override
    public void initialize() {
        Config.Storage.Redis config = PlayerVaults.getInstance().getConf().getStorage().getRedis();

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(128);

            this.pool = new JedisPool(poolConfig, config.getHost(), config.getPort(), config.getTimeout(),
                    config.getPassword().isEmpty() ? null : config.getPassword(), config.getDatabase(), config.isSsl());

            // Test connection
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }
            this.ttlSeconds = config.getTtl() * 60;
            this.enabled = true;
            Logger.info("Redis caching enabled!");
        } catch (Exception e) {
            Logger.warn("Failed to connect to Redis. Caching will be disabled: " + e.getMessage());
            this.enabled = false;
        }
    }

    @Override
    public void shutdown() {
        if (pool != null) {
            pool.close();
        }
        if (backingStore != null) {
            backingStore.shutdown();
        }
    }

    private String getKey(UUID holder, int number, String scope) {
        String scopeKey = (scope == null || scope.isEmpty()) ? "global" : scope;
        return "pv:" + scopeKey + ":" + holder.toString() + ":" + number;
    }

    private String getLockKey(UUID holder, int number, String scope) {
        String scopeKey = (scope == null || scope.isEmpty()) ? "global" : scope;
        return "pv:lock:" + scopeKey + ":" + holder.toString() + ":" + number;
    }

    @Override
    public void saveVault(UUID holder, int number, String serialized, String scope) throws StorageException {
        // Write-through: Save to DB first
        backingStore.saveVault(holder, number, serialized, scope);

        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                String key = getKey(holder, number, scope);
                jedis.setex(key, ttlSeconds, serialized);
            } catch (Exception e) {
                Logger.warn("Failed to update Redis cache: " + e.getMessage());
            }
        }
    }

    @Override
    public String loadVault(UUID holder, int number, String scope) throws StorageException {
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                String key = getKey(holder, number, scope);
                String data = jedis.get(key);
                if (data != null) {
                    Logger.debug("Loaded vault from cache: " + key);
                    // Refresh TTL
                    jedis.expire(key, ttlSeconds);
                    return data;
                }
            } catch (Exception e) {
                Logger.warn("Failed to load from Redis cache: " + e.getMessage());
            }
        }

        // Cache miss
        String data = backingStore.loadVault(holder, number, scope);

        if (enabled && data != null) {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(getKey(holder, number, scope), ttlSeconds, data);
            } catch (Exception e) {
                // Ignore cache write errors on read
            }
        }
        return data;
    }

    @Override
    public boolean vaultExists(UUID holder, int number, String scope) throws StorageException {
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                if (jedis.exists(getKey(holder, number, scope))) {
                    return true;
                }
            }
        }
        return backingStore.vaultExists(holder, number, scope);
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID holder, String scope) throws StorageException {
        return backingStore.getVaultNumbers(holder, scope);
    }

    @Override
    public void deleteVault(UUID holder, int number, String scope) throws StorageException {
        backingStore.deleteVault(holder, number, scope);
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                jedis.del(getKey(holder, number, scope));
            } catch (Exception e) {
                Logger.warn("Failed to delete from Redis cache: " + e.getMessage());
            }
        }
    }

    @Override
    public void deleteAllVaults(UUID holder, String scope) throws StorageException {
        backingStore.deleteAllVaults(holder, scope);
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                String scopeKey = (scope == null || scope.isEmpty()) ? "global" : scope;
                Set<String> keys = jedis.keys("pv:" + scopeKey + ":" + holder.toString() + ":*");
                if (!keys.isEmpty()) {
                    jedis.del(keys.toArray(new String[0]));
                }
            } catch (Exception e) {
                Logger.warn("Failed to delete all vaults from Redis cache: " + e.getMessage());
            }
        }
    }

    @Override
    public Set<UUID> getAllPlayerUUIDs() throws StorageException {
        return backingStore.getAllPlayerUUIDs();
    }

    @Override
    public void saveVaults(Map<UUID, Map<Integer, String>> vaults, String scope) throws StorageException {
        backingStore.saveVaults(vaults, scope);
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                Pipeline p = jedis.pipelined();
                for (Map.Entry<UUID, Map<Integer, String>> playerEntry : vaults.entrySet()) {
                    UUID uuid = playerEntry.getKey();
                    for (Map.Entry<Integer, String> vaultEntry : playerEntry.getValue().entrySet()) {
                        String key = getKey(uuid, vaultEntry.getKey(), scope);
                        p.setex(key, ttlSeconds, vaultEntry.getValue());
                    }
                }
                p.sync();
            } catch (Exception e) {
                Logger.warn("Failed to bulk update Redis cache: " + e.getMessage());
            }
        }
    }

    @Override
    public void cleanup(long olderThanTimestamp) throws StorageException {
        backingStore.cleanup(olderThanTimestamp);
    }

    @Override
    public boolean attemptLock(UUID playerUUID, int vaultId, String scope) {
        if (!enabled) {
            return true;
        }
        try (Jedis jedis = pool.getResource()) {
            String lockKey = getLockKey(playerUUID, vaultId, scope);
            // Set lock with 120 second TTL (safeguard) if it doesn't exist
            redis.clients.jedis.params.SetParams params = new redis.clients.jedis.params.SetParams().nx().ex(120);
            String result = jedis.set(lockKey, "locked", params);
            return "OK".equals(result);
        } catch (Exception e) {
            Logger.warn("Failed to acquire lock from Redis: " + e.getMessage());
            return true;
        }
    }

    @Override
    public void unlock(UUID playerUUID, int vaultId, String scope) {
        if (!enabled) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            String lockKey = getLockKey(playerUUID, vaultId, scope);
            jedis.del(lockKey);
        } catch (Exception e) {
            Logger.warn("Failed to release lock from Redis: " + e.getMessage());
        }
    }

    @Override
    public void saveVaultIcon(UUID playerUUID, int vaultId, String iconData) throws StorageException {
        backingStore.saveVaultIcon(playerUUID, vaultId, iconData);
    }

    @Override
    public String loadVaultIcon(UUID playerUUID, int vaultId) throws StorageException {
        return backingStore.loadVaultIcon(playerUUID, vaultId);
    }
}
